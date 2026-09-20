package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ExpirarJanelaDeConfirmacao — isolamento transacional com PROPAGATION_REQUIRES_NEW")
class ExpirarJanelaDeConfirmacaoTransactionRollbackTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    private final AgendamentoRepositorio agendamentoRepositorio = mock(AgendamentoRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    @DisplayName("FIX-1: falha em salvar evento propaga com @Transactional(propagation=REQUIRES_NEW)")
    void shouldPropagateExceptionOnEventoSalvarFailure() {
        UUID recursoId = UUID.randomUUID();
        Agendamento agendamento = new Agendamento(
                1L,
                42L,
                recursoId,
                AGORA.plus(java.time.Duration.ofDays(1)),
                StatusAgendamento.AGUARDANDO_CONFIRMACAO,
                AGORA.minus(java.time.Duration.ofHours(1)),
                AGORA.minus(java.time.Duration.ofHours(2)),
                AGORA.minus(java.time.Duration.ofMinutes(1)));

        when(agendamentoRepositorio.atualizarStatusComMotivo(
                1L, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO, "NAO_CONFIRMADO"))
                .thenReturn(true);

        doThrow(new RuntimeException("Falha ao salvar evento no outbox"))
                .when(eventoOutboxRepositorio).salvar(any());

        ExpirarJanelaDeConfirmacao handler = new ExpirarJanelaDeConfirmacao(
                agendamentoRepositorio, eventoOutboxRepositorio, clock, 10);

        assertThatThrownBy(() -> handler.processar(agendamento))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha ao salvar evento");
    }
}
