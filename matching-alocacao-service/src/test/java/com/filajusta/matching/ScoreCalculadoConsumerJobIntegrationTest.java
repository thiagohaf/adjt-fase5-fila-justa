package com.filajusta.matching;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificação ponta a ponta de {@code ScoreCalculadoConsumerJob} (Story
 * 3.1b) contra uma fila SQS FIFO real via Testcontainers-LocalStack --
 * mesmo precedente de LocalStack da Story 3.0
 * ({@code RelaySnsPublisherJobIntegrationTest}, {@code localstack:4.12.0}).
 * Envia a mensagem DIRETO na fila (mesmo formato bruto que uma subscription
 * SNS com {@code RawMessageDelivery=true} entregaria) -- a entrega
 * SNS-&gt;SQS em si já foi provada pela suíte da Story 3.0; este teste prova
 * o lado do consumidor: réplica upsertada e mensagem removida da fila só
 * após o upsert confirmado.
 *
 * <p>Cobre o último cenário da I/O &amp; Edge-Case Matrix da spec 3.1b:
 * "Consumo normal -- réplica local upsertada; mensagem removida da fila".
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ScoreCalculadoConsumerJobIntegrationTest {

    private static final String FILA = "score-calculado-matching-test.fifo";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // 4.12.0 (nao a numeracao "2026.MM"): a partir da nova numeracao por
    // calendario a imagem community exige LOCALSTACK_AUTH_TOKEN mesmo so
    // para SQS -- mesmo achado documentado em RelaySnsPublisherJobIntegrationTest.
    @Container
    static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:4.12.0")
            .withServices("sqs");

    private static String queueUrl;

    @DynamicPropertySource
    static void relayProperties(DynamicPropertyRegistry registry) {
        try (SqsClient sqs = sqsClient()) {
            queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                            .queueName(FILA)
                            .attributes(Map.of(
                                    QueueAttributeName.FIFO_QUEUE, "true",
                                    QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true"))
                            .build())
                    .queueUrl();
        }

        registry.add("filajusta.matching.relay.endpoint-override", () -> localstack.getEndpoint().toString());
        registry.add("filajusta.matching.relay.region", localstack::getRegion);
        registry.add("filajusta.matching.relay.queue-url", () -> queueUrl);
        registry.add("filajusta.matching.relay.poll-interval-ms", () -> "500");
        registry.add("filajusta.matching.relay.wait-time-seconds", () -> "1");
        // Relay outbox (Story 3-3a) desligado aqui -- este teste so cobre o
        // consumidor SQS de ScoreCalculado, sem topico SNS/LocalStack para
        // ele; sem isso o RelaySnsPublisherJob falharia ao subir (topic-arn
        // vazio, fail-fast).
        registry.add("filajusta.matching.outbox-relay.enabled", () -> "false");
    }

    private static SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mensagemScoreCalculadoValidaENaFilaEUpsertadaNaReplicaERemovidaDaFila() throws Exception {
        long pacienteId = 4242L;
        UUID eventId = UUID.randomUUID();
        String occurredAt = "2026-09-08T12:00:00Z";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pacienteId", pacienteId);
        payload.put("triagemId", 99L);
        payload.put("scoreValor", 73);
        payload.put("algoritmoVersao", "v1");

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "ScoreCalculado");
        envelope.put("occurredAt", occurredAt);
        envelope.put("version", 1);
        envelope.put("correlationId", "corr-" + eventId);
        envelope.put("payload", payload);

        try (SqsClient sqs = sqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(envelope))
                    .messageGroupId(String.valueOf(pacienteId))
                    .build());
        }

        aguardarReplicaUpsertada(pacienteId, eventId);
        aguardarFilaVazia();
    }

    private void aguardarReplicaUpsertada(long pacienteId, UUID eventId) throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            var linhas = jdbcTemplate.queryForList(
                    "SELECT score, event_id, numero_sequencial_triagem FROM matching_alocacao.score_replica "
                            + "WHERE paciente_id = ?", pacienteId);
            if (!linhas.isEmpty()) {
                assertThat(linhas).hasSize(1);
                assertThat(linhas.get(0).get("score")).isEqualTo(73);
                assertThat(linhas.get(0).get("event_id").toString()).isEqualTo(eventId.toString());
                // Story 3.2b1: payload.triagemId (99L no envelope enviado
                // acima) propagado ate numero_sequencial_triagem.
                assertThat(linhas.get(0).get("numero_sequencial_triagem")).isEqualTo(99L);
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Replica de Score para pacienteId=" + pacienteId + " nao foi upsertada em 20s "
                + "-- consumidor nao processou a mensagem?");
    }

    private void aguardarFilaVazia() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        try (SqsClient sqs = sqsClient()) {
            while (System.currentTimeMillis() < limite) {
                String aproximado = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                                .queueUrl(queueUrl)
                                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                                .build())
                        .attributesAsStrings()
                        .values()
                        .stream()
                        .reduce((a, b) -> String.valueOf(Integer.parseInt(a) + Integer.parseInt(b)))
                        .orElse("0");
                if ("0".equals(aproximado)) {
                    return;
                }
                Thread.sleep(200);
            }
        }
        throw new AssertionError("Mensagem nao foi removida da fila SQS FIFO em 20s apos o upsert -- "
                + "deleteMessage nao foi chamado?");
    }
}
