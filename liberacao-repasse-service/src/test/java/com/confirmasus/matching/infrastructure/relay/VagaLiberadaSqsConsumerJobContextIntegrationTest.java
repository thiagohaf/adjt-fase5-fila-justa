package com.confirmasus.matching.infrastructure.relay;

import com.confirmasus.matching.application.command.UpsertRecurso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 6.4: exercita o wiring real do {@link VagaLiberadaSqsConsumerJob}
 * (contexto Spring com {@code enabled=true}, {@code ObjectMapper} do Boot 4,
 * {@code SqsClient} do {@link VagaLiberadaSqsClientConfig}) contra SQS FIFO
 * real via LocalStack. O bug de Jackson 2 vs 3 da Story 6.3 passou pelos
 * testes unitários justamente porque nenhum deles subia o contexto.
 *
 * <p>A mensagem é enviada já embrulhada no formato do SNS sem raw delivery
 * ({@code {"Type":"Notification","Message":"<envelope>"}}), como no compose.
 */
@Testcontainers
@SpringBootTest
class VagaLiberadaSqsConsumerJobContextIntegrationTest {

    private static final String FILA = "vaga-liberada-contexto-test.fifo";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // Mesma tag dos demais testes: 4.12.0 é a última que roda sem token.
    @Container
    static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:4.12.0")
            .withServices("sqs");

    private static String queueUrl;

    @DynamicPropertySource
    static void consumerProperties(DynamicPropertyRegistry registry) {
        try (SqsClient sqs = sqsClient()) {
            queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                            .queueName(FILA)
                            .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true"))
                            .build())
                    .queueUrl();
        }
        registry.add("confirmasus.matching.vaga-liberada-consumer.enabled", () -> "true");
        registry.add("confirmasus.matching.vaga-liberada-consumer.queue-url", () -> queueUrl);
        registry.add("confirmasus.matching.vaga-liberada-consumer.endpoint-override",
                () -> localstack.getEndpoint().toString());
        registry.add("confirmasus.matching.vaga-liberada-consumer.region", localstack::getRegion);
        registry.add("confirmasus.matching.vaga-liberada-consumer.poll-interval-ms", () -> "300");
        registry.add("confirmasus.matching.vaga-liberada-consumer.wait-time-seconds", () -> "0");
        // Consumidor de ScoreCalculado e outbox relay fora deste teste.
        registry.add("confirmasus.matching.relay.enabled", () -> "false");
        registry.add("confirmasus.matching.outbox-relay.enabled", () -> "false");
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
    private ApplicationContext contexto;

    @Autowired
    private UpsertRecurso upsertRecurso;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void contextoSobeComOConsumidorLigado() {
        assertThat(contexto.getBeansOfType(VagaLiberadaSqsConsumerJob.class)).hasSize(1);
    }

    @Test
    void mensagemEmbrulhadaPeloSnsGeraSugestaoPendenteEERemovidaDaFila() throws Exception {
        UUID recursoId = upsertRecurso.upsertar("RECURSO-" + UUID.randomUUID(), 1, true, null, null)
                .recurso().getRecursoId();
        long pacienteId = novoPacienteNaListaDeEspera(recursoId);
        long agendamentoId = 9000 + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100000);

        try (SqsClient sqs = sqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageGroupId(recursoId.toString())
                    .messageDeduplicationId(UUID.randomUUID().toString())
                    .messageBody(notificacaoSns(agendamentoId, recursoId))
                    .build());

            aguardar(() -> sugestoes(agendamentoId) == 1);
            aguardar(() -> mensagensNaFila(sqs) == 0);
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM matching_alocacao.sugestao_repasse WHERE agendamento_id = ?",
                String.class, agendamentoId)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject(
                "SELECT paciente_id FROM matching_alocacao.sugestao_repasse WHERE agendamento_id = ?",
                Long.class, agendamentoId)).isEqualTo(pacienteId);
    }

    private String notificacaoSns(long agendamentoId, UUID recursoId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agendamentoId", agendamentoId);
        payload.put("recursoId", recursoId.toString());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "VagaLiberada");
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("version", 1);
        envelope.put("correlationId", "corr-" + UUID.randomUUID());
        envelope.put("payload", payload);
        Map<String, Object> notificacao = new LinkedHashMap<>();
        notificacao.put("Type", "Notification");
        notificacao.put("Message", objectMapper.writeValueAsString(envelope));
        return objectMapper.writeValueAsString(notificacao);
    }

    private long novoPacienteNaListaDeEspera(UUID recursoId) {
        String cpf = String.valueOf(10000000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 89999999999L));
        long id = jdbc.queryForObject(
                "INSERT INTO matching_alocacao.paciente (cpf) VALUES (?) RETURNING paciente_id", Long.class, cpf);
        Timestamp agora = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO matching_alocacao.lista_espera_entrada "
                        + "(paciente_id, recurso_id, data_solicitacao, criado_em) VALUES (?, ?, ?, ?)",
                id, recursoId, agora, agora);
        return id;
    }

    private int sugestoes(long agendamentoId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.sugestao_repasse WHERE agendamento_id = ?",
                Integer.class, agendamentoId);
    }

    private int mensagensNaFila(SqsClient sqs) {
        Map<QueueAttributeName, String> a = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                        .build())
                .attributes();
        return Integer.parseInt(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    private static void aguardar(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("condicao nao atendida em 30s");
    }
}
