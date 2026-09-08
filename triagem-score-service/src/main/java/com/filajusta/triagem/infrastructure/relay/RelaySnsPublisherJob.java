package com.filajusta.triagem.infrastructure.relay;

import com.filajusta.triagem.application.command.EventoOutboxRepositorio;
import com.filajusta.triagem.domain.EventoOutbox;
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
 * Relay/publisher real do evento {@code ScoreCalculado} (Story 3.0, AD-3):
 * poller {@code @Scheduled} que le linhas pendentes de
 * {@code eventos_outbox} ({@link EventoOutboxRepositorio#buscarNaoPublicados}),
 * publica cada uma no topico SNS FIFO {@code score-calculado.fifo}
 * (declarado em {@code infra-cdk}) e so marca a linha como publicada
 * ({@link EventoOutboxRepositorio#marcarComoPublicado}) apos o ack do SNS
 * -- nunca antes (Boundaries da spec 3.0).
 *
 * <p>Envelope publicado e exatamente {@code {eventId, eventType,
 * occurredAt, version, correlationId, payload}} (contrato fixado no Epic
 * 2); {@code MessageGroupId = pacienteId} (extraido do payload) e
 * {@code MessageDeduplicationId = eventId} do outbox -- garante ordem por
 * paciente e deduplicacao do lado do SNS numa republicacao apos falha
 * parcial (I/O Matrix da spec 3.0: "Falha apos publicar, antes de marcar").
 *
 * <p>{@code @Transactional} (achado do code review): sem isso,
 * {@code buscarNaoPublicados} e {@code marcarComoPublicado} rodariam em
 * transacoes separadas e o lock de linha (
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}, ver
 * {@code EventoOutboxJpaRepository}) seria liberado assim que a leitura
 * retornasse -- ou seja, antes do ack do SNS -- permitindo que duas
 * instancias do job lessem e publicassem a MESMA linha (a I/O Matrix da spec
 * 3.0 exige protecao explicita aqui, nao so na marcacao). Com o metodo
 * inteiro numa unica transacao, o lock da leitura so e liberado quando este
 * metodo retorna (apos publicar e marcar), entao a segunda instancia que
 * rodar {@code SKIP LOCKED} concorrentemente pula as linhas que esta
 * instancia ja esta processando.
 *
 * <p>Processa o lote em ordem de {@code id}; ao falhar a publicacao de um
 * evento, so os eventos SEGUINTES do MESMO {@code pacienteId} sao pulados
 * nesta execucao (preserva a ordem FIFO por paciente, unico motivo de nao
 * tentar a frente) -- eventos de outros pacientes no mesmo lote continuam
 * sendo publicados normalmente, evitando head-of-line blocking entre
 * pacientes nao relacionados. A(s) linha(s) puladas permanecem pendentes e
 * sao tentadas de novo na proxima execucao -- nenhuma intervencao manual e
 * exigida.
 *
 * <p>{@code @ConditionalOnProperty} -- mesmo kill switch de
 * {@link RelaySnsClientConfig} ({@code filajusta.triagem.relay.enabled},
 * default {@code true}); desligado em testes que nao exercitam o relay.
 */
@Component
@ConditionalOnProperty(prefix = "filajusta.triagem.relay", name = "enabled", matchIfMissing = true)
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
                         @Value("${filajusta.triagem.relay.topic-arn}") String topicArn,
                         @Value("${filajusta.triagem.relay.batch-size:50}") int loteTamanho) {
        // Fail-fast (achado do code review): com o relay habilitado (unico
        // caso em que este bean e criado, ver @ConditionalOnProperty), um
        // topic-arn vazio faria o job rodar para sempre falhando em
        // silencio a cada ciclo -- melhor derrubar a subida do servico.
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException(
                    "filajusta.triagem.relay.topic-arn nao pode ser vazio com filajusta.triagem.relay.enabled=true");
        }
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn;
        // batch-size <= 0 quebraria a query de leitura a cada execucao
        // (achado do code review) -- piso de 1 em vez de derrubar a subida,
        // por ser um parametro de tuning, nao uma dependencia externa.
        this.loteTamanho = Math.max(1, loteTamanho);
    }

    @Scheduled(fixedDelayString = "${filajusta.triagem.relay.poll-interval-ms:5000}")
    @Transactional
    void publicarPendentes() {
        List<EventoOutbox> pendentes;
        try {
            pendentes = eventoOutboxRepositorio.buscarNaoPublicados(loteTamanho);
        } catch (RuntimeException e) {
            // I/O Matrix: "SNS indisponivel/erro transitorio" cobre tambem
            // falha na propria leitura do outbox -- nenhuma excecao pode
            // escapar do job (nao pode derrubar a app).
            log.error("Falha ao ler eventos pendentes de eventos_outbox -- tenta de novo na proxima execucao", e);
            return;
        }

        // Pacientes cujo evento mais antigo pendente ja falhou nesta
        // execucao -- eventos seguintes do MESMO paciente sao pulados para
        // preservar a ordem FIFO (MessageGroupId=pacienteId); eventos de
        // OUTROS pacientes continuam sendo processados no mesmo lote.
        Set<Object> pacientesBloqueadosNestaExecucao = new HashSet<>();

        for (EventoOutbox evento : pendentes) {
            Object pacienteIdBruto = evento.getPayload().get("pacienteId");
            if (pacientesBloqueadosNestaExecucao.contains(pacienteIdBruto)) {
                continue;
            }
            if (!publicarEMarcar(evento)) {
                pacientesBloqueadosNestaExecucao.add(pacienteIdBruto);
            }
        }
    }

    /** @return {@code true} se a publicacao teve sucesso (mesmo que o marcar tenha perdido uma corrida). */
    private boolean publicarEMarcar(EventoOutbox evento) {
        String envelope;
        try {
            envelope = montarEnvelope(evento);
        } catch (RuntimeException e) {
            // Distinto de falha de publicacao (achado do code review): um
            // bug de serializacao do envelope nao e uma falha de rede do
            // SNS, e confundir os dois logs atrapalha o diagnostico.
            log.error("Falha ao montar o envelope do evento {} (eventType={}) -- bug de serializacao, "
                    + "nao uma falha de rede; linha permanece pendente",
                    evento.getEventId(), evento.getEventType(), e);
            return false;
        }

        try {
            // messageGroupId(evento) lanca se o payload nao tiver
            // pacienteId (achado do code review: nunca cair silenciosamente
            // para a string "null") -- tratado no mesmo caminho de falha de
            // publicacao, sem derrubar o job.
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
                // Corrida entre instancias (I/O Matrix): outra instancia ja
                // marcou esta linha publicada primeiro -- nao e um erro, so
                // um dado a auditar.
                log.warn("Evento {} ja havia sido marcado como publicado por outra instancia do job",
                        evento.getEventId());
            }
        } catch (RuntimeException e) {
            // I/O Matrix: "Falha apos publicar, antes de marcar" -- a linha
            // continua com publicado_em nulo (a transacao do UPDATE nao
            // commitou) e sera republicada na proxima execucao; at-least-once
            // e aceitavel, dedup fica a cargo do MessageDeduplicationId
            // (Boundaries da spec 3.0). Nao propaga: o evento ja foi
            // publicado com sucesso, nao ha por que interromper o lote.
            log.error("Falha ao marcar o evento {} como publicado apos o ack do SNS -- linha sera republicada "
                    + "na proxima execucao (at-least-once, dedup via MessageDeduplicationId)",
                    evento.getEventId(), e);
        }
        return true;
    }

    private static String messageGroupId(EventoOutbox evento) {
        Object pacienteId = evento.getPayload().get("pacienteId");
        if (pacienteId == null) {
            // Achado do code review: String.valueOf(null) vira a string
            // literal "null" e publicaria com um MessageGroupId invalido em
            // vez de recusar o evento malformado -- falha alto.
            throw new IllegalStateException(
                    "payload do evento " + evento.getEventId() + " nao tem pacienteId -- MessageGroupId invalido");
        }
        return String.valueOf(pacienteId);
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
