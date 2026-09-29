package com.confirmasus.matching.infrastructure.relay;

import com.confirmasus.matching.application.command.GerarSugestaoRepasse;
import com.confirmasus.matching.application.query.RecursoNaoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VagaLiberadaSqsConsumerJobTest {

    private static final UUID RECURSO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ENVELOPE = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"VagaLiberada\","
            + "\"occurredAt\":\"2026-09-29T10:00:00Z\",\"version\":1,\"correlationId\":\"agendamento-42\","
            + "\"payload\":{\"agendamentoId\":42,\"recursoId\":\"" + RECURSO + "\","
            + "\"dataHoraAgendamento\":\"2026-10-01T10:00:00Z\"}}";

    private SqsClient sqs;
    private GerarSugestaoRepasse gerar;
    private VagaLiberadaSqsConsumerJob job;

    @BeforeEach
    void setUp() {
        sqs = Mockito.mock(SqsClient.class);
        gerar = Mockito.mock(GerarSugestaoRepasse.class);
        job = new VagaLiberadaSqsConsumerJob(sqs, gerar, new ObjectMapper(), "http://q/vaga.fifo", 10, 1);
    }

    private void receber(String body) {
        Message m = Message.builder().messageId("m1").receiptHandle("rh").body(body).build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(m).build());
    }

    @Test
    void envelopeRawGeraSugestaoEApagaMensagem() {
        when(gerar.gerar(42L, RECURSO, "agendamento-42")).thenReturn(GerarSugestaoRepasse.Resultado.GERADA);
        receber(ENVELOPE);

        job.consumirPendentes();

        verify(gerar).gerar(42L, RECURSO, "agendamento-42");
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void envelopeSnsEDesembrulhado() throws Exception {
        String sns = new ObjectMapper().writeValueAsString(Map.of("Type", "Notification", "Message", ENVELOPE));
        when(gerar.gerar(eq(42L), eq(RECURSO), any())).thenReturn(GerarSugestaoRepasse.Resultado.DUPLICADA);
        receber(sns);

        job.consumirPendentes();

        verify(gerar).gerar(42L, RECURSO, "agendamento-42");
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void mensagemMalformadaNaoApaga() {
        receber("{nao e json");

        job.consumirPendentes();

        verify(gerar, never()).gerar(anyLong(), any(), any());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void falhaDeProcessamentoNaoApaga() {
        when(gerar.gerar(eq(42L), eq(RECURSO), any())).thenThrow(new RecursoNaoEncontradoException(RECURSO));
        receber(ENVELOPE);

        job.consumirPendentes();

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void outroEventTypeEApagadoSemEfeito() {
        receber(ENVELOPE.replace("VagaLiberada", "RecusaRegistrada"));

        job.consumirPendentes();

        verify(gerar, never()).gerar(anyLong(), any(), any());
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }
}
