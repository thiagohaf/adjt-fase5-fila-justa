package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.AtualizarScoreReplica;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ScoreCalculadoConsumerJob} com {@link SqsClient}/{@link AtualizarScoreReplica}
 * mockados (Tasks da spec 3.1b: teste unitário do consumidor cobrindo a I/O
 * Matrix) -- sem Testcontainers/rede real; o percurso ponta a ponta contra
 * SQS de verdade (LocalStack) vive em {@code ScoreCalculadoConsumerJobIntegrationTest}.
 */
class ScoreCalculadoConsumerJobTest {

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/score-calculado-matching.fifo";

    private final SqsClient sqsClient = mock(SqsClient.class);
    private final AtualizarScoreReplica atualizarScoreReplica = mock(AtualizarScoreReplica.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ScoreCalculadoConsumerJob job() {
        return new ScoreCalculadoConsumerJob(sqsClient, atualizarScoreReplica, objectMapper, QUEUE_URL, 10, 1);
    }

    private String envelopeValido(long pacienteId, int score, String occurredAt, UUID eventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pacienteId", pacienteId);
        payload.put("triagemId", 7L);
        payload.put("scoreValor", score);
        payload.put("algoritmoVersao", "v1");

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "ScoreCalculado");
        envelope.put("occurredAt", occurredAt);
        envelope.put("version", 1);
        envelope.put("correlationId", "corr-1");
        envelope.put("payload", payload);
        return objectMapper.writeValueAsString(envelope);
    }

    private static Message mensagem(String body, String messageId, String receiptHandle) {
        return Message.builder().body(body).messageId(messageId).receiptHandle(receiptHandle).build();
    }

