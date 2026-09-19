package com.filajusta.matching.application.command;

import com.filajusta.matching.application.query.RecursoConsultaRepositorio;
import com.filajusta.matching.application.query.RecursoNaoEncontradoException;
import com.filajusta.matching.domain.EventoOutbox;
import com.filajusta.matching.domain.Recurso;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link RecusarSugestao} com mocks das 3 portas (Story 3-3c1) -- a
 * I/O &amp; Edge-Case Matrix da spec: recusa feliz (incluindo a duplicada,
 * que é só uma segunda chamada idempotente, sem nenhum tratamento especial
 * no caso de uso -- a idempotência mora no upsert do adapter, não aqui),
 * Recurso inexistente e {@code correlationId} acima do limite persistível.
 * A prova de que o upsert nativo REALMENTE não duplica linha fica em
 * {@code AlocacaoControllerIntegrationTest} (Testcontainers) -- aqui só se
 * prova a orquestração do caso de uso, mesmo molde de
 * {@code ConfirmarAlocacaoTest}.
 */
class RecusarSugestaoTest {

    private static final Instant AGORA = Instant.parse("2026-09-12T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final UUID RECURSO_ID = UUID.randomUUID();
    private static final long PACIENTE_ID = 42L;
    private static final String MOTIVO = "Paciente recusou o leito ofertado";

    private final SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio = mock(SugestaoRecusadaRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final RecursoConsultaRepositorio recursoConsultaRepositorio = mock(RecursoConsultaRepositorio.class);

    private final RecusarSugestao useCase = new RecusarSugestao(
            sugestaoRecusadaRepositorio, eventoOutboxRepositorio, recursoConsultaRepositorio, CLOCK);

    private void recursoExistente() {
        when(recursoConsultaRepositorio.buscarPorId(RECURSO_ID))
                .thenReturn(Optional.of(new Recurso(RECURSO_ID, "LEITO-01", 1, true)));
    }

    @Test
    void recusaFelizRegistraParEGravaEventoOutbox() {
        recursoExistente();

        Instant recusadoEm = useCase.recusar(RECURSO_ID, PACIENTE_ID, MOTIVO, "corr-1");

        assertThat(recusadoEm).isEqualTo(AGORA);
        verify(sugestaoRecusadaRepositorio).registrar(RECURSO_ID, PACIENTE_ID, MOTIVO, AGORA);

        ArgumentCaptor<EventoOutbox> eventoCaptor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(eventoCaptor.capture());
        EventoOutbox evento = eventoCaptor.getValue();
        assertThat(evento.getEventType()).isEqualTo("SugestaoRecusada");
        assertThat(evento.getCorrelationId()).isEqualTo("corr-1");
        assertThat(evento.getVersion()).isEqualTo(1);
        assertThat(evento.getOccurredAt()).isEqualTo(AGORA);
        assertThat(evento.getPayload()).containsEntry("recursoId", RECURSO_ID);
        assertThat(evento.getPayload()).containsEntry("pacienteId", PACIENTE_ID);
        assertThat(evento.getPayload()).containsEntry("motivo", MOTIVO);
        assertThat(evento.getPayload()).containsEntry("recusadoEm", AGORA);
    }

    @Test
    void recusaDuplicadaParaOMesmoParApenasChamaRegistrarDeNovoSemFalhar() {
        recursoExistente();

        useCase.recusar(RECURSO_ID, PACIENTE_ID, MOTIVO, "corr-1");
        Instant segundaRecusadoEm = useCase.recusar(RECURSO_ID, PACIENTE_ID, "outro motivo", "corr-2");

        assertThat(segundaRecusadoEm).isEqualTo(AGORA);
        verify(sugestaoRecusadaRepositorio).registrar(RECURSO_ID, PACIENTE_ID, MOTIVO, AGORA);
        verify(sugestaoRecusadaRepositorio).registrar(RECURSO_ID, PACIENTE_ID, "outro motivo", AGORA);
        verify(eventoOutboxRepositorio, times(2)).salvar(any());
    }

    @Test
    void correlationIdAusenteGeraUmUuidNuncaGravaNulo() {
        recursoExistente();

        useCase.recusar(RECURSO_ID, PACIENTE_ID, MOTIVO, null);

        ArgumentCaptor<EventoOutbox> eventoCaptor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(eventoCaptor.capture());
        assertThat(UUID.fromString(eventoCaptor.getValue().getCorrelationId())).isNotNull();
    }

    @Test
    void correlationIdAcimaDoLimitePersistivelLancaExcecaoAntesDeQualquerEscrita() {
        String correlationIdMuitoLongo = "x".repeat(129);

        assertThatThrownBy(() -> useCase.recusar(RECURSO_ID, PACIENTE_ID, MOTIVO, correlationIdMuitoLongo))
                .isInstanceOf(CorrelationIdInvalidoException.class);

        verifyNoInteractions(recursoConsultaRepositorio, sugestaoRecusadaRepositorio, eventoOutboxRepositorio);
    }

    @Test
    void recursoInexistenteLancaRecursoNaoEncontradoENuncaRegistraNemGravaEvento() {
        when(recursoConsultaRepositorio.buscarPorId(RECURSO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.recusar(RECURSO_ID, PACIENTE_ID, MOTIVO, "corr-1"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining(RECURSO_ID.toString());

        verifyNoInteractions(sugestaoRecusadaRepositorio, eventoOutboxRepositorio);
    }
}
