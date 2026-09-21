package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link LiberarRecurso} com mocks das 3 portas (Story 3-4b1) -- a
 * I/O &amp; Edge-Case Matrix da spec: liberação feliz (transição aplicada) e
 * já-liberada/inexistente (0 linhas afetadas pelo UPDATE condicional). A
 * prova de que a idempotência REALMENTE segura sob concorrência real fica em
 * {@code LiberarRecursoRepositorioAdapterIntegrationTest} (Testcontainers) --
 * aqui só se prova a orquestração do caso de uso, mesmo padrão de
 * {@code ConfirmarAlocacaoTest}.
 */
class LiberarRecursoTest {

    private static final Instant AGORA = Instant.parse("2026-09-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final UUID ALOCACAO_ID = UUID.randomUUID();
    private static final UUID RECURSO_ID = UUID.randomUUID();

    private final AlocacaoRepositorio alocacaoRepositorio = mock(AlocacaoRepositorio.class);
    private final RecursoRepositorio recursoRepositorio = mock(RecursoRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);

    private final LiberarRecurso useCase = new LiberarRecurso(
            alocacaoRepositorio, recursoRepositorio, eventoOutboxRepositorio, CLOCK);

    @Test
    void liberacaoFelizMarcaRecursoDisponivelEGravaRecursoLiberadoNoOutbox() {
        when(alocacaoRepositorio.liberar(ALOCACAO_ID)).thenReturn(true);

        useCase.liberar(ALOCACAO_ID, RECURSO_ID, "corr-1");

        verify(recursoRepositorio).marcarDisponivel(RECURSO_ID);

        ArgumentCaptor<EventoOutbox> eventoCaptor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(eventoCaptor.capture());
        EventoOutbox evento = eventoCaptor.getValue();
        assertThat(evento.getEventType()).isEqualTo("RecursoLiberado");
        assertThat(evento.getOccurredAt()).isEqualTo(AGORA);
        assertThat(evento.getVersion()).isEqualTo(1);
        assertThat(evento.getCorrelationId()).isEqualTo("corr-1");
        // payload.recursoId e o que o relay SNS FIFO usa como MessageGroupId
        // (Boundaries da spec 3-4b1) -- tem que estar presente sempre.
        assertThat(evento.getPayload()).containsEntry("recursoId", RECURSO_ID);
        assertThat(evento.getPayload()).containsEntry("alocacaoId", ALOCACAO_ID);
    }

    @Test
    void jaLiberadaOuInexistenteNaoMarcaRecursoDisponivelNemGravaEvento() {
        when(alocacaoRepositorio.liberar(ALOCACAO_ID)).thenReturn(false);

        useCase.liberar(ALOCACAO_ID, RECURSO_ID, "corr-1");

        verify(recursoRepositorio, never()).marcarDisponivel(RECURSO_ID);
        verifyNoInteractions(eventoOutboxRepositorio);
    }
}
