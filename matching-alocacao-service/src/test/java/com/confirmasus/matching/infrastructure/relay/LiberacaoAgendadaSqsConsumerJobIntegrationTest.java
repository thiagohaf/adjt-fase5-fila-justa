package com.confirmasus.matching.infrastructure.relay;

import com.confirmasus.matching.application.command.AlocacaoRepositorio;
import com.confirmasus.matching.application.command.RecursoRepositorio;
import com.confirmasus.matching.application.query.AlocacaoConsultaRepositorio;
import com.confirmasus.matching.application.query.RecursoConsultaRepositorio;
import com.confirmasus.matching.domain.Alocacao;
import com.confirmasus.matching.domain.Recurso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificação ponta a ponta de {@link LiberacaoAgendadaSqsConsumerJob} (Story
 * 3-4b2) contra uma fila SQS standard real via Testcontainers-LocalStack --
 * mesmo precedente de LocalStack de {@link LiberacaoAgendadaRelayJobIntegrationTest}.
 *
 * <p>Cobre as Acceptance Criteria: mensagem válida é consumida, LiberarRecurso
 * é invocado (verificando que Alocação foi liberada e Recurso marcado
 * disponível), mensagem é deletada da fila (HAPPY_PATH da I/O &amp; Edge-Case
 * Matrix).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiberacaoAgendadaSqsConsumerJobIntegrationTest {

    private static final String FILA_PRINCIPAL = "liberacao-agendada-test";
    private static final String FILA_DLQ = "liberacao-agendada-test-dlq";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // 4.12.0 (não a numeração "2026.MM"): a partir da nova numeração por
    // calendário a imagem community exige LOCALSTACK_AUTH_TOKEN mesmo só
    // para SQS -- mesmo achado documentado em LiberacaoAgendadaRelayJobIntegrationTest.
    @Container
    static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:4.12.0")
            .withServices("sqs");

    private static String queueUrl;
    private static String dlqUrl;

    @DynamicPropertySource
    static void consumerProperties(DynamicPropertyRegistry registry) {
        try (SqsClient sqs = sqsClient()) {
            dlqUrl = sqs.createQueue(CreateQueueRequest.builder()
                            .queueName(FILA_DLQ)
                            .build())
                    .queueUrl();

            queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                            .queueName(FILA_PRINCIPAL)
                            .attributes(Map.of(
                                    QueueAttributeName.VISIBILITY_TIMEOUT, "60",
                                    QueueAttributeName.MESSAGE_RETENTION_PERIOD, "86400",
                                    QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20"))
                            .build())
                    .queueUrl();
        }

        registry.add("confirmasus.matching.liberacao-agendada-consumer.enabled", () -> "true");
        registry.add("confirmasus.matching.liberacao-agendada-consumer.endpoint-override",
                () -> localstack.getEndpoint().toString());
        registry.add("confirmasus.matching.liberacao-agendada-consumer.region", localstack::getRegion);
        registry.add("confirmasus.matching.liberacao-agendada-consumer.queue-url", () -> queueUrl);
        registry.add("confirmasus.matching.liberacao-agendada-consumer.dlq-url", () -> dlqUrl);
        registry.add("confirmasus.matching.liberacao-agendada-consumer.poll-interval-ms", () -> "300");
        registry.add("confirmasus.matching.liberacao-agendada-consumer.batch-size", () -> "10");

        // Desabilitar relay de publicação e outbox relay para este teste
        registry.add("confirmasus.matching.liberacao-agendada-relay.enabled", () -> "false");
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
    private AlocacaoRepositorio alocacaoRepositorio;

    @Autowired
    private RecursoRepositorio recursoRepositorio;

    @Autowired
    private AlocacaoConsultaRepositorio alocacaoConsultaRepositorio;

    @Autowired
    private RecursoConsultaRepositorio recursoConsultaRepositorio;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void consumerProcessaMensagemValidaEDeleta() throws Exception {
        // Preparar: criar Alocação e Recurso no estado esperado
        UUID alocacaoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        String correlationId = "corr-" + alocacaoId;
        long pacienteId = 123L;

        recursoRepositorio.upsert(new Recurso(recursoId, "LEITO-001", 1, false));
        alocacaoRepositorio.confirmar(new Alocacao(
                alocacaoId, recursoId, pacienteId, Alocacao.STATUS_ATIVA, Instant.now()));

        // Enfileirar mensagem válida com version=1
        enfileirarMensagem(alocacaoId, recursoId, correlationId, 1, Instant.now());

        // Aguardar processamento pelo consumer
        aguardarProcessamento();

        // Verificar: Alocação deve estar LIBERADA (verificando que pacienteId está fora do ativo)
        assertThat(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva())
                .as("pacienteId não deve estar na lista de alocacoes ativas")
                .doesNotContain(pacienteId);

        // Verificar: Recurso deve estar disponível
        assertThat(recursoConsultaRepositorio.buscarPorId(recursoId))
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.isDisponivel()).isTrue());

        // Verificar: mensagem foi removida da fila principal
        List<Message> mensagensRestantes = receberSemEsperar();
        assertThat(mensagensRestantes)
                .as("mensagem deve ter sido deletada da fila principal apos processamento")
                .isEmpty();
    }

    @Test
    void consumerRejeitaMensagemComVersionIncompativel() throws Exception {
        UUID alocacaoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        String correlationId = "corr-" + alocacaoId;
        long pacienteId = 456L;

        // Preparar: Alocação e Recurso
        recursoRepositorio.upsert(new Recurso(recursoId, "LEITO-002", 1, false));
        alocacaoRepositorio.confirmar(new Alocacao(
                alocacaoId, recursoId, pacienteId, Alocacao.STATUS_ATIVA, Instant.now()));

        // Enfileirar mensagem com version=2 (incompatível)
        enfileirarMensagem(alocacaoId, recursoId, correlationId, 2, Instant.now());

        // Aguardar processamento
        aguardarProcessamento();

        // Verificar: Alocação continua ATIVA (pacienteId continua ativo)
        assertThat(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva())
                .as("pacienteId deve continuar na lista de alocacoes ativas")
                .contains(pacienteId);

        // Verificar: Recurso continua indisponível
        assertThat(recursoConsultaRepositorio.buscarPorId(recursoId))
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.isDisponivel()).isFalse());

        // Verificar: mensagem foi movida para DLQ
        try (SqsClient sqs = sqsClient()) {
            List<Message> mensagensNaDLQ = sqs.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(dlqUrl)
                            .maxNumberOfMessages(1)
                            .waitTimeSeconds(1)
                            .build())
                    .messages();
            assertThat(mensagensNaDLQ)
                    .as("mensagem com version incompatível deve estar na DLQ")
                    .isNotEmpty();
        }
    }

    @Test
    void consumerIdempotentQuandoAlocacaoJaLiberada() throws Exception {
        UUID alocacaoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        String correlationId = "corr-" + alocacaoId;
        long pacienteId = 789L;

        // Preparar: Alocação já LIBERADA e Recurso já disponível
        recursoRepositorio.upsert(new Recurso(recursoId, "LEITO-003", 1, true));
        alocacaoRepositorio.confirmar(new Alocacao(
                alocacaoId, recursoId, pacienteId, Alocacao.STATUS_LIBERADA, Instant.now()));

        // Enfileirar mensagem válida
        enfileirarMensagem(alocacaoId, recursoId, correlationId, 1, Instant.now());

        // Aguardar processamento
        aguardarProcessamento();

        // Verificar: Alocação continua LIBERADA (no-op) - não está na lista ativa
        assertThat(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva())
                .as("pacienteId não deve estar na lista de alocacoes ativas (continua LIBERADA)")
                .doesNotContain(pacienteId);

        // Verificar: Recurso continua disponível
        assertThat(recursoConsultaRepositorio.buscarPorId(recursoId))
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.isDisponivel()).isTrue());

        // Verificar: mensagem foi removida (mesmo sendo no-op)
        List<Message> mensagensRestantes = receberSemEsperar();
        assertThat(mensagensRestantes)
                .as("mensagem deve ter sido deletada mesmo com no-op")
                .isEmpty();
    }



    private void enfileirarMensagem(UUID alocacaoId, UUID recursoId, String correlationId,
                                   int version, Instant occurredAt) throws Exception {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("alocacaoId", alocacaoId.toString());
        corpo.put("recursoId", recursoId.toString());
        corpo.put("correlationId", correlationId);
        corpo.put("version", String.valueOf(version));
        corpo.put("occurredAt", occurredAt.toString());

        try (SqsClient sqs = sqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(corpo))
                    .build());
        }
    }

    private void aguardarProcessamento() throws InterruptedException {
        // Aguardar um tempo razoável para o consumer processar
        // (poll-interval-ms=300, mais margem)
        Thread.sleep(1500);
    }

    private List<Message> receberSemEsperar() {
        try (SqsClient sqs = sqsClient()) {
            return sqs.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(0)
                            .build())
                    .messages();
        }
    }

}
