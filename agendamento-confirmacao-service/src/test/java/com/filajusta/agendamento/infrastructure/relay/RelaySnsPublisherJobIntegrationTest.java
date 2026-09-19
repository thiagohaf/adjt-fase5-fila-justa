package com.filajusta.agendamento.infrastructure.relay;

import com.filajusta.agendamento.application.command.EventoOutboxRepositorio;
import com.filajusta.agendamento.domain.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacao ponta a ponta do {@link RelaySnsPublisherJob} (spec 1.2)
 * contra SNS FIFO + SQS FIFO reais via Testcontainers-LocalStack --
 * adaptado de {@code matching-alocacao-service/.../
 * RelaySnsPublisherJobIntegrationTest.java}. A linha e inserida DIRETO via
 * {@link EventoOutboxRepositorio#salvar(EventoOutbox)} (o produtor real,
 * {@code AbrirJanelaDeConfirmacao}, e coberto separadamente por
 * {@code AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest}).
 *
 * <p>Cobre o envelope completo com {@code MessageGroupId=agendamentoId} e a
 * Acceptance Criteria da spec 1.2: "uma linha ja marcada como publicada,
 * quando o job roda de novo, nao e republicada".
 */
@Testcontainers
@SpringBootTest
class RelaySnsPublisherJobIntegrationTest {

    private static final String TOPICO = "agendamento-confirmacao-eventos-test.fifo";
    private static final String FILA_ASSINANTE = "agendamento-confirmacao-eventos-test-subscriber.fifo";
    private static final String FILA_DLQ = "agendamento-confirmacao-eventos-test-dlq.fifo";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // Mesma tag ja provada em matching-alocacao-service -- a partir da
    // numeracao por calendario a imagem community exige LOCALSTACK_AUTH_TOKEN
    // mesmo so para SNS/SQS; 4.12.0 e a ultima tag da numeracao classica,
    // roda sem nenhum token.
    @Container
    static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:4.12.0")
            .withServices("sns", "sqs");

    private static String topicArn;
    private static String queueUrl;
    private static String dlqArn;

    @DynamicPropertySource
    static void relayProperties(DynamicPropertyRegistry registry) {
        try (SqsClient sqs = sqsClient(); SnsClient sns = snsClient()) {
            dlqArn = criarFilaFifo(sqs, FILA_DLQ);

            String redrivePolicy = "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"5\"}";
            queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                            .queueName(FILA_ASSINANTE)
                            .attributes(Map.of(
                                    QueueAttributeName.FIFO_QUEUE, "true",
                                    QueueAttributeName.REDRIVE_POLICY, redrivePolicy))
                            .build())
                    .queueUrl();
            String queueArn = atributoDaFila(sqs, queueUrl, QueueAttributeName.QUEUE_ARN);

            topicArn = sns.createTopic(CreateTopicRequest.builder()
                            .name(TOPICO)
                            .attributes(Map.of("FifoTopic", "true", "ContentBasedDeduplication", "false"))
                            .build())
                    .topicArn();

            sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributes(Map.of(QueueAttributeName.POLICY, allowSnsPublishPolicy(queueArn, topicArn)))
                    .build());

            sns.subscribe(SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("sqs")
                    .endpoint(queueArn)
                    .attributes(Map.of("RawMessageDelivery", "true"))
                    .build());
        }

        registry.add("filajusta.agendamento.outbox-relay.enabled", () -> "true");
        registry.add("filajusta.agendamento.outbox-relay.endpoint-override",
                () -> localstack.getEndpoint().toString());
        registry.add("filajusta.agendamento.outbox-relay.region", localstack::getRegion);
        registry.add("filajusta.agendamento.outbox-relay.topic-arn", () -> topicArn);
        registry.add("filajusta.agendamento.outbox-relay.poll-interval-ms", () -> "300");
    }

    private static String criarFilaFifo(SqsClient sqs, String nome) {
        String url = sqs.createQueue(CreateQueueRequest.builder()
                        .queueName(nome)
                        .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true"))
                        .build())
                .queueUrl();
        return atributoDaFila(sqs, url, QueueAttributeName.QUEUE_ARN);
    }

    private static String atributoDaFila(SqsClient sqs, String queueUrl, QueueAttributeName atributo) {
        return sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(atributo)
                        .build())
                .attributes()
                .get(atributo);
    }

    private static String allowSnsPublishPolicy(String queueArn, String topicArn) {
        return "{"
                + "\"Version\":\"2012-10-17\","
                + "\"Statement\":[{"
                + "\"Effect\":\"Allow\","
                + "\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                + "\"Action\":\"sqs:SendMessage\","
                + "\"Resource\":\"" + queueArn + "\","
                + "\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + topicArn + "\"}}"
                + "}]}";
    }

    private static SnsClient snsClient() {
        return SnsClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
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
    private EventoOutboxRepositorio eventoOutboxRepositorio;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void linhaSalvaDiretoNoOutboxChegaNaFilaComEnvelopeCompletoMessageGroupIdAgendamentoIdENuncaERepublicada()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-18T12:00:00Z");
        String correlationId = "corr-" + UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agendamentoId", 99L);
        payload.put("pacienteId", 7L);

        eventoOutboxRepositorio.salvar(new EventoOutbox(null, eventId, "NotificacaoConfirmacaoPublicada",
                occurredAt, 1, correlationId, payload));

        Message mensagem = aguardarMensagemNaFilaAssinante();

        assertThat(mensagem.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID)).isEqualTo("99");

        JsonNode envelope = objectMapper.readTree(mensagem.body());
        assertThat(envelope.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("NotificacaoConfirmacaoPublicada");
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("correlationId").asText()).isEqualTo(correlationId);
        assertThat(envelope.get("payload").get("agendamentoId").asLong()).isEqualTo(99L);
        assertThat(envelope.get("payload").get("pacienteId").asLong()).isEqualTo(7L);

        try (SqsClient sqs = sqsClient()) {
            String redrivePolicy = atributoDaFila(sqs, queueUrl, QueueAttributeName.REDRIVE_POLICY);
            assertThat(redrivePolicy).contains(dlqArn).contains("\"maxReceiveCount\":\"5\"");
        }

        List<Message> mensagensExtras = tentarReceberMaisMensagensPorUmTempo();
        assertThat(mensagensExtras)
                .as("o job rodando de novo apos marcar a linha como publicada nao pode republicar o mesmo evento")
                .isEmpty();
    }

    private Message aguardarMensagemNaFilaAssinante() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        try (SqsClient sqs = sqsClient()) {
            while (System.currentTimeMillis() < limite) {
                List<Message> mensagens = sqs.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds(2)
                                .messageSystemAttributeNames(MessageSystemAttributeName.MESSAGE_GROUP_ID)
                                .build())
                        .messages();
                if (!mensagens.isEmpty()) {
                    return mensagens.get(0);
                }
                Thread.sleep(200);
            }
        }
        throw new AssertionError("Nenhuma mensagem chegou na fila assinante em 20s -- relay nao publicou?");
    }

    private List<Message> tentarReceberMaisMensagensPorUmTempo() throws InterruptedException {
        List<Message> recebidas = new ArrayList<>();
        long limite = System.currentTimeMillis() + 3_000;
        try (SqsClient sqs = sqsClient()) {
            while (System.currentTimeMillis() < limite) {
                List<Message> mensagens = sqs.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(10)
                                .waitTimeSeconds(1)
                                .build())
                        .messages();
                recebidas.addAll(mensagens);
            }
        }
        return recebidas;
    }
}
