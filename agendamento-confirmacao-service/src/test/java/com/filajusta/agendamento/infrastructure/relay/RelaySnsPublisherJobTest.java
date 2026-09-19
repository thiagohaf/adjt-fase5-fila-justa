package com.filajusta.agendamento.infrastructure.relay;

import com.filajusta.agendamento.application.command.EventoOutboxRepositorio;
import com.filajusta.agendamento.domain.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link RelaySnsPublisherJob} com {@link SnsClient} mockado --
 * adaptado de {@code matching-alocacao-service/.../relay/
 * RelaySnsPublisherJobTest.java} para {@code agendamentoId}/
 * {@code MessageGroupId} deste servico (spec 1.2, AD-3).
 *
 * <p>{@link #falhaTransitoriaDoSnsNaoPropagaExcecaoENaoMarcaALinhaComoPublicada()}
 * cobre a linha "Relay indisponivel (SNS fora do ar)" da I/O &amp; Edge-Case
 * Matrix da spec 1.2: quando {@code snsClient.publish(...)} lanca,
 * {@code repositorio.marcarComoPublicado(...)} nunca e chamado -- a linha
 * permanece com {@code publicado_em IS NULL} e {@code eventId} inalterado
 * (lido do banco, nunca gerado no relay), entao uma nova execucao do job
 * reencontraria a MESMA linha com o MESMO eventId.
 */
class RelaySnsPublisherJobTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:agendamento-confirmacao.fifo";

    private final EventoOutboxRepositorio repositorio = mock(EventoOutboxRepositorio.class);
    private final SnsClient snsClient = mock(SnsClient.class);
    private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    private RelaySnsPublisherJob job(int loteTamanho) {
        return new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, TOPIC_ARN, loteTamanho);
    }

    private static EventoOutbox evento(long id, long agendamentoId) {
        return new EventoOutbox(id, UUID.randomUUID(), "NotificacaoConfirmacaoPublicada",
                Instant.parse("2026-09-18T12:00:00Z"), 1, "agendamento-" + agendamentoId,
                Map.of("agendamentoId", agendamentoId, "pacienteId", 7L));
    }

    @Test
    void falhaTransitoriaDoSnsNaoPropagaExcecaoENaoMarcaALinhaComoPublicada() {
        EventoOutbox evento = evento(1L, 42L);
        UUID eventIdOriginal = evento.getEventId();
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(evento));
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("SNS indisponivel"));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(repositorio, never()).marcarComoPublicado(anyLong());

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        assertThat(captor.getValue().messageDeduplicationId()).isEqualTo(eventIdOriginal.toString());
        assertThat(captor.getValue().messageGroupId()).isEqualTo("42");
    }

    @Test
    void nenhumaLinhaPendenteNaoChamaSnsNemMarcaNada() {
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of());

        job(50).publicarPendentes();

        verifyNoInteractions(snsClient);
        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void topicArnVazioComRelayHabilitadoFalhaNaConstrucaoDoJob() {
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, "", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, "   ", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, null, 50))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void falhaNaSegundaLinhaInterrompeOLotePreservandoOrdemFifoDoAgendamento() {
        EventoOutbox primeiro = evento(1L, 42L);
        EventoOutbox segundo = evento(2L, 42L);
        EventoOutbox terceiro = evento(3L, 42L);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(primeiro, segundo, terceiro));
        when(repositorio.marcarComoPublicado(1L)).thenReturn(true);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().build())
                .thenThrow(new RuntimeException("SNS indisponivel"));

        job(50).publicarPendentes();

        verify(snsClient, times(2)).publish(any(PublishRequest.class));
        verify(repositorio).marcarComoPublicado(1L);
        verify(repositorio, never()).marcarComoPublicado(2L);
        verify(repositorio, never()).marcarComoPublicado(3L);
    }

    @Test
    void falhaDeUmAgendamentoNaoBloqueiaAPublicacaoDeOutrosAgendamentosNoMesmoLote() {
        EventoOutbox agendamentoAPrimeiro = evento(1L, 42L);
        EventoOutbox agendamentoASegundo = evento(2L, 42L);
        EventoOutbox agendamentoB = evento(3L, 43L);
        when(repositorio.buscarNaoPublicados(50))
                .thenReturn(List.of(agendamentoAPrimeiro, agendamentoASegundo, agendamentoB));
        when(repositorio.marcarComoPublicado(3L)).thenReturn(true);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenThrow(new RuntimeException("SNS indisponivel para o agendamento 42"))
                .thenReturn(PublishResponse.builder().build());

        job(50).publicarPendentes();

        verify(repositorio, never()).marcarComoPublicado(1L);
        verify(repositorio, never()).marcarComoPublicado(2L);
        verify(repositorio).marcarComoPublicado(3L);
        verify(snsClient, times(2)).publish(any(PublishRequest.class));
    }

    @Test
    void messageGroupIdAusenteNoPayloadFalhaAPublicacaoSemChamarSnsNemMarcar() {
        EventoOutbox semAgendamentoId = new EventoOutbox(1L, UUID.randomUUID(), "NotificacaoConfirmacaoPublicada",
                Instant.parse("2026-09-18T12:00:00Z"), 1, "corr-1", Map.of("pacienteId", 7L));
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(semAgendamentoId));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void batchSizeMenorOuIgualAZeroEClampeadoParaUm() {
        when(repositorio.buscarNaoPublicados(1)).thenReturn(List.of());

        new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, TOPIC_ARN, 0).publicarPendentes();

        verify(repositorio).buscarNaoPublicados(1);
    }
}
