package com.confirmasus.matching.infrastructure.relay;

import com.confirmasus.matching.application.command.GerarSugestaoRepasse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.UUID;

/**
 * Consumidor SQS FIFO de {@code VagaLiberada} (Story 6.1, AD-3/AD-6), molde
 * {@code DecisaoSqsConsumerJob} (auditoria-service). Envelope do outbox:
 * {@code {eventId, eventType, occurredAt, version, correlationId, payload}};
 * sem raw message delivery no SNS o envelope vem embrulhado em
 * {@code {"Type":"Notification","Message":"<json>"}} e é desembrulhado aqui.
 *
 * <p>Só apaga a mensagem após {@link GerarSugestaoRepasse#gerar} concluir
 * (inclusive {@code DUPLICADA}, idempotente por {@code agendamentoId}).
 * Malformada ou falha de processamento: não apaga -- reentregue após o
 * VisibilityTimeout e movida à DLQ pelo SQS em {@code maxReceiveCount}.
 * Eventos de outro {@code eventType} são apagados sem efeito (não são
 * desta fila de negócio).
 *
 * <p>Kill switch: {@code confirmasus.matching.vaga-liberada-consumer.enabled}
 * (default desligado até a fila existir no CDK).
 */
@Component
@ConditionalOnProperty(prefix = "confirmasus.matching.vaga-liberada-consumer", name = "enabled")
class VagaLiberadaSqsConsumerJob {

    private static final Logger log = LoggerFactory.getLogger(VagaLiberadaSqsConsumerJob.class);
    private static final String EVENT_TYPE = "VagaLiberada";

    private final SqsClient sqsClient;
    private final GerarSugestaoRepasse gerarSugestaoRepasse;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final int loteTamanho;
    private final int waitTimeSeconds;

    VagaLiberadaSqsConsumerJob(
            SqsClient sqsClient,
            GerarSugestaoRepasse gerarSugestaoRepasse,
            ObjectMapper objectMapper,
            @Value("${confirmasus.matching.vaga-liberada-consumer.queue-url}") String queueUrl,
            @Value("${confirmasus.matching.vaga-liberada-consumer.batch-size:10}") int loteTamanho,
            @Value("${confirmasus.matching.vaga-liberada-consumer.wait-time-seconds:1}") int waitTimeSeconds) {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("confirmasus.matching.vaga-liberada-consumer.queue-url nao pode ser "
                    + "vazio com confirmasus.matching.vaga-liberada-consumer.enabled=true");
        }
        this.sqsClient = sqsClient;
        this.gerarSugestaoRepasse = gerarSugestaoRepasse;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        this.loteTamanho = Math.min(10, Math.max(1, loteTamanho));
        this.waitTimeSeconds = Math.min(20, Math.max(0, waitTimeSeconds));
    }

    @Scheduled(fixedDelayString = "${confirmasus.matching.vaga-liberada-consumer.poll-interval-ms:5000}")
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
            log.error("Falha ao receber mensagens da fila de VagaLiberada -- tenta de novo no proximo ciclo", e);
            return;
        }
        mensagens.forEach(this::processar);
    }

    private void processar(Message mensagem) {
        long agendamentoId;
        UUID recursoId;
        String correlationId;
        try {
            JsonNode envelope = desembrulhar(objectMapper.readTree(mensagem.body()));
            String eventType = texto(envelope, "eventType");
            if (!EVENT_TYPE.equals(eventType)) {
                log.warn("Evento {} ignorado na fila de VagaLiberada (messageId={})", eventType, mensagem.messageId());
                apagar(mensagem);
                return;
            }
            JsonNode payload = envelope.path("payload");
            if (!payload.path("agendamentoId").canConvertToLong()) {
                throw new IllegalArgumentException("payload sem agendamentoId numerico");
            }
            agendamentoId = payload.get("agendamentoId").asLong();
            recursoId = UUID.fromString(texto(payload, "recursoId"));
            correlationId = envelope.hasNonNull("correlationId") ? envelope.get("correlationId").asText() : null;
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Mensagem malformada na fila de VagaLiberada (messageId={}) -- nao removida, "
                    + "sera reentregue ate ir para a DLQ", mensagem.messageId(), e);
            return;
        }

        try {
            GerarSugestaoRepasse.Resultado resultado = gerarSugestaoRepasse.gerar(agendamentoId, recursoId, correlationId);
            log.info("VagaLiberada processada (messageId={}, agendamentoId={}, recursoId={}): {}",
                    mensagem.messageId(), agendamentoId, recursoId, resultado);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar Sugestao de Repasse (messageId={}, agendamentoId={}) -- mensagem NAO "
                    + "removida, retry na proxima execucao", mensagem.messageId(), agendamentoId, e);
            return;
        }
        apagar(mensagem);
    }

    private JsonNode desembrulhar(JsonNode corpo) throws JsonProcessingException {
        if (corpo.has("Message") && !corpo.has("eventType")) {
            return objectMapper.readTree(corpo.get("Message").asText());
        }
        return corpo;
    }

    private static String texto(JsonNode no, String campo) {
        if (!no.hasNonNull(campo)) {
            throw new IllegalArgumentException("campo ausente: " + campo);
        }
        return no.get(campo).asText();
    }

    private void apagar(Message mensagem) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(mensagem.receiptHandle())
                    .build());
        } catch (RuntimeException e) {
            log.error("Falha ao remover mensagem ja processada (messageId={}) -- sera reentregue, "
                    + "reprocessamento e idempotente", mensagem.messageId(), e);
        }
    }
}
