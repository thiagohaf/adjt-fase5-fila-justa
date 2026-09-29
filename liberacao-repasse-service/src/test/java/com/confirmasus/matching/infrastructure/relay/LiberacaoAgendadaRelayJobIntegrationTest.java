package com.confirmasus.matching.infrastructure.relay;

import com.confirmasus.matching.application.command.LiberacaoAgendadaRepositorio;
import com.confirmasus.matching.domain.LiberacaoAgendada;
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
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacao ponta a ponta de {@link LiberacaoAgendadaRelayJob} (Story
 * 3-4a2) contra uma fila SQS standard real via Testcontainers-LocalStack --
 * mesmo precedente de LocalStack de
 * {@code RelaySnsPublisherJobIntegrationTest}/
 * {@code ScoreCalculadoConsumerJobIntegrationTest}. A linha e inserida DIRETO
 * via {@link LiberacaoAgendadaRepositorio#salvar}, como a propria spec pede
 * (nenhum produtor real -- Boundaries da spec 3-4a2, ConfirmarAlocacao ja
 * grava desde a Story 3-4a1).
 *
 * <p>Cobre a Acceptance Criteria "a fila recebe 1 mensagem com
 * DelaySeconds=N e enviado_em e gravado na mesma transacao" (HAPPY_PATH da
 * I/O &amp; Edge-Case Matrix): {@code delaySegundos} pequeno (3s, nao os
 * minutos reais de {@code LiberacaoDuracaoProperties}) para o teste ficar
 * rapido -- prova-se o DelaySeconds observando que a mensagem NAO fica
 * visivel antes do delay e FICA visivel depois, o unico jeito de observar
 * DelaySeconds via a API do SQS (nao e um atributo consultavel da fila).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiberacaoAgendadaRelayJobIntegrationTest {

    private static final String FILA = "liberacao-agendada-test";
    private static final int DELAY_SEGUNDOS = 3;

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
                            .build())
                    .queueUrl();
        }

        registry.add("confirmasus.matching.liberacao-agendada-relay.enabled", () -> "true");
        registry.add("confirmasus.matching.liberacao-agendada-relay.endpoint-override",
                () -> localstack.getEndpoint().toString());
        registry.add("confirmasus.matching.liberacao-agendada-relay.region", localstack::getRegion);
        registry.add("confirmasus.matching.liberacao-agendada-relay.queue-url", () -> queueUrl);
        registry.add("confirmasus.matching.liberacao-agendada-relay.poll-interval-ms", () -> "300");
        // Consumidor SQS de ScoreCalculado (Story 3.1b) e relay outbox
        // (Story 3-3a) desligados -- este teste nao os exercita, mesmo
        // padrao dos demais testes deste servico.
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
    private LiberacaoAgendadaRepositorio liberacaoAgendadaRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void liberacaoPendenteChegaNaFilaComDelaySecondsCorretoEEnviadoEmEGravado() throws Exception {
        UUID alocacaoId = UUID.randomUUID();
        UUID recursoId = UUID.randomUUID();
        String correlationId = "corr-" + alocacaoId;

        long inicio = System.currentTimeMillis();
        liberacaoAgendadaRepositorio.salvar(new LiberacaoAgendada(
                alocacaoId, recursoId, correlationId, DELAY_SEGUNDOS, Instant.now(), null));

        // Antes do delay decorrer, a mensagem nao pode estar visivel na fila
        // -- unica forma de observar DelaySeconds via a API do SQS.
        Thread.sleep(500);
        assertThat(receberSemEsperar())
                .as("mensagem nao pode ficar visivel antes de DelaySeconds=%ds decorrer", DELAY_SEGUNDOS)
                .isEmpty();

        Message mensagem = aguardarMensagemNaFila();
        long decorridoMs = System.currentTimeMillis() - inicio;
        assertThat(decorridoMs)
                .as("mensagem so pode ficar visivel apos DelaySeconds=%ds decorrer desde o salvar", DELAY_SEGUNDOS)
                .isGreaterThanOrEqualTo(DELAY_SEGUNDOS * 1000L - 250);

        JsonNode corpo = objectMapper.readTree(mensagem.body());
        assertThat(corpo.get("alocacaoId").asText()).isEqualTo(alocacaoId.toString());
        assertThat(corpo.get("recursoId").asText()).isEqualTo(recursoId.toString());
        assertThat(corpo.get("correlationId").asText()).isEqualTo(correlationId);

        aguardarEnviadoEmGravado(alocacaoId);
    }

    private List<Message> receberSemEsperar() {
        try (SqsClient sqs = sqsClient()) {
            return sqs.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(1)
                            .waitTimeSeconds(0)
                            .build())
                    .messages();
        }
    }

    private Message aguardarMensagemNaFila() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        try (SqsClient sqs = sqsClient()) {
            while (System.currentTimeMillis() < limite) {
                List<Message> mensagens = sqs.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds(1)
                                .build())
                        .messages();
                if (!mensagens.isEmpty()) {
                    return mensagens.get(0);
                }
            }
        }
        throw new AssertionError("Nenhuma mensagem chegou na fila em 20s -- relay nao publicou?");
    }

    private void aguardarEnviadoEmGravado(UUID alocacaoId) throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                    "SELECT enviado_em FROM matching_alocacao.liberacao_agendada WHERE alocacao_id = ?",
                    alocacaoId);
            if (!linhas.isEmpty() && linhas.get(0).get("enviado_em") != null) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("enviado_em da liberacao " + alocacaoId + " nao foi gravado em 20s");
    }
}
