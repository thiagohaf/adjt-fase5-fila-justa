package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.EventoOutbox;
import com.filajusta.agendamento.domain.MotivoLiberacao;
import com.filajusta.agendamento.domain.StatusAgendamento;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ExpirarJanelaDeConfirmacao} contra a I/O &amp; Edge-Case Matrix
 * da spec 1.5 (poller que expira a Janela de Confirmacao, AD-3/AD-4/AD-5):
 *
 * <ul>
 *   <li>{@link #expiracaoValidaTransicionaEGravaDoiseventosNoOutbox()} --
 *   "Expiração válida": Agendamento AGUARDANDO_CONFIRMACAO com
 *   janelaExpiraEm &lt;= now() -&gt; status vira LIBERADO com motivo
 *   NAO_CONFIRMADO e dois eventos (AgendamentoNaoConfirmado +
 *   VagaLiberada) sao gravados no outbox;</li>
 *   <li>{@link #jaExpiradoPorOutraInstanciaRetornaSilenciosamente()} --
 *   "Ja expirado por outra instancia": escrita condicional afeta 0 linhas
 *   (outra instancia ja processou) -&gt; nenhum evento novo gravado, nao e
 *   um erro;</li>
 *   <li>{@link #nenhum agendamentoPendenteNaoChamaEscritaCondicional()} --
 *   "Janela ainda nao expirada": a query de leitura (janelaExpiraEm >
 *   now()) nao devolve nada -&gt; o poller nao tenta transicionar nem
 *   gravar nada;</li>
 *   <li>{@link #falhaAoLerPendentesNaoPropagaExcecao()} -- nenhuma excecao
 *   pode escapar do poller (nao pode derrubar a app);</li>
 *   <li>{@link #falhaAoGravarPrimeiroEventoNaoPropagaExcecao()} -- excecao
 *   durante salvar do primeiro evento e capturada e registrada, loop
 *   continua.</li>
 * </ul>
 *
 * <p>O cenario "Multiplas tasks ECS concorrentes" (SKIP LOCKED) e coberto por
 * {@code ExpirarJanelaDeConfirmacaoConcurrencyIntegrationTest} (Postgres real,
 * Testcontainers).
 */
class ExpirarJanelaDeConfirmacaoTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    private final AgendamentoRepositorio agendamentoRepositorio = mock(AgendamentoRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private ExpirarJanelaDeConfirmacao poller(int loteTamanho) {
        return new ExpirarJanelaDeConfirmacao(agendamentoRepositorio, eventoOutboxRepositorio, clock, loteTamanho);
    }

    private static Agendamento agendamento(long id, long pacienteId, UUID recursoId) {
        Instant janelaExpiraEm = AGORA.minus(java.time.Duration.ofMinutes(1));
        Instant dataHoraAgendamento = AGORA.plus(java.time.Duration.ofDays(1));
        return new Agendamento(id, pacienteId, recursoId, dataHoraAgendamento,
                StatusAgendamento.AGUARDANDO_CONFIRMACAO, AGORA.minus(java.time.Duration.ofHours(1)),
                AGORA.minus(java.time.Duration.ofHours(2)), janelaExpiraEm);
    }

    @Test
    void expiracaoValidaTransicionaEGravaDoiseventosNoOutbox() {
        UUID recursoId = UUID.randomUUID();
        Agendamento pendente = agendamento(1L, 42L, recursoId);
        when(agendamentoRepositorio.buscarPendentesExpiracaoJanela(50)).thenReturn(List.of(pendente));
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.NAO_CONFIRMADO.name()))
                .thenReturn(true);

        poller(50).expirarJanelas();

        verify(agendamentoRepositorio).atualizarStatusComMotivo(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.NAO_CONFIRMADO.name());

        ArgumentCaptor<EventoOutbox> captor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio, times(2)).salvar(captor.capture());
        List<EventoOutbox> eventos = captor.getAllValues();

        // Primeiro evento: AgendamentoNaoConfirmado
        EventoOutbox eventoNaoConfirmado = eventos.get(0);
        assertThat(eventoNaoConfirmado.getEventType()).isEqualTo("AgendamentoNaoConfirmado");
        assertThat(eventoNaoConfirmado.getPayload())
                .containsEntry("agendamentoId", 1L)
                .containsEntry("pacienteId", 42L)
                .containsEntry("motivo", MotivoLiberacao.NAO_CONFIRMADO.name());
        assertThat(eventoNaoConfirmado.getCorrelationId()).isEqualTo("agendamento-1");
        assertThat(eventoNaoConfirmado.getVersion()).isEqualTo(1);

        // Segundo evento: VagaLiberada
        EventoOutbox eventoVaga = eventos.get(1);
        assertThat(eventoVaga.getEventType()).isEqualTo("VagaLiberada");
        assertThat(eventoVaga.getPayload())
                .containsEntry("agendamentoId", 1L)
                .containsEntry("recursoId", recursoId)
                .containsEntry("dataHoraAgendamento", pendente.getDataHoraAgendamento());
        assertThat(eventoVaga.getCorrelationId()).isEqualTo("agendamento-1");
    }

    @Test
    void jaExpiradoPorOutraInstanciaRetornaSilenciosamente() {
        // I/O Matrix: "Ja expirado por outra instancia" -- a escrita
        // condicional (UPDATE...WHERE status=AGUARDANDO_CONFIRMACAO)
        // afeta 0 linhas -- nao e um erro, so idempotencia por design.
        UUID recursoId = UUID.randomUUID();
        Agendamento jaProcessado = agendamento(2L, 43L, recursoId);
        when(agendamentoRepositorio.buscarPendentesExpiracaoJanela(50)).thenReturn(List.of(jaProcessado));
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                eq(2L), eq(StatusAgendamento.AGUARDANDO_CONFIRMACAO), any(), any()))
                .thenReturn(false);

        assertThatCode(() -> poller(50).expirarJanelas()).doesNotThrowAnyException();

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void nenhumAgendamentoPendenteNaoChamaEscritaCondicional() {
        // I/O Matrix: "Janela ainda nao expirada" -- a query de leitura
        // (janelaExpiraEm > now()) nao devolve o Agendamento; o poller nao
        // tem nada para processar.
        when(agendamentoRepositorio.buscarPendentesExpiracaoJanela(50)).thenReturn(List.of());

        poller(50).expirarJanelas();

        verify(agendamentoRepositorio, never()).atualizarStatusComMotivo(any(), any(), any(), any());
        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void falhaAoLerPendentesNaoPropagaExcecao() {
        when(agendamentoRepositorio.buscarPendentesExpiracaoJanela(50))
                .thenThrow(new RuntimeException("Postgres indisponivel"));

        assertThatCode(() -> poller(50).expirarJanelas()).doesNotThrowAnyException();

        verifyNoInteractions(eventoOutboxRepositorio);
    }

    @Test
    void falhaAoGravarPrimeiroEventoNaoPropagaExcecao() {
        UUID recursoId = UUID.randomUUID();
        Agendamento pendente1 = agendamento(1L, 42L, recursoId);
        Agendamento pendente2 = agendamento(2L, 43L, UUID.randomUUID());
        when(agendamentoRepositorio.buscarPendentesExpiracaoJanela(50))
                .thenReturn(List.of(pendente1, pendente2));
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.NAO_CONFIRMADO.name()))
                .thenReturn(true);
        when(agendamentoRepositorio.atualizarStatusComMotivo(
                2L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.NAO_CONFIRMADO.name()))
                .thenReturn(true);

        // Primeira chamada salvar falha, segunda sucede
        doThrow(new RuntimeException("Erro ao salvar evento"))
                .doNothing()
                .when(eventoOutboxRepositorio).salvar(any());

        assertThatCode(() -> poller(50).expirarJanelas()).doesNotThrowAnyException();

        // Verifica que ambos os Agendamentos foram processados (a excecao
        // e isolada por item do lote, nao propaga)
        verify(agendamentoRepositorio).atualizarStatusComMotivo(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.NAO_CONFIRMADO.name());
        verify(agendamentoRepositorio).atualizarStatusComMotivo(
                2L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                MotivoLiberacao.NAO_CONFIRMADO.name());
    }

    @Test
    void loteTamanhoMenorOuIgualAZeroEClampeadoParaUm() {
        when(agendamentoRepositorio.buscarPendentesExpiracaoJanela(1)).thenReturn(List.of());

        poller(0).expirarJanelas();

        verify(agendamentoRepositorio).buscarPendentesExpiracaoJanela(1);
    }
}
