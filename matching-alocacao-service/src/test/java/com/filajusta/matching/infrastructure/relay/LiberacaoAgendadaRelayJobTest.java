package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.LiberacaoAgendadaRepositorio;
import com.filajusta.matching.domain.LiberacaoAgendada;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link LiberacaoAgendadaRelayJob} com {@link LiberacaoAgendadaRepositorio}
 * e {@link SqsClient} mockados -- molde de {@link RelaySnsPublisherJobTest}
 * (Story 3-3a), adaptado para fila SQS standard (sem MessageGroupId/ordem
 * FIFO entre Recursos, Boundaries da spec 3-4a2).
 *
 * <p>Cobre HAPPY_PATH, SEM_PENDENTES e FALHA_ENVIO_1_ITEM da I/O &amp;
 * Edge-Case Matrix da spec 3-4a2 (unitario, com mocks); CONCORRENCIA_2_INSTANCIAS
 * e coberto por {@link LiberacaoAgendadaRelayJobConcurrencyIntegrationTest}
 * contra Postgres real, e a chegada real na fila SQS (LocalStack) por
 * {@link LiberacaoAgendadaRelayJobIntegrationTest}.
 */
class LiberacaoAgendadaRelayJobTest {

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/liberacao-agendada-test";

    private final LiberacaoAgendadaRepositorio repositorio = mock(LiberacaoAgendadaRepositorio.class);
    private final SqsClient sqsClient = mock(SqsClient.class);
    private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    private LiberacaoAgendadaRelayJob job(int loteTamanho) {
        return new LiberacaoAgendadaRelayJob(repositorio, sqsClient, objectMapper, QUEUE_URL, loteTamanho);
    }

    private static LiberacaoAgendada liberacao(UUID alocacaoId, int delaySegundos) {
        return new LiberacaoAgendada(alocacaoId, UUID.randomUUID(), "corr-" + alocacaoId, delaySegundos,
                Instant.parse("2026-09-13T12:00:00Z"), null);
    }

    @Test
    void umaLiberacaoPendenteEEnviadaComDelaySecondsCorretoEMarcadaComoEnviada() {
        // HAPPY_PATH da I/O Matrix: liberacao pendente com delaySegundos=N ->
        // fila recebe 1 mensagem com DelaySeconds=N e enviado_em e gravado.
        UUID alocacaoId = UUID.randomUUID();
        LiberacaoAgendada liberacao = liberacao(alocacaoId, 120);
        when(repositorio.buscarPendentes(50)).thenReturn(List.of(liberacao));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().build());
        when(repositorio.marcarComoEnviado(alocacaoId)).thenReturn(true);

        job(50).publicarPendentes();

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        assertThat(captor.getValue().queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(captor.getValue().delaySeconds()).isEqualTo(120);
        assertThat(captor.getValue().messageBody())
                .contains(liberacao.getAlocacaoId().toString())
                .contains(liberacao.getRecursoId().toString())
                .contains(liberacao.getCorrelationId());
        verify(repositorio).marcarComoEnviado(alocacaoId);
    }

    @Test
    void semPendentesNaoChamaSqsNemMarcaNada() {
        // SEM_PENDENTES da I/O Matrix: job nao faz nada, sem erro.
        when(repositorio.buscarPendentes(50)).thenReturn(List.of());

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verifyNoInteractions(sqsClient);
        verify(repositorio, never()).marcarComoEnviado(any(UUID.class));
    }

    @Test
    void falhaNoEnvioDoPrimeiroItemNaoImpedeOSegundoItemDoLote() {
        // FALHA_ENVIO_1_ITEM da I/O Matrix: lote com 2 pendentes, sendMessage
        // falha para o 1o -- 2o item ainda e processado nesta execucao;
        // enviado_em do 1o continua NULL (retry na proxima execucao) porque
        // marcarComoEnviado nunca e chamado para ele.
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(repositorio.buscarPendentes(50))
                .thenReturn(List.of(liberacao(primeiro, 60), liberacao(segundo, 90)));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(new RuntimeException("SQS indisponivel"))
                .thenReturn(SendMessageResponse.builder().build());
        when(repositorio.marcarComoEnviado(segundo)).thenReturn(true);

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(sqsClient, times(2)).sendMessage(any(SendMessageRequest.class));
        verify(repositorio, never()).marcarComoEnviado(primeiro);
        verify(repositorio).marcarComoEnviado(segundo);
    }

    @Test
    void queueUrlVazioComRelayHabilitadoFalhaNaConstrucaoDoJob() {
        // Fail-fast (mesmo raciocinio de RelaySnsPublisherJob/
        // ScoreCalculadoConsumerJob): sem isso, o relay habilitado com
        // queue-url vazio rodaria para sempre falhando em silencio a cada
        // ciclo, em vez de derrubar a subida do servico.
        assertThatThrownBy(() -> new LiberacaoAgendadaRelayJob(repositorio, sqsClient, objectMapper, "", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LiberacaoAgendadaRelayJob(repositorio, sqsClient, objectMapper, "   ", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LiberacaoAgendadaRelayJob(repositorio, sqsClient, objectMapper, null, 50))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void batchSizeMenorOuIgualAZeroEClampeadoParaUm() {
        // batch-size <= 0 quebraria a query de leitura (LIMIT invalido) a
        // cada execucao -- pisado em 1 em vez de propagar o valor invalido.
        when(repositorio.buscarPendentes(1)).thenReturn(List.of());

        new LiberacaoAgendadaRelayJob(repositorio, sqsClient, objectMapper, QUEUE_URL, 0).publicarPendentes();

        verify(repositorio).buscarPendentes(1);
    }

    @Test
    void falhaAoMarcarComoEnviadoAposOAckDoSqsNaoPropagaExcecao() {
        // Mensagem ja publicada com sucesso -- uma falha so ao marcar
        // enviado_em nao pode derrubar o job nem o lote (at-least-once,
        // reenvio na proxima execucao e aceitavel).
        UUID alocacaoId = UUID.randomUUID();
        when(repositorio.buscarPendentes(50)).thenReturn(List.of(liberacao(alocacaoId, 60)));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().build());
        when(repositorio.marcarComoEnviado(alocacaoId)).thenThrow(new RuntimeException("DB indisponivel"));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }
}
