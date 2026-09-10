package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.AtualizarScoreReplica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Primeiro consumidor SQS real do projeto (Story 3.1b, Design Notes):
 * poller {@code @Scheduled} que lê a fila SQS FIFO assinante de
 * {@code score-calculado.fifo} (declarada em {@code infra-cdk}), faz o
 * upsert idempotente na réplica local via {@link AtualizarScoreReplica} e só
 * remove a mensagem da fila ({@code deleteMessage}) após o upsert
 * confirmado -- nunca antes (Boundaries da spec 3.1b). Não introduz outbox
 * local: consome direto da fila, diferente do publisher da Story 3.0
 * ({@code RelaySnsPublisherJob}), que só escreve.
 *
 * <p>Envelope lido é exatamente {@code {eventId, eventType, occurredAt,
 * version, correlationId, payload}} (contrato fixado no Epic 2, mesmo
 * publicado pelo relay da Story 3.0); só {@code payload.pacienteId},
 * {@code payload.scoreValor}, {@code occurredAt} e {@code eventId} são
 * usados nesta fase -- {@code triagemId}/{@code algoritmoVersao}/
 * {@code fatores}/{@code version}/{@code correlationId} não são persistidos
 * na réplica (Code Map da spec 3.1b: só o que a tabela {@code score_replica}
 * guarda).
 *
 * <p>Mensagem malformada (I/O Matrix): nem o parse do envelope nem uma
 * falha de upsert chamam {@code deleteMessage} -- a mensagem permanece na
 * fila, é reentregue após o {@code VisibilityTimeout} e, ao atingir
 * {@code maxReceiveCount} (fila declarada em {@code infra-cdk}), o próprio
 * SQS a move para a DLQ automaticamente -- este job não precisa rastrear
 * tentativas.
 *
 * <p>{@code @ConditionalOnProperty} -- mesmo kill switch de
 * {@link ScoreCalculadoSqsClientConfig}
 * ({@code filajusta.matching.relay.enabled}, default {@code true}).
 */
@Component
@ConditionalOnProperty(prefix = "filajusta.matching.relay", name = "enabled", matchIfMissing = true)
class ScoreCalculadoConsumerJob {

    private static final Logger log = LoggerFactory.getLogger(ScoreCalculadoConsumerJob.class);
    private static final String EVENT_TYPE_ESPERADO = "ScoreCalculado";

    private final SqsClient sqsClient;
    private final AtualizarScoreReplica atualizarScoreReplica;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final int loteTamanho;
    private final int waitTimeSeconds;

