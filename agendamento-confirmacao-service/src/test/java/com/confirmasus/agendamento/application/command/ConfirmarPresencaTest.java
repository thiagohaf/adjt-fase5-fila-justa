package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.EventoOutbox;
import com.confirmasus.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ConfirmarPresenca} contra a I/O &amp; Edge-Case Matrix da spec
 * 1.3 (comando de {@code POST /v1/agendamentos/{id}/confirmacao}, AD-3/AD-4):
 *
 * <ul>
 *   <li>{@link #confirmacaoValidaTransicionaEGravaConfirmacaoRegistradaNoOutboxNaMesmaExecucao()} --
 *   "Confirmacao valida": AGUARDANDO_CONFIRMACAO -&gt; CONFIRMADO, evento
 *   ConfirmacaoRegistrada gravado no outbox;</li>
 *   <li>{@link #confirmacaoDuplicadaNaoGravaNovoEventoNemLancaExcecao()} --
 *   "Confirmacao duplicada": ja CONFIRMADO -&gt; sucesso silencioso, sem novo
 *   evento;</li>
 *   <li>{@link #janelaAindaNaoAbertaLanca409()} -- "Janela ainda nao aberta":
 *   AGUARDANDO_JANELA -&gt; {@link AgendamentoForaDaJanelaException};</li>
 *   <li>{@link #vagaJaLiberadaLanca409()} -- "Vaga ja liberada": LIBERADO
 *   -&gt; {@link AgendamentoForaDaJanelaException};</li>
 *   <li>{@link #agendamentoIdInexistenteLanca404()} -- "agendamentoId
 *   inexistente": {@code buscarPorId} vazio -&gt; {@link
 *   AgendamentoNaoEncontradoException}.</li>
 * </ul>
 *
 * <p>O cenario "Corrida Confirmacao vs. Confirmacao" e coberto por {@code
 * ConfirmarPresencaConcurrencyIntegrationTest} (Postgres real, Testcontainers)
 * -- um mock de repositorio nao pode provar exclusao mutua de banco de dados.
 *
 * <p>{@link #janelaAbreEntreEscritaCondicionalEReleituraRetentaEConfirma()} --
 * achado do code review adversarial: a janela pode abrir (poller {@code
 * AbrirJanelaDeConfirmacao}) exatamente entre a escrita condicional falhar e
 * a releitura -- uma unica retentativa deve completar a confirmacao em vez de
 * lancar {@code 409} incorretamente.
 */
class ConfirmarPresencaTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    private final AgendamentoRepositorio agendamentoRepositorio = mock(AgendamentoRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private final ConfirmarPresenca confirmarPresenca =
            new ConfirmarPresenca(agendamentoRepositorio, eventoOutboxRepositorio, clock);

    private static Agendamento agendamento(long id, long pacienteId, StatusAgendamento status) {
        Instant janelaAbreEm = AGORA.minus(Duration.ofMinutes(10));
        return new Agendamento(id, pacienteId, UUID.randomUUID(), AGORA.plus(Duration.ofDays(1)),
                status, AGORA.minus(Duration.ofHours(1)), janelaAbreEm, null);
    }

    @Test
    void confirmacaoValidaTransicionaEGravaConfirmacaoRegistradaNoOutboxNaMesmaExecucao() {
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO))
                .thenReturn(true);
        when(agendamentoRepositorio.buscarPorId(1L))
                .thenReturn(Optional.of(agendamento(1L, 42L, StatusAgendamento.CONFIRMADO)));

        confirmarPresenca.confirmar(1L);

        verify(agendamentoRepositorio).atualizarStatusSeAtual(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO);

        ArgumentCaptor<EventoOutbox> captor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(captor.capture());
        EventoOutbox evento = captor.getValue();
        assertThat(evento.getEventType()).isEqualTo("ConfirmacaoRegistrada");
        assertThat(evento.getPayload()).containsEntry("agendamentoId", 1L).containsEntry("pacienteId", 42L);
        assertThat(evento.getCorrelationId()).isEqualTo("agendamento-1");
        assertThat(evento.getVersion()).isEqualTo(1);
    }

    @Test
    void confirmacaoDuplicadaNaoGravaNovoEventoNemLancaExcecao() {
        // I/O Matrix: "Confirmacao duplicada" -- escrita condicional falha
        // (ja nao esta mais em AGUARDANDO_CONFIRMACAO), releitura confirma
        // que ja e CONFIRMADO -- sucesso silencioso, nao um erro.
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                2L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(2L))
                .thenReturn(Optional.of(agendamento(2L, 43L, StatusAgendamento.CONFIRMADO)));

        assertThatCode(() -> confirmarPresenca.confirmar(2L)).doesNotThrowAnyException();

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void janelaAindaNaoAbertaLanca409() {
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                3L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(3L))
                .thenReturn(Optional.of(agendamento(3L, 44L, StatusAgendamento.AGUARDANDO_JANELA)));

        assertThatThrownBy(() -> confirmarPresenca.confirmar(3L))
                .isInstanceOf(AgendamentoForaDaJanelaException.class)
                .hasMessageContaining("janela");

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void vagaJaLiberadaLanca409() {
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                4L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(4L))
                .thenReturn(Optional.of(agendamento(4L, 45L, StatusAgendamento.LIBERADO)));

        assertThatThrownBy(() -> confirmarPresenca.confirmar(4L))
                .isInstanceOf(AgendamentoForaDaJanelaException.class)
                .hasMessageContaining("liberada");

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void agendamentoIdInexistenteLanca404() {
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                5L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO))
                .thenReturn(false);
        when(agendamentoRepositorio.buscarPorId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> confirmarPresenca.confirmar(5L))
                .isInstanceOf(AgendamentoNaoEncontradoException.class);

        verify(agendamentoRepositorio, never()).salvar(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void janelaAbreEntreEscritaCondicionalEReleituraRetentaEConfirma() {
        // Achado do code review adversarial: 1a escrita condicional falha,
        // releitura mostra AGUARDANDO_CONFIRMACAO (a janela abriu nesse
        // intervalo) -- retentativa unica deve transicionar e gravar o
        // outbox, nunca lancar AgendamentoForaDaJanelaException.
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                6L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO))
                .thenReturn(false, true);
        when(agendamentoRepositorio.buscarPorId(6L))
                .thenReturn(
                        Optional.of(agendamento(6L, 46L, StatusAgendamento.AGUARDANDO_CONFIRMACAO)),
                        Optional.of(agendamento(6L, 46L, StatusAgendamento.CONFIRMADO)));

        assertThatCode(() -> confirmarPresenca.confirmar(6L)).doesNotThrowAnyException();

        verify(agendamentoRepositorio, org.mockito.Mockito.times(2)).atualizarStatusSeAtual(
                6L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO);

        ArgumentCaptor<EventoOutbox> captor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(captor.capture());
        EventoOutbox evento = captor.getValue();
        assertThat(evento.getEventType()).isEqualTo("ConfirmacaoRegistrada");
        assertThat(evento.getPayload()).containsEntry("agendamentoId", 6L).containsEntry("pacienteId", 46L);
    }
}
