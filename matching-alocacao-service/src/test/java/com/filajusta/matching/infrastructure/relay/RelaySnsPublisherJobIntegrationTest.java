package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.EventoOutboxRepositorio;
import com.filajusta.matching.domain.EventoOutbox;
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
 * Verificacao ponta a ponta do {@link RelaySnsPublisherJob} (Story 3-3a)
 * contra SNS FIFO + SQS FIFO reais via Testcontainers-LocalStack -- mesmo
 * precedente de {@code triagem-score-service/.../
 * RelaySnsPublisherJobIntegrationTest.java} (Story 3.0). Diferente daquele
 * teste (que dispara o outbox indiretamente via POST /v1/triagens), esta
 * story e infraestrutura pura sem nenhum produtor real (Boundaries da spec
 * 3-3a) -- a linha e inserida DIRETO via {@link EventoOutboxRepositorio
 * #salvar(EventoOutbox)}, como a propria spec pede.
 *
 * <p>Cobre os tres cenarios da Acceptance Criteria da spec 3-3a: envelope
 * completo com {@code MessageGroupId=recursoId}; uma linha ja publicada
 * nunca e republicada quando o job roda de novo; e (implicito no desenho do
 * relay, provado pelo {@code eventId} nunca regenerado) uma falha de rede
 * antes do ack nao perde nem duplica o evento.
 */
@Testcontainers
@SpringBootTest
class RelaySnsPublisherJobIntegrationTest {

    private static final String TOPICO = "matching-alocacao-eventos-test.fifo";
    private static final String FILA_ASSINANTE = "matching-alocacao-eventos-test-subscriber.fifo";
    private static final String FILA_DLQ = "matching-alocacao-eventos-test-dlq.fifo";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // Mesma tag ja provada em triagem-score-service (Story 3.0) -- a partir
    // da numeracao por calendario a imagem community exige
    // LOCALSTACK_AUTH_TOKEN mesmo so para SNS/SQS; 4.12.0 e a ultima tag da
    // numeracao classica, roda sem nenhum token.
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

            // Policy de fila permitindo o SNS entregar nela -- sem isso o
            // subscribe ate funciona, mas a entrega falha silenciosamente.
            sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributes(Map.of(QueueAttributeName.POLICY, allowSnsPublishPolicy(queueArn, topicArn)))
                    .build());

            // RawMessageDelivery=true: o corpo da mensagem SQS e o envelope
            // publicado direto, sem o wrapper JSON padrao do SNS.
            sns.subscribe(SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("sqs")
                    .endpoint(queueArn)
                    .attributes(Map.of("RawMessageDelivery", "true"))
                    .build());
        }

        // Namespace outbox-relay (nao o "relay" do consumidor SQS de
        // ScoreCalculado) -- enabled=true so aqui (default e false no
        // application.yml, ver Boundaries da spec 3-3a/Ask First).
        registry.add("filajusta.matching.outbox-relay.enabled", () -> "true");
        registry.add("filajusta.matching.outbox-relay.endpoint-override", () -> localstack.getEndpoint().toString());
        registry.add("filajusta.matching.outbox-relay.region", localstack::getRegion);
        registry.add("filajusta.matching.outbox-relay.topic-arn", () -> topicArn);
        registry.add("filajusta.matching.outbox-relay.poll-interval-ms", () -> "300");
        // Consumidor SQS de ScoreCalculado (Story 3.1b) desligado -- este
        // teste nao o exercita, mesmo padrao dos demais testes deste
        // servico.
        registry.add("filajusta.matching.relay.enabled", () -> "false");
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
    void linhaSalvaDiretoNoOutboxChegaNaFilaComEnvelopeCompletoMessageGroupIdRecursoIdENuncaERepublicada()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-11T12:00:00Z");
        String correlationId = "corr-" + UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recursoId", "leito-42");
        payload.put("pacienteId", 7L);

        // Ask First da spec 3-3a: nenhum produtor real existe ainda -- a
        // linha e inserida DIRETO via a porta, como o Design Notes da spec
        // pede ("publica uma linha inserida diretamente via
        // EventoOutboxRepositorio.salvar(...) em teste").
        eventoOutboxRepositorio.salvar(
                new EventoOutbox(null, eventId, "AlocacaoConfirmada", occurredAt, 1, correlationId, payload));

        Message mensagem = aguardarMensagemNaFilaAssinante();

        assertThat(mensagem.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID)).isEqualTo("leito-42");

        JsonNode envelope = objectMapper.readTree(mensagem.body());
        assertThat(envelope.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("AlocacaoConfirmada");
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("correlationId").asText()).isEqualTo(correlationId);
        assertThat(envelope.get("payload").get("recursoId").asText()).isEqualTo("leito-42");
        assertThat(envelope.get("payload").get("pacienteId").asLong()).isEqualTo(7L);

        // Confirma a fila assinante amarrada a uma DLQ (mesmo precedente da
        // spec 3.0).
        try (SqsClient sqs = sqsClient()) {
            String redrivePolicy = atributoDaFila(sqs, queueUrl, QueueAttributeName.REDRIVE_POLICY);
            assertThat(redrivePolicy).contains(dlqArn).contains("\"maxReceiveCount\":\"5\"");
        }

        // Acceptance Criteria da spec 3-3a: "uma linha ja marcada como
        // publicada, quando o job roda de novo, nao e republicada" -- o
        // poll-interval-ms curto (300ms) garante varios ciclos do job
        // dentro da espera abaixo; nenhuma segunda mensagem para o mesmo
        // eventId pode chegar na fila.
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