    ScoreCalculadoConsumerJob(SqsClient sqsClient,
                               AtualizarScoreReplica atualizarScoreReplica,
                               ObjectMapper objectMapper,
                               @Value("${filajusta.matching.relay.queue-url}") String queueUrl,
                               @Value("${filajusta.matching.relay.batch-size:10}") int loteTamanho,
                               @Value("${filajusta.matching.relay.wait-time-seconds:1}") int waitTimeSeconds) {
        // Fail-fast (mesmo raciocinio de RelaySnsPublisherJob, Story 3.0):
        // com o consumidor habilitado (unico caso em que este bean e
        // criado, ver @ConditionalOnProperty), uma queue-url vazia faria o
        // job rodar para sempre falhando em silencio a cada ciclo -- melhor
        // derrubar a subida do servico.
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException(
                    "filajusta.matching.relay.queue-url nao pode ser vazio com filajusta.matching.relay.enabled=true");
        }
        this.sqsClient = sqsClient;
        this.atualizarScoreReplica = atualizarScoreReplica;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        // SQS limita ReceiveMessage a no maximo 10 mensagens por chamada;
        // batch-size <= 0 quebraria a chamada a cada execucao -- piso de 1.
        this.loteTamanho = Math.min(10, Math.max(1, loteTamanho));
        // Achado do code review: SQS rejeita WaitTimeSeconds fora de 0..20 --
        // sem o teto de 20, um valor configurado acima disso faria TODA
        // chamada receiveMessage falhar (o consumidor nunca processaria
        // nenhuma mensagem, silenciosamente, ja que falha transitoria so e
        // logada e ignorada).
        this.waitTimeSeconds = Math.min(20, Math.max(0, waitTimeSeconds));
    }

    @Scheduled(fixedDelayString = "${filajusta.matching.relay.poll-interval-ms:5000}")
    void consumirPendentes() {
        List<Message> mensagens;
        try {
            mensagens = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(loteTamanho)
                            .waitTimeSeconds(waitTimeSeconds)
                            .build())
                    .messages();
        } catch (RuntimeException e) {
            // Nenhuma excecao pode escapar do job (nao pode derrubar a
            // app) -- tenta de novo no proximo ciclo do @Scheduled.
            log.error("Falha ao receber mensagens da fila SQS FIFO assinante de score-calculado.fifo "
                    + "-- tenta de novo na proxima execucao", e);
            return;
        }

        for (Message mensagem : mensagens) {
            processar(mensagem);
        }
    }

    private void processar(Message mensagem) {
        JsonNode envelope;
        UUID eventId;
        String correlationId;
        try {
            envelope = objectMapper.readTree(mensagem.body());
            validarEnvelope(envelope);
            // eventId/correlationId extraidos aqui (nao so na secao do
            // upsert abaixo) para estarem disponiveis nos logs de erro dos
            // dois try/catch seguintes mesmo quando o upsert ou o delete
            // falham -- achado do code review: antes os logs so citavam o
            // messageId do SQS, nunca o eventId de dominio, quebrando o
            // padrao ja usado em RelaySnsPublisherJob (Story 3.0). Um
            // eventId mal formado cai aqui (tratado como mensagem
            // malformada), nunca chega a ser usado nos logs abaixo.
            eventId = UUID.fromString(envelope.get("eventId").asText());
            JsonNode correlationIdNode = envelope.get("correlationId");
            correlationId = (correlationIdNode != null && !correlationIdNode.isNull())
                    ? correlationIdNode.asText() : null;
        } catch (RuntimeException e) {
            // I/O Matrix: "Mensagem malformada" -- log de erro, mensagem NAO
            // removida; reentregue ate maxReceiveCount, depois DLQ (fila
            // declarada em infra-cdk).
            log.error("Mensagem malformada na fila SQS FIFO assinante de score-calculado.fifo "
                    + "(messageId={}) -- nao removida, sera reentregue ate ir para a DLQ",
                    mensagem.messageId(), e);
            return;
        }

        try {
            long pacienteId = envelope.get("payload").get("pacienteId").asLong();
            int score = envelope.get("payload").get("scoreValor").asInt();
            Instant occurredAt = Instant.parse(envelope.get("occurredAt").asText());

            atualizarScoreReplica.atualizar(pacienteId, score, occurredAt, eventId);
        } catch (RuntimeException e) {
            // Distinto de "malformado" (achado do envelope valido, mas o
            // upsert falhou -- ex.: DB indisponivel) -- mesmo tratamento:
            // mensagem NAO removida, retry na proxima execucao.
            log.error("Falha ao aplicar o upsert da replica de Score (messageId={}, eventId={}, "
                    + "correlationId={}) -- mensagem NAO removida, retry na proxima execucao",
                    mensagem.messageId(), eventId, correlationId, e);
            return;
        }

        try {
            // So remove a mensagem da fila apos o upsert confirmado
            // (Boundaries da spec 3.1b) -- nunca antes.
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(mensagem.receiptHandle())
                    .build());
        } catch (RuntimeException e) {
            // Upsert ja confirmado -- se o delete falhar, a mensagem sera
            // reentregue e o upsert repetido e um no-op (mesmo eventId,
            // ScoreReplica#maisRecenteQue nunca "mais recente que si
            // mesma") -- idempotente, nao propaga.
            log.error("Falha ao remover da fila a mensagem ja processada (messageId={}, eventId={}, "
                    + "correlationId={}) -- sera reentregue, reprocessamento e idempotente",
                    mensagem.messageId(), eventId, correlationId, e);
        }
    }

    private static void validarEnvelope(JsonNode envelope) {
        if (envelope == null || envelope.isNull()) {
            throw new IllegalArgumentException("corpo da mensagem nao e um JSON valido");
        }
        JsonNode eventTypeNode = envelope.get("eventType");
        if (eventTypeNode == null || !EVENT_TYPE_ESPERADO.equals(eventTypeNode.asText())) {
            throw new IllegalArgumentException("eventType inesperado: "
                    + (eventTypeNode == null ? null : eventTypeNode.asText()));
        }
        JsonNode payload = envelope.get("payload");
        if (payload == null || payload.get("pacienteId") == null || payload.get("scoreValor") == null) {
            throw new IllegalArgumentException("payload sem pacienteId/scoreValor");
        }
        // Achado do code review: JsonNode#asInt() de um scoreValor
        // nao-numerico (ex.: null JSON, string) retorna 0 em vez de lancar
        // -- sem esta checagem explicita de tipo, um payload malformado
        // desse jeito corromperia a replica com Score=0 em vez de cair no
        // fluxo de erro/retry/DLQ ja existente para payload malformado.
        if (!payload.get("scoreValor").isNumber()) {
            throw new IllegalArgumentException("payload.scoreValor nao e numerico");
        }
        if (envelope.get("occurredAt") == null || envelope.get("eventId") == null) {
            throw new IllegalArgumentException("envelope sem occurredAt/eventId");
        }
    }
}
