package com.filajusta.triagem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacao ponta a ponta do {@code RelaySnsPublisherJob} (Story 3.0)
 * contra SNS FIFO + SQS FIFO reais via Testcontainers-LocalStack -- primeiro
 * precedente de LocalStack no projeto (Ask First da spec 3.0, decidido com o
 * usuario em 2026-09-08). A fila SQS FIFO + DLQ assinam o topico so para
 * este teste ler de volta a mensagem publicada e confirmar o envelope; nao
 * ha consumidor de producao (isso e Story 3.1).
 *
 * <p>Cobre o ultimo cenario da I/O &amp; Edge-Case Matrix da spec 3.0:
 * "Verificacao ponta a ponta (LocalStack) -- mensagem chega na fila com o
 * envelope completo e MessageGroupId correto".
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RelaySnsPublisherJobIntegrationTest {

    private static final String TOPICO = "score-calculado.fifo";
    private static final String FILA_ASSINANTE = "score-calculado-test-subscriber.fifo";
    private static final String FILA_DLQ = "score-calculado-test-dlq.fifo";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // 4.12.0 (nao a linha de release "2026.MM" mais recente): a partir da
    // nova numeracao por calendario, a imagem community exige
    // LOCALSTACK_AUTH_TOKEN mesmo so para SNS/SQS (verificado ao vivo --
    // "License activation failed", exit code 55) -- 4.12.0 e a ultima tag
    // da numeracao classica, roda SNS/SQS sem nenhum token.
    @Container
    static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:4.12.0")
            .withServices("sns", "sqs");

    private static String topicArn;
    private static String queueUrl;
    private static String dlqArn;

    @DynamicPropertySource
    static void relayProperties(DynamicPropertyRegistry registry) {
        try (SqsClient sqs = sqsClient(); SnsClient sns = snsClient()) {
            dlqArn = criarFilaFifo(sqs, FILA_DLQ, Map.of());

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
            // publicado direto, sem o wrapper JSON padrao do SNS -- mais
            // fiel ao que um consumidor real (Story 3.1) vai ler.
            sns.subscribe(SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("sqs")
                    .endpoint(queueArn)
                    .attributes(Map.of("RawMessageDelivery", "true"))
                    .build());
        }

        registry.add("filajusta.triagem.relay.endpoint-override", () -> localstack.getEndpoint().toString());
        registry.add("filajusta.triagem.relay.region", localstack::getRegion);
        registry.add("filajusta.triagem.relay.topic-arn", () -> topicArn);
        registry.add("filajusta.triagem.relay.poll-interval-ms", () -> "500");
    }

    private static String criarFilaFifo(SqsClient sqs, String nome, Map<QueueAttributeName, String> atributosExtras) {
        Map<QueueAttributeName, String> atributos = new LinkedHashMap<>(atributosExtras);
        atributos.put(QueueAttributeName.FIFO_QUEUE, "true");
        String url = sqs.createQueue(CreateQueueRequest.builder().queueName(nome).attributes(atributos).build())
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

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void eventoScoreCalculadoChegaNaFilaSqsComEnvelopeCompletoEMessageGroupIdCorreto() throws Exception {
        String correlationId = "corr-" + UUID.randomUUID();

        Map<String, Object> sinaisVitais = new LinkedHashMap<>();
        sinaisVitais.put("frequenciaCardiaca", 85.0);
        sinaisVitais.put("pressaoArterialSistolica", 120.0);
        sinaisVitais.put("pressaoArterialDiastolica", 80.0);
        sinaisVitais.put("saturacaoOxigenio", 97.0);
        sinaisVitais.put("frequenciaRespiratoria", 18.0);
        sinaisVitais.put("temperatura", 36.7);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpf", "529.982.247-25");
        body.put("sinaisVitais", sinaisVitais);
        body.put("gravidadePercebida", "GRAVE");
        body.put("sintomas", List.of("dor no peito"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", correlationId)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);

        JsonNode responseBody = objectMapper.readTree(response.body());
        long pacienteId = responseBody.get("pacienteId").asLong();
        long triagemId = responseBody.get("triagemId").asLong();

        Message mensagem = aguardarMensagemNaFilaAssinante();

        assertThat(mensagem.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID))
                .isEqualTo(String.valueOf(pacienteId));

        JsonNode envelope = objectMapper.readTree(mensagem.body());
        assertThat(envelope.get("eventId").asText()).isNotBlank();
        assertThat(envelope.get("eventType").asText()).isEqualTo("ScoreCalculado");
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("correlationId").asText()).isEqualTo(correlationId);
        assertThat(envelope.get("payload").get("pacienteId").asLong()).isEqualTo(pacienteId);
        assertThat(envelope.get("payload").get("triagemId").asLong()).isEqualTo(triagemId);

        // Confirma que a fila assinante ficou realmente amarrada a uma DLQ
        // (Ask First da spec 3.0: "fila SQS FIFO de teste + DLQ").
        try (SqsClient sqs = sqsClient()) {
            String redrivePolicy = atributoDaFila(sqs, queueUrl, QueueAttributeName.REDRIVE_POLICY);
            assertThat(redrivePolicy).contains(dlqArn).contains("\"maxReceiveCount\":\"5\"");
        }
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
}