    @Test
    void consumoNormalAplicaOUpsertERemoveAMensagemDaFila() {
        UUID eventId = UUID.randomUUID();
        String body = envelopeValido(42L, 77, "2026-09-08T12:00:00Z", eventId);
        Message mensagem = mensagem(body, "msg-1", "receipt-1");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagem).build());

        job().consumirPendentes();

        verify(atualizarScoreReplica).atualizar(42L, 77, Instant.parse("2026-09-08T12:00:00Z"), eventId);
        verify(sqsClient).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle("receipt-1")
                .build());
    }

    @Test
    void nenhumaMensagemNaoChamaUpsertNemDelete() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        job().consumirPendentes();

        verifyNoInteractions(atualizarScoreReplica);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void falhaTransitoriaAoReceberMensagensNaoPropagaExcecao() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new RuntimeException("SQS indisponivel"));

        assertThatCode(() -> job().consumirPendentes()).doesNotThrowAnyException();

        verifyNoInteractions(atualizarScoreReplica);
    }

    @Test
    void mensagemComJsonInvalidoNaoChamaUpsertNemRemoveDaFila() {
        Message mensagem = mensagem("{ isto nao e json valido", "msg-1", "receipt-1");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagem).build());

        assertThatCode(() -> job().consumirPendentes()).doesNotThrowAnyException();

        verifyNoInteractions(atualizarScoreReplica);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void mensagemComEventTypeInesperadoNaoChamaUpsertNemRemoveDaFila() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "OutroEvento");
        envelope.put("occurredAt", "2026-09-08T12:00:00Z");
        envelope.put("payload", Map.of("pacienteId", 1L, "scoreValor", 50));
        Message mensagem = mensagem(objectMapper.writeValueAsString(envelope), "msg-1", "receipt-1");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagem).build());

        assertThatCode(() -> job().consumirPendentes()).doesNotThrowAnyException();

        verifyNoInteractions(atualizarScoreReplica);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void payloadSemPacienteIdOuScoreValorNaoChamaUpsertNemRemoveDaFila() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "ScoreCalculado");
        envelope.put("occurredAt", "2026-09-08T12:00:00Z");
        envelope.put("payload", Map.of("triagemId", 7L));
        Message mensagem = mensagem(objectMapper.writeValueAsString(envelope), "msg-1", "receipt-1");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagem).build());

        assertThatCode(() -> job().consumirPendentes()).doesNotThrowAnyException();

        verifyNoInteractions(atualizarScoreReplica);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void payloadComScoreValorNaoNumericoNaoChamaUpsertNemRemoveDaFila() {
        // Achado do code review: JsonNode#asInt() de um scoreValor
        // nao-numerico (string ou JSON null) retorna 0 em vez de lancar --
        // sem a validacao explicita de tipo em validarEnvelope, esse
        // payload malformado corromperia a replica com Score=0 em vez de
        // cair no fluxo de erro/retry/DLQ.
        Map<String, Object> payloadComStringNoLugarDeNumero = new LinkedHashMap<>();
        payloadComStringNoLugarDeNumero.put("pacienteId", 1L);
        payloadComStringNoLugarDeNumero.put("scoreValor", "nao-e-um-numero");
        Map<String, Object> envelopeComScoreValorString = new LinkedHashMap<>();
        envelopeComScoreValorString.put("eventId", UUID.randomUUID().toString());
        envelopeComScoreValorString.put("eventType", "ScoreCalculado");
        envelopeComScoreValorString.put("occurredAt", "2026-09-08T12:00:00Z");
        envelopeComScoreValorString.put("payload", payloadComStringNoLugarDeNumero);

        Map<String, Object> payloadComScoreValorNulo = new LinkedHashMap<>();
        payloadComScoreValorNulo.put("pacienteId", 1L);
        payloadComScoreValorNulo.put("scoreValor", null);
        Map<String, Object> envelopeComScoreValorNulo = new LinkedHashMap<>();
        envelopeComScoreValorNulo.put("eventId", UUID.randomUUID().toString());
        envelopeComScoreValorNulo.put("eventType", "ScoreCalculado");
        envelopeComScoreValorNulo.put("occurredAt", "2026-09-08T12:00:00Z");
        envelopeComScoreValorNulo.put("payload", payloadComScoreValorNulo);

        Message mensagemComString = mensagem(
                objectMapper.writeValueAsString(envelopeComScoreValorString), "msg-1", "receipt-1");
        Message mensagemComNulo = mensagem(
                objectMapper.writeValueAsString(envelopeComScoreValorNulo), "msg-2", "receipt-2");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagemComString, mensagemComNulo).build());

        assertThatCode(() -> job().consumirPendentes()).doesNotThrowAnyException();

        verifyNoInteractions(atualizarScoreReplica);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void falhaAoAplicarOUpsertNaoRemoveAMensagemDaFilaNemPropagaExcecao() {
        UUID eventId = UUID.randomUUID();
        String body = envelopeValido(42L, 77, "2026-09-08T12:00:00Z", eventId);
        Message mensagem = mensagem(body, "msg-1", "receipt-1");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagem).build());
        doThrow(new RuntimeException("DB indisponivel"))
                .when(atualizarScoreReplica).atualizar(anyLong(), anyInt(), any(), any());

        assertThatCode(() -> job().consumirPendentes()).doesNotThrowAnyException();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void falhaAoRemoverAMensagemJaProcessadaNaoPropagaExcecao() {
        UUID eventId = UUID.randomUUID();
        String body = envelopeValido(42L, 77, "2026-09-08T12:00:00Z", eventId);
        Message mensagem = mensagem(body, "msg-1", "receipt-1");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensagem).build());
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(new RuntimeException("SQS indisponivel no delete"));

        assertThatCode(() -> job().consumirPendentes()).doesNotThrowAnyException();

        verify(atualizarScoreReplica).atualizar(eq(42L), eq(77), any(), eq(eventId));
    }

    @Test
    void umaMensagemMalformadaNoLoteNaoBloqueiaOProcessamentoDasDemais() {
        Message malformada = mensagem("{ nao e json", "msg-1", "receipt-1");
        UUID eventIdValida = UUID.randomUUID();
        Message valida = mensagem(envelopeValido(43L, 88, "2026-09-08T12:00:00Z", eventIdValida),
                "msg-2", "receipt-2");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(malformada, valida).build());

        job().consumirPendentes();

        verify(atualizarScoreReplica, times(1)).atualizar(anyLong(), anyInt(), any(), any());
        verify(atualizarScoreReplica).atualizar(43L, 88, Instant.parse("2026-09-08T12:00:00Z"), eventIdValida);
        verify(sqsClient, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void queueUrlVazioComConsumidorHabilitadoFalhaNaConstrucaoDoJob() {
        assertThatThrownBy(() -> new ScoreCalculadoConsumerJob(sqsClient, atualizarScoreReplica, objectMapper, "", 10, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ScoreCalculadoConsumerJob(sqsClient, atualizarScoreReplica, objectMapper, "   ", 10, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ScoreCalculadoConsumerJob(sqsClient, atualizarScoreReplica, objectMapper, null, 10, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void batchSizeEClampeadoEntre1E10() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        new ScoreCalculadoConsumerJob(sqsClient, atualizarScoreReplica, objectMapper, QUEUE_URL, 0, 1)
                .consumirPendentes();
        new ScoreCalculadoConsumerJob(sqsClient, atualizarScoreReplica, objectMapper, QUEUE_URL, 999, 1)
                .consumirPendentes();

        verify(sqsClient).receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL).maxNumberOfMessages(1).waitTimeSeconds(1).build());
        verify(sqsClient).receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL).maxNumberOfMessages(10).waitTimeSeconds(1).build());
    }

    @Test
    void waitTimeSecondsNegativoEClampeadoParaZero() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        new ScoreCalculadoConsumerJob(sqsClient, atualizarScoreReplica, objectMapper, QUEUE_URL, 10, -5)
                .consumirPendentes();

        verify(sqsClient).receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL).maxNumberOfMessages(10).waitTimeSeconds(0).build());
    }
}
