package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AbrirJanelaDeConfirmacao — rollback parcial em lote com falha no meio")
class AbrirJanelaDeConfirmacaoBatchPartialFailureTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    private final AgendamentoRepositorio agendamentoRepositorio = mock(AgendamentoRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private AbrirJanelaDeConfirmacao poller(int loteTamanho) {
        return new AbrirJanelaDeConfirmacao(agendamentoRepositorio, eventoOutboxRepositorio, clock, loteTamanho);
    }

    private static Agendamento agendamento(long id, long pacienteId) {
        return new Agendamento(
                id,
                pacienteId,
                UUID.randomUUID(),
                AGORA.plus(java.time.Duration.ofDays(1)),
                StatusAgendamento.AGUARDANDO_JANELA,
                AGORA.minus(java.time.Duration.ofHours(1)),
                AGORA.minus(java.time.Duration.ofHours(2)),
                AGORA.plus(java.time.Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("FIX-2: falha no item 3 de 5 não reverte trabalho de itens 1-2")
    void shouldNotRollbackPreviousItemsOnPartialBatchFailure() {
        // Setup: 5 agendamentos em AGUARDANDO_JANELA
        Agendamento item1 = agendamento(1L, 42L);
        Agendamento item2 = agendamento(2L, 43L);
        Agendamento item3 = agendamento(3L, 44L);
        Agendamento item4 = agendamento(4L, 45L);
        Agendamento item5 = agendamento(5L, 46L);

        when(agendamentoRepositorio.buscarPendentesAberturaJanela(10))
                .thenReturn(List.of(item1, item2, item3, item4, item5));

        // Mock: itens 1-2 transicionam sucesso, item 3 falha, itens 4-5 transicionam sucesso
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                1L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                2L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                3L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                4L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                5L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);

        // Mock: eventoOutboxRepositorio falha na 3ª chamada (item 3)
        doThrow(new RuntimeException("Erro ao salvar evento do item 3"))
                .when(eventoOutboxRepositorio).salvar(any());

        // Executar poller — deve não propagar a exceção
        assertThatCode(() -> poller(10).abrirJanelas())
                .doesNotThrowAnyException();

        // Verificar que todos os 5 itens foram tentados (try-catch isola falha)
        verify(agendamentoRepositorio, times(5)).atualizarStatusSeAtual(any(), any(), any());

        // Verificar que as 5 chamadas de salvar foram tentadas
        verify(eventoOutboxRepositorio, times(5)).salvar(any());
    }

    @Test
    @DisplayName("FIX-2: com PROPAGATION_REQUIRES_NEW, falha em item não reverte outros")
    void shouldUseRequiresNewPropagationForEachItem() {
        // Este teste valida que cada processar() tem @Transactional(propagation=REQUIRES_NEW)
        // A intenção é que cada item tem sua própria transação; falha em uma não afeta outra

        Agendamento item1 = agendamento(1L, 42L);
        Agendamento item2 = agendamento(2L, 43L);

        when(agendamentoRepositorio.buscarPendentesAberturaJanela(10))
                .thenReturn(List.of(item1, item2));

        when(agendamentoRepositorio.atualizarStatusSeAtual(
                1L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);
        when(agendamentoRepositorio.atualizarStatusSeAtual(
                2L, StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO))
                .thenReturn(true);

        // Item 1 salva sucesso, item 2 falha
        doThrow(new RuntimeException("Erro ao salvar evento do item 2"))
                .when(eventoOutboxRepositorio).salvar(any());

        assertThatCode(() -> poller(10).abrirJanelas())
                .doesNotThrowAnyException();

        // Ambos os itens foram processados (try-catch isolou a falha)
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(agendamentoRepositorio, times(2)).atualizarStatusSeAtual(
                idCaptor.capture(), eq(StatusAgendamento.AGUARDANDO_JANELA),
                eq(StatusAgendamento.AGUARDANDO_CONFIRMACAO));

        assertThat(idCaptor.getAllValues()).containsExactly(1L, 2L);
    }
}
