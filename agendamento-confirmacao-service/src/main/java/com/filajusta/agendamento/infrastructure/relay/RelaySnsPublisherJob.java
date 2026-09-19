package com.filajusta.agendamento.infrastructure.relay;

import com.filajusta.agendamento.application.command.EventoOutboxRepositorio;
import com.filajusta.agendamento.domain.EventoOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Relay/publisher outbox proprio do agendamento-confirmacao-service (spec
 * 1.2, AD-3) -- copia adaptada de {@code matching-alocacao-service/.../
 * infrastructure/relay/RelaySnsPublisherJob.java}: poller {@code @Scheduled}
 * que le linhas pendentes de {@code eventos_outbox}
 * ({@link EventoOutboxRepositorio#buscarNaoPublicados}), publica cada uma no
 * topico SNS FIFO configurado e so marca a linha como publicada
 * ({@link EventoOutboxRepositorio#marcarComoPublicado}) apos o ack do SNS --
 * nunca antes.
 *
 * <p>Envelope publicado e exatamente {@code {eventId, eventType,
 * occurredAt, version, correlationId, payload}} (contrato do Epic 2);
 * {@code MessageGroupId = agendamentoId} (extraido do payload -- diferente
 * de {@code matching-alocacao-service}, que usa {@code recursoId}, Code Map
 * da spec 1.2) e {@code MessageDeduplicationId = eventId} do outbox --
 * garante ordem por Agendamento e deduplicacao do lado do SNS numa
 * republicacao apos falha parcial.
 *
 * <p>{@code @Transactional}: sem isso, {@code buscarNaoPublicados} e
 * {@code marcarComoPublicado} rodariam em transacoes separadas e o lock de
 * linha ({@code SELECT ... FOR UPDATE SKIP LOCKED}, ver
 * {@code EventoOutboxJpaRepository}) seria liberado assim que a leitura
 * retornasse -- antes do ack do SNS -- permitindo que duas instancias do job
 * lessem e publicassem a MESMA linha. Com o metodo inteiro numa unica
 * transacao, a segunda instancia que rodar {@code SKIP LOCKED}
 * concorrentemente pula as linhas que esta instancia ja esta processando.
 *
 * <p>Processa o lote em ordem de {@code id}; ao falhar a publicacao de um
 * evento, so os eventos SEGUINTES do MESMO {@code agendamentoId} sao pulados
 * nesta execucao (preserva a ordem FIFO por Agendamento) -- eventos de
 * outros Agendamentos no mesmo lote continuam sendo publicados normalmente,
 * evitando head-of-line blocking entre Agendamentos nao relacionados. A(s)
 * linha(s) puladas permanecem pendentes e sao tentadas de novo na proxima
 * execucao.
 *
 * <p>{@code @ConditionalOnProperty} -- kill switch
 * ({@code filajusta.agendamento.outbox-relay.enabled}, default {@code true}
 * em {@code application.yml}, mesmo padrao do molde); testes
 * {@code @SpringBootTest} que nao exercitam este relay desligam
 * {@code outbox-relay.enabled=false} explicitamente.
 */
@Component
@ConditionalOnProperty(prefix = "filajusta.agendamento.outbox-relay", name = "enabled", matchIfMissing = false)
class RelaySnsPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(RelaySnsPublisherJob.class);

    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;
    private final int loteTamanho;

    RelaySnsPublisherJob(EventoOutboxRepositorio eventoOutboxRepositorio,
                         SnsClient snsClient,
                         ObjectMapper objectMapper,
                         @Value("${filajusta.agendamento.outbox-relay.topic-arn}") String topicArn,
                         @Value("${filajusta.agendamento.outbox-relay.batch-size:50}") int loteTamanho) {
        // Fail-fast (mesmo achado do code review do molde): com o relay
        // habilitado (unico caso em que este bean e criado, ver
        // @ConditionalOnProperty), um topic-arn vazio faria o job rodar para
        // sempre falhando em silencio a cada ciclo -- melhor derrubar a
        // subida do servico.
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException("filajusta.agendamento.outbox-relay.topic-arn nao pode ser vazio "
                    + "com filajusta.agendamento.outbox-relay.enabled=true");
        }
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn;
        // batch-size <= 0 quebraria a query de leitura a cada execucao --
        // piso de 1 em vez de derrubar a subida, por ser um parametro de
        // tuning, nao uma dependencia externa.
        this.loteTamanho = Math.max(1, loteTamanho);
    }

    @Scheduled(fixedDelayString = "${filajusta.agendamento.outbox-relay.poll-interval-ms:5000}")
    @Transactional
    void publicarPendentes() {
        List<EventoOutbox> pendentes;
        try {
            pendentes = eventoOutboxRepositorio.buscarNaoPublicados(loteTamanho);
        } catch (RuntimeException e) {
            // Nenhuma excecao pode escapar do job (nao pode derrubar a app).
            log.error("Falha ao ler eventos pendentes de eventos_outbox -- tenta de novo na proxima execucao", e);
            return;
        }

        // Agendamentos cujo evento mais antigo pendente ja falhou nesta
        // execucao -- eventos seguintes do MESMO agendamentoId sao pulados
        // para preservar a ordem FIFO (MessageGroupId=agendamentoId); eventos
        // de OUTROS agendamentos continuam sendo processados no mesmo lote.
        //
        // Set<String> (nao Set<Object>): mesma normalizacao (String.valueOf)
        // que messageGroupId(evento) usa para montar o MessageGroupId
        // publicado no SNS -- evita que dois eventos do MESMO agendamentoId
        // caiam em chaves diferentes por causa do tipo Java bruto do payload
        // desserializado (achado do code review do molde).
        Set<String> agendamentosBloqueadosNestaExecucao = new HashSet<>();

        for (EventoOutbox evento : pendentes) {
            String chave = chaveDeBloqueio(evento);
            if (agendamentosBloqueadosNestaExecucao.contains(chave)) {
                continue;
            }
            if (!publicarEMarcar(evento)) {
                agendamentosBloqueadosNestaExecucao.add(chave);
            }
        }
    }

    /** @return {@code true} se a publicacao teve sucesso (mesmo que o marcar tenha perdido uma corrida). */
    private boolean publicarEMarcar(EventoOutbox evento) {
        String envelope;
        try {
            envelope = montarEnvelope(evento);
        } catch (RuntimeException e) {
            // Distinto de falha de publicacao: um bug de serializacao do
            // envelope nao e uma falha de rede do SNS, e confundir os dois
            // logs atrapalha o diagnostico.
            log.error("Falha ao montar o envelope do evento {} (eventType={}) -- bug de serializacao, "
                    + "nao uma falha de rede; linha permanece pendente",
                    evento.getEventId(), evento.getEventType(), e);
            return false;
        }

        try {
            // messageGroupId(evento) lanca se o payload nao tiver
            // agendamentoId (nunca cair silenciosamente para a string
            // "null") -- tratado no mesmo caminho de falha de publicacao,
            // sem derrubar o job.
            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(envelope)
                    .messageGroupId(messageGroupId(evento))
                    .messageDeduplicationId(evento.getEventId().toString())
                    .build());
        } catch (RuntimeException e) {
            log.error("Falha ao publicar evento {} (eventType={}) no SNS FIFO -- linha permanece pendente, "
                    + "retry na proxima execucao", evento.getEventId(), evento.getEventType(), e);
            return false;
        }

        try {
            boolean marcado = eventoOutboxRepositorio.marcarComoPublicado(evento.getId());
            if (!marcado) {
                // Corrida entre instancias: outra instancia ja marcou esta
                // linha publicada primeiro -- nao e um erro, so um dado a
                // auditar.
                log.warn("Evento {} ja havia sido marcado como publicado por outra instancia do job",
                        evento.getEventId());
            }
        } catch (RuntimeException e) {
            // A linha continua com publicado_em nulo (a transacao do UPDATE
            // nao commitou) e sera republicada na proxima execucao --
            // at-least-once e aceitavel, dedup fica a cargo do
            // MessageDeduplicationId. Nao propaga: o evento ja foi publicado
            // com sucesso, nao ha por que interromper o lote.
            log.error("Falha ao marcar o evento {} como publicado apos o ack do SNS -- linha sera republicada "
                    + "na proxima execucao (at-least-once, dedup via MessageDeduplicationId)",
                    evento.getEventId(), e);
        }
        return true;
    }

    /**
     * Chave de bloqueio FIFO por agendamentoId, usada tanto na leitura quanto
     * na escrita de {@code agendamentosBloqueadosNestaExecucao} em
     * {@link #publicarPendentes()} -- mesma normalizacao
     * ({@code String.valueOf}) de {@link #messageGroupId(EventoOutbox)}.
     */
    private static String chaveDeBloqueio(EventoOutbox evento) {
        return String.valueOf(evento.getPayload().get("agendamentoId"));
    }

    private static String messageGroupId(EventoOutbox evento) {
        Object agendamentoId = evento.getPayload().get("agendamentoId");
        if (agendamentoId == null) {
            // String.valueOf(null) viraria a string literal "null" e
            // publicaria com um MessageGroupId invalido em vez de recusar o
            // evento malformado -- falha alto.
            throw new IllegalStateException(
                    "payload do evento " + evento.getEventId() + " nao tem agendamentoId -- MessageGroupId invalido");
        }
        return String.valueOf(agendamentoId);
    }

    private String montarEnvelope(EventoOutbox evento) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", evento.getEventId());
        envelope.put("eventType", evento.getEventType());
        envelope.put("occurredAt", evento.getOccurredAt());
        envelope.put("version", evento.getVersion());
        envelope.put("correlationId", evento.getCorrelationId());
        envelope.put("payload", evento.getPayload());
        return objectMapper.writeValueAsString(envelope);
    }
}
