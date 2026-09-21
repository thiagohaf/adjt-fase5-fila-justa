package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.EventoOutbox;
import com.confirmasus.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link AbrirJanelaDeConfirmacao} contra a I/O &amp; Edge-Case Matrix
 * da spec 1.2 (poller que abre a Janela de Confirmacao, AD-3/AD-4):
 *
 * <ul>
 *   <li>{@link #janelaProntaParaAbrirTransicionaEGravaNotificacaoNoOutboxNaMesmaExecucao()} --
 *   "Janela pronta para abrir": Agendamento AGUARDANDO_JANELA com
 *   janelaAbreEm &lt;= now() -&gt; status vira AGUARDANDO_CONFIRMACAO e
 *   NotificacaoConfirmacaoPublicada e gravado no outbox;</li>
 *   <li>{@link #reprocessamentoDoMesmoAgendamentoNaoGravaSegundaNotificacao()} --
 *   "Reprocessamento do mesmo Agendamento": escrita condicional afeta 0
 *   linhas (outra instancia ja processou) -&gt; nenhum evento novo gravado,
 *   nao e um erro;</li>
 *   <li>{@link #nenhumAgendamentoPendenteNaoChamaEscritaCondicionalNemOutbox()} --
 *   "Ainda nao chegou a hora": a query de leitura (responsabilidade do
 *   repositorio, {@code janelaAbreEm > now()}) nao devolve nada -&gt; o
 *   poller nao tenta transicionar nem gravar nada;</li>
 *   <li>{@link #falhaAoLerPendentesNaoPropagaExcecao()} -- nenhuma excecao
 *   pode escapar do poller (nao pode derrubar a app).</li>
 * </ul>
 *
 * <p>O cenario "Multiplas tasks ECS concorrentes" (SKIP LOCKED) e coberto por
 * {@code AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest} (Postgres real,
 * Testcontainers) -- um mock de repositorio nao pode provar exclusao mutua
 * de banco de dados.
 */
class AbrirJanelaDeConfirmacaoTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    private final AgendamentoRepositorio agendamentoRepositorio = mock(AgendamentoRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private AbrirJanelaDeConfirmacao poller(int loteTamanho) {
        return new AbrirJanelaDeConfirmacao(agendamentoRepositorio, eventoOutboxRepositorio, clock, loteTamanho);
    }

    private static Agendamento agendamento(long id, long pacienteId) {
        Instant janelaAbreEm = AGORA.minus(java.time.Duration.ofMinutes(1));
        return new Agendamento(id, pacienteId, UUID.randomUUID(), AGORA.plus(java.time.Duration.ofDays(1)),
                StatusAgendamento.AGUARDANDO_JANELA, AGORA.minus(java.time.Duration.ofHours(1)), janelaAbreEm, null);
    }

    @Test
    void janelaProntaParaAbrirTransicionaEGravaNotificacaoNoOutboxNaMesmaExecucao() {
        Agendamento pendente = agendamento(1L, 42L);
        when(agendamentoRepositorio.buscarPendentesAberturaJanela(50)).thenReturn(List.of(pendente));
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                1L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);

        poller(50).abrirJanelas();

        verify(agendamentoRepositorio).atualizarStatusSeAtual(
                1L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO);

        ArgumentCaptor<EventoOutbox> captor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(captor.capture());
        EventoOutbox evento = captor.getValue();
        assertThat(evento.getEventType()).isEqualTo("NotificacaoConfirmacaoPublicada");
        assertThat(evento.getPayload()).containsEntry("agendamentoId", 1L).containsEntry("pacienteId", 42L);
        assertThat(evento.getCorrelationId()).isEqualTo("agendamento-1");
        assertThat(evento.getVersion()).isEqualTo(1);
    }

    @Test
    void reprocessamentoDoMesmoAgendamentoNaoGravaSegundaNotificacao() {
        // I/O Matrix: "poller roda de novo sobre Agendamento ja
        // AGUARDANDO_CONFIRMACAO" -- a escrita condicional (simulada aqui
        // pelo repositorio real via UPDATE...WHERE status=...) afeta 0
        // linhas -- nao e um erro, so idempotencia por design.
        Agendamento jaProcessado = agendamento(2L, 43L);
        when(agendamentoRepositorio.buscarPendentesAberturaJanela(50)).thenReturn(List.of(jaProcessado));
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                eq(2L), eq(StatusAgendamento.AGUARDANDO_JANELA), any()))
                .thenReturn(false);

        assertThatCode(() -> poller(50).abrirJanelas()).doesNotThrowAnyException();

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void nenhumAgendamentoPendenteNaoChamaEscritaCondicionalNemOutbox() {
        // I/O Matrix: "Ainda nao chegou a hora" -- a query de leitura
        // (janelaAbreEm > now()) nao devolve o Agendamento; o poller nao tem
        // nada para processar.
        when(agendamentoRepositorio.buscarPendentesAberturaJanela(50)).thenReturn(List.of());

        poller(50).abrirJanelas();

        verify(agendamentoRepositorio, never()).atualizarStatusSeAtual(any(), any(), any());
        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void falhaAoLerPendentesNaoPropagaExcecao() {
        when(agendamentoRepositorio.buscarPendentesAberturaJanela(50))
                .thenThrow(new RuntimeException("Postgres indisponivel"));

        assertThatCode(() -> poller(50).abrirJanelas()).doesNotThrowAnyException();

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void loteTamanhoMenorOuIgualAZeroEClampeadoParaUm() {
        when(agendamentoRepositorio.buscarPendentesAberturaJanela(1)).thenReturn(List.of());

        poller(0).abrirJanelas();

        verify(agendamentoRepositorio).buscarPendentesAberturaJanela(1);
    }
}
