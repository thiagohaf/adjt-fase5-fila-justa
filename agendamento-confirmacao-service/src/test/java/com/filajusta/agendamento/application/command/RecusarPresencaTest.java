package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.EventoOutbox;
import com.filajusta.agendamento.domain.MotivoLiberacao;
import com.filajusta.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link RecusarPresenca} contra a I/O &amp; Edge-Case Matrix da spec
 * 1.4 (comando de {@code POST /v1/agendamentos/{id}/recusa}, AD-3/AD-4):
 *
 * <ul>
 *   <li>{@link #recusaValidaTransicionaEGraVaDoisEventosNoOutboxNaMesmaExecucao()} --
 *   "Recusa valida": AGUARDANDO_CONFIRMACAO -> LIBERADO/RECUSA, eventos
 *   RecusaRegistrada + VagaLiberada gravados no outbox;</li>
 *   <li>{@link #recusaDuplicadaNaoGravaNovoEventoNemLancaExcecao()} --
 *   "Recusa duplicada": ja LIBERADO/RECUSA -> sucesso silencioso, sem novo
 *   evento;</li>
 *   <li>{@link #janelaAindaNaoAbertaLanca409()} -- "Janela ainda nao aberta":
 *   AGUARDANDO_JANELA -> {@link AgendamentoForaDaJanelaException};</li>
 *   <li>{@link #agendamentoConfirmadoLanca409()} -- "Confirmado":
 *   CONFIRMADO -> {@link AgendamentoForaDaJanelaException};</li>
 *   <li>{@link #vagaJaLiberadaPorOutroMotivoLanca409()} -- "Liberado (outro motivo)":
 *   LIBERADO/NAO_CONFIRMADO -> {@link AgendamentoForaDaJanelaException};</li>
 *   <li>{@link #agendamentoIdInexistenteLanca404()} -- "agendamentoId
 *   inexistente": {@code buscarPorId} vazio -> {@link
 *   AgendamentoNaoEncontradoException}.</li>
 * </ul>
 *
 * <p>O cenario "Corrida Recusa vs. Recusa" e coberto por {@code
 * RecusarPresencaConcurrencyIntegrationTest} (Postgres real, Testcontainers).
 *
 * <p>{@link #janelaAbreEntreEscritaCondicionalEReleituraRetentaERecusa()} --
 * achado do code review adversarial: a janela pode abrir (poller {@code
 * AbrirJanelaDeConfirmacao}) exatamente entre a escrita condicional falhar e
 * a releitura -- uma unica retentativa deve completar a recusa em vez de
 * lancar {@code 409} incorretamente.
 */
class RecusarPresencaTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    private final AgendamentoRepositorio agendamentoRepositorio = mock(AgendamentoRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private final RecusarPresenca recusarPresenca =
            new RecusarPresenca(agendamentoRepositorio, eventoOutboxRepositorio, clock);

    private static Agendamento agendamento(long id, long pacienteId, StatusAgendamento status) {
        return agendamento(id, pacienteId, status, null);
    }

    private static Agendamento agendamento(long id, long pacienteId, StatusAgendamento status, String motivoLiberacao) {
        Instant janelaAbreEm = AGORA.minus(Duration.ofMinutes(10));
        return new Agendamento(id, pacienteId, UUID.randomUUID(), AGORA.plus(Duration.ofDays(1)),
                status, AGORA.minus(Duration.ofHours(1)), janelaAbreEm, null, motivoLiberacao);
    }

    @Test
    void recusaValidaTransicionaEGraVaDoisEventosNoOutboxNaMesmaExecucao() {
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name()))
                .thenReturn(true);
        UUID recursoId = UUID.randomUUID();
        when(agendamentoRepositorio.buscarPorId(1L))
                .thenReturn(Optional.of(
                        new Agendamento(1L, 42L, recursoId, AGORA.plus(Duration.ofDays(1)),
                                StatusAgendamento.LIBERADO, AGORA.minus(Duration.ofHours(1)),
                                AGORA.minus(Duration.ofMinutes(10)), null, MotivoLiberacao.RECUSA.name())));

        recusarPresenca.recusar(1L);

        verify(agendamentoRepositorio).atualizarStatusComMotivo(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name());

        ArgumentCaptor<EventoOutbox> captor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio, times(2)).salvar(captor.capture());
        List<EventoOutbox> eventos = captor.getAllValues();

        assertThat(eventos).hasSize(2);
        EventoOutbox recusaEvento = eventos.get(0);
        assertThat(recusaEvento.getEventType()).isEqualTo("RecusaRegistrada");
        assertThat(recusaEvento.getPayload())
                .containsEntry("agendamentoId", 1L)
                .containsEntry("pacienteId", 42L)
                .containsEntry("motivo", MotivoLiberacao.RECUSA.name());
        assertThat(recusaEvento.getCorrelationId()).isEqualTo("agendamento-1");
        assertThat(recusaEvento.getVersion()).isEqualTo(1);

        EventoOutbox vagaEvento = eventos.get(1);
        assertThat(vagaEvento.getEventType()).isEqualTo("VagaLiberada");
        assertThat(vagaEvento.getPayload())
                .containsEntry("agendamentoId", 1L)
                .containsEntry("recursoId", recursoId);
        assertThat(vagaEvento.getCorrelationId()).isEqualTo("agendamento-1");
        assertThat(vagaEvento.getVersion()).isEqualTo(1);
    }

    @Test
    void recusaDuplicadaNaoGravaNovoEventoNemLancaExcecao() {
        // I/O Matrix: "Recusa duplicada" -- escrita condicional falha
        // (ja nao esta mais em AGUARDANDO_CONFIRMACAO), releitura confirma
        // que ja e LIBERADO/RECUSA -- sucesso silencioso, nao um erro.
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                2L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name()))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(2L))
                .thenReturn(Optional.of(agendamento(2L, 43L, StatusAgendamento.LIBERADO, MotivoLiberacao.RECUSA.name())));

        assertThatCode(() -> recusarPresenca.recusar(2L)).doesNotThrowAnyException();

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void janelaAindaNaoAbertaLanca409() {
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                3L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name()))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(3L))
                .thenReturn(Optional.of(agendamento(3L, 44L, StatusAgendamento.AGUARDANDO_JANELA)));

        assertThatThrownBy(() -> recusarPresenca.recusar(3L))
                .isInstanceOf(AgendamentoForaDaJanelaException.class)
                .hasMessageContaining("janela");

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void agendamentoConfirmadoLanca409() {
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                4L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name()))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(4L))
                .thenReturn(Optional.of(agendamento(4L, 45L, StatusAgendamento.CONFIRMADO)));

        assertThatThrownBy(() -> recusarPresenca.recusar(4L))
                .isInstanceOf(AgendamentoForaDaJanelaException.class)
                .hasMessageContaining("nao esta aguardando confirmacao");

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void vagaJaLiberadaPorOutroMotivoLanca409() {
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                5L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name()))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(5L))
                .thenReturn(Optional.of(agendamento(5L, 45L, StatusAgendamento.LIBERADO, MotivoLiberacao.NAO_CONFIRMADO.name())));

        assertThatThrownBy(() -> recusarPresenca.recusar(5L))
                .isInstanceOf(AgendamentoForaDaJanelaException.class)
                .hasMessageContaining("liberada");

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void agendamentoIdInexistenteLanca404() {
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                6L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name()))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recusarPresenca.recusar(6L))
                .isInstanceOf(AgendamentoNaoEncontradoException.class);

        verify(agendamentoRepositorio, never()).salvar(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void janelaAbreEntreEscritaCondicionalEReleituraRetentaERecusa() {
        // Achado do code review adversarial: 1a escrita condicional falha,
        // releitura mostra AGUARDANDO_CONFIRMACAO (a janela abriu nesse
        // intervalo) -- retentativa unica deve transicionar e gravar os
        // eventos, nunca lancar AgendamentoForaDaJanelaException.
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                7L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name()))
                .thenReturn(false, true);
        UUID recursoId = UUID.randomUUID();
        when(agendamentoRepositorio.buscarPorId(7L))
                .thenReturn(
                        Optional.of(agendamento(7L, 47L, StatusAgendamento.AGUARDANDO_CONFIRMACAO)),
                        Optional.of(new Agendamento(7L, 47L, recursoId, AGORA.plus(Duration.ofDays(1)),
                                StatusAgendamento.LIBERADO, AGORA.minus(Duration.ofHours(1)),
                                AGORA.minus(Duration.ofMinutes(10)), null, MotivoLiberacao.RECUSA.name())));

        assertThatCode(() -> recusarPresenca.recusar(7L)).doesNotThrowAnyException();

        verify(agendamentoRepositorio, times(2)).atualizarStatusComMotivo(
                7L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.RECUSA.name());

        ArgumentCaptor<EventoOutbox> captor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio, times(2)).salvar(captor.capture());
        List<EventoOutbox> eventos = captor.getAllValues();
        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0).getEventType()).isEqualTo("RecusaRegistrada");
        assertThat(eventos.get(1).getEventType()).isEqualTo("VagaLiberada");
    }

    @Test
    void recusarComIdNullLancaNullPointerException() {
        assertThatThrownBy(() -> recusarPresenca.recusar(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");

        verifyNoInteractions(agendamentoRepositorio);
        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void recusarComIdZeroOuNegativoLancaIllegalArgumentException() {
        assertThatThrownBy(() -> recusarPresenca.recusar(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");

        assertThatThrownBy(() -> recusarPresenca.recusar(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");

        verifyNoInteractions(agendamentoRepositorio);
        verifyNoInteractions(eventoOutboxRepositorio);
    }
}
