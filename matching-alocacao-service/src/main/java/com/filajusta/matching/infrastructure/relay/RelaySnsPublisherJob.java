package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.EventoOutboxRepositorio;
import com.filajusta.matching.domain.EventoOutbox;
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
 * Relay/publisher outbox proprio do matching-alocacao-service (Story 3-3a,
 * AD-3) -- infraestrutura pura, sem nenhum produtor real ainda (as Stories
 * 3.3b/3.3c vao gravar {@code AlocacaoConfirmada}/{@code SugestaoRecusada}/
 * {@code SugestaoGerada}). Mesmo padrao de
 * {@code triagem-score-service/.../infrastructure/relay/
 * RelaySnsPublisherJob.java} (Story 3.0): poller {@code @Scheduled} que le
 * linhas pendentes de {@code eventos_outbox}
 * ({@link EventoOutboxRepositorio#buscarNaoPublicados}), publica cada uma no
 * topico SNS FIFO configurado e so marca a linha como publicada
 * ({@link EventoOutboxRepositorio#marcarComoPublicado}) apos o ack do SNS --
 * nunca antes.
 *
 * <p>Envelope publicado e exatamente {@code {eventId, eventType,
 * occurredAt, version, correlationId, payload}} (contrato fixado no Epic 2);
 * {@code MessageGroupId = recursoId} (extraido do payload -- AQUI, diferente
 * de {@code triagem-score-service}, nunca {@code pacienteId}, Boundaries da
 * spec 3-3a) e {@code MessageDeduplicationId = eventId} do outbox -- garante
 * ordem por Recurso e deduplicacao do lado do SNS numa republicacao apos
 * falha parcial.
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
 * evento, so os eventos SEGUINTES do MESMO {@code recursoId} sao pulados
 * nesta execucao (preserva a ordem FIFO por Recurso) -- eventos de outros
 * Recursos no mesmo lote continuam sendo publicados normalmente, evitando
 * head-of-line blocking entre Recursos nao relacionados. A(s) linha(s)
 * puladas permanecem pendentes e sao tentadas de novo na proxima execucao.
 *
 * <p>{@code @ConditionalOnProperty} -- mesmo kill switch de
 * {@link RelaySnsClientConfig} ({@code filajusta.matching.outbox-relay.enabled},
 * default {@code true} em {@code application.yml}, mesmo padrao de
 * {@code filajusta.triagem.relay}, agora que o topico SNS FIFO proprio
 * deste servico esta provisionado em {@code infra-cdk} (emenda da Story
 * 3-3a, ver Spec Change Log)); testes {@code @SpringBootTest} pre-existentes
 * que nao exercitam este relay desligam {@code outbox-relay.enabled=false}
 * explicitamente -- so {@code RelaySnsPublisherJobIntegrationTest} liga com
 * um topico LocalStack.
 */
@Component
@ConditionalOnProperty(prefix = "filajusta.matching.outbox-relay", name = "enabled", matchIfMissing = false)
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
                         @Value("${filajusta.matching.outbox-relay.topic-arn}") String topicArn,
                         @Value("${filajusta.matching.outbox-relay.batch-size:50}") int loteTamanho) {
        // Fail-fast (mesmo achado do code review da Story 3.0): com o relay
        // habilitado (unico caso em que este bean e criado, ver
        // @ConditionalOnProperty), um topic-arn vazio faria o job rodar para
        // sempre falhando em silencio a cada ciclo -- melhor derrubar a
        // subida do servico.
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException("filajusta.matching.outbox-relay.topic-arn nao pode ser vazio "
                    + "com filajusta.matching.outbox-relay.enabled=true");
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

    @Scheduled(fixedDelayString = "${filajusta.matching.outbox-relay.poll-interval-ms:5000}")
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

        // Recursos cujo evento mais antigo pendente ja falhou nesta
        // execucao -- eventos seguintes do MESMO recursoId sao pulados para
        // preservar a ordem FIFO (MessageGroupId=recursoId); eventos de
        // OUTROS recursos continuam sendo processados no mesmo lote.
        //
        // Set<String> (nao Set<Object>): achado do code review multi-agente
        // -- a chave de bloqueio precisa da MESMA normalizacao
        // (String.valueOf) que messageGroupId(evento) usa para montar o
        // MessageGroupId publicado no SNS. Comparar o Object bruto do
        // payload aqui (ex.: Long 42 vs String "42" apos desserializacao)
        // podia deixar dois eventos do MESMO recursoId caindo no MESMO
        // MessageGroupId do SNS sem se bloquearem mutuamente neste Set,
        // quebrando a garantia de ordem FIFO por Recurso documentada acima.
        Set<String> recursosBloqueadosNestaExecucao = new HashSet<>();

        for (EventoOutbox evento : pendentes) {
            String chave = chaveDeBloqueio(evento);
            if (recursosBloqueadosNestaExecucao.contains(chave)) {
                continue;
            }
            if (!publicarEMarcar(evento)) {
                recursosBloqueadosNestaExecucao.add(chave);
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
            // messageGroupId(evento) lanca se o payload nao tiver recursoId
            // (nunca cair silenciosamente para a string "null") -- tratado
            // no mesmo caminho de falha de publicacao, sem derrubar o job.
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
     * Chave de bloqueio FIFO por recursoId, usada tanto na leitura quanto na
     * escrita de {@code recursosBloqueadosNestaExecucao} em
     * {@link #publicarPendentes()} -- mesma normalizacao
     * ({@code String.valueOf}) de {@link #messageGroupId(EventoOutbox)},
     * para que dois eventos do MESMO recursoId sempre colidam aqui
     * independente do tipo Java bruto do valor no payload desserializado
     * (achado do code review multi-agente).
     */
    private static String chaveDeBloqueio(EventoOutbox evento) {
        return String.valueOf(evento.getPayload().get("recursoId"));
    }

    private static String messageGroupId(EventoOutbox evento) {
        Object recursoId = evento.getPayload().get("recursoId");
        if (recursoId == null) {
            // String.valueOf(null) viraria a string literal "null" e
            // publicaria com um MessageGroupId invalido em vez de recusar o
            // evento malformado -- falha alto.
            throw new IllegalStateException(
                    "payload do evento " + evento.getEventId() + " nao tem recursoId -- MessageGroupId invalido");
        }
        return String.valueOf(recursoId);
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
