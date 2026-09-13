package com.filajusta.matching.application.command;

import com.filajusta.matching.application.query.RecursoConsultaRepositorio;
import com.filajusta.matching.application.query.RecursoNaoEncontradoException;
import com.filajusta.matching.domain.Alocacao;
import com.filajusta.matching.domain.EventoOutbox;
import com.filajusta.matching.domain.LiberacaoAgendada;
import com.filajusta.matching.domain.Recurso;
import com.filajusta.matching.infrastructure.config.LiberacaoDuracaoProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ConfirmarAlocacao} com mocks das 5 portas (Stories 3-3b1 e
 * 3-4a1) -- a I/O &amp; Edge-Case Matrix da spec: confirmação feliz, os 2
 * cenários de {@code 409} (propagados sem tratamento, traduzidos para RFC
 * 7807 em {@code infrastructure/web}), Recurso inexistente e
 * {@code correlationId} acima do limite persistível. A prova de que os 2
 * índices únicos parciais REALMENTE rejeitam sob concorrência fica em
 * {@code AlocacaoRepositorioAdapterIntegrationTest} (Testcontainers) -- aqui
 * só se prova a orquestração do caso de uso.
 *
 * <p>{@link LiberacaoDuracaoProperties} (Story 3-4a1) não é mockada --
 * classe concreta simples, sem dependência de framework, montada com
 * durações fixas e distintas por rank para os testes identificarem sem
 * ambiguidade qual delay foi propagado a
 * {@link LiberacaoAgendadaRepositorio#salvar}.
 */
class ConfirmarAlocacaoTest {

    private static final Instant AGORA = Instant.parse("2026-09-11T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final UUID RECURSO_ID = UUID.randomUUID();
    private static final long PACIENTE_ID = 42L;

    // Rank N -> N*30s: valores distintos e facilmente identificaveis por
    // teste (sem relacao com os valores reais de application.yml).
    private static final LiberacaoDuracaoProperties DURACAO_POR_RANK = new LiberacaoDuracaoProperties(
            Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(90), Duration.ofSeconds(120));

    private final AlocacaoRepositorio alocacaoRepositorio = mock(AlocacaoRepositorio.class);
    private final RecursoRepositorio recursoRepositorio = mock(RecursoRepositorio.class);
    private final RecursoConsultaRepositorio recursoConsultaRepositorio = mock(RecursoConsultaRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);
    private final LiberacaoAgendadaRepositorio liberacaoAgendadaRepositorio = mock(LiberacaoAgendadaRepositorio.class);

    private final ConfirmarAlocacao useCase = new ConfirmarAlocacao(
            alocacaoRepositorio, recursoRepositorio, recursoConsultaRepositorio, eventoOutboxRepositorio,
            liberacaoAgendadaRepositorio, DURACAO_POR_RANK, CLOCK);

    private void recursoExistenteEDisponivel() {
        when(recursoConsultaRepositorio.buscarPorId(RECURSO_ID))
                .thenReturn(Optional.of(new Recurso(RECURSO_ID, "LEITO-01", 1, true)));
    }

    @Test
    void confirmacaoFelizCriaAlocacaoMarcaRecursoIndisponivelGravaEventoOutboxEAgendaLiberacao() {
        recursoExistenteEDisponivel();
        when(alocacaoRepositorio.confirmar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        Alocacao alocacao = useCase.confirmar(RECURSO_ID, PACIENTE_ID, "corr-1");

        assertThat(alocacao.getRecursoId()).isEqualTo(RECURSO_ID);
        assertThat(alocacao.getPacienteId()).isEqualTo(PACIENTE_ID);
        assertThat(alocacao.getStatus()).isEqualTo(Alocacao.STATUS_ATIVA);
        assertThat(alocacao.getConfirmadoEm()).isEqualTo(AGORA);

        verify(recursoRepositorio).marcarIndisponivel(RECURSO_ID);

        ArgumentCaptor<EventoOutbox> eventoCaptor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(eventoCaptor.capture());
        EventoOutbox evento = eventoCaptor.getValue();
        assertThat(evento.getEventType()).isEqualTo("AlocacaoConfirmada");
        assertThat(evento.getCorrelationId()).isEqualTo("corr-1");
        assertThat(evento.getPayload()).containsEntry("recursoId", RECURSO_ID);
        assertThat(evento.getPayload()).containsEntry("pacienteId", PACIENTE_ID);

        // I/O Matrix da spec 3-4a1: "Confirmação agenda liberação" --
        // Recurso de rank 1 (recursoExistenteEDisponivel) usa a duracao
        // configurada para o rank 1 (30s, DURACAO_POR_RANK acima).
        ArgumentCaptor<LiberacaoAgendada> liberacaoCaptor = ArgumentCaptor.forClass(LiberacaoAgendada.class);
        verify(liberacaoAgendadaRepositorio).salvar(liberacaoCaptor.capture());
        LiberacaoAgendada liberacao = liberacaoCaptor.getValue();
        assertThat(liberacao.getAlocacaoId()).isEqualTo(alocacao.getAlocacaoId());
        assertThat(liberacao.getRecursoId()).isEqualTo(RECURSO_ID);
        assertThat(liberacao.getCorrelationId()).isEqualTo("corr-1");
        assertThat(liberacao.getDelaySegundos()).isEqualTo(30);
        assertThat(liberacao.getCriadoEm()).isEqualTo(AGORA);
        assertThat(liberacao.getEnviadoEm()).isNull();
    }

    @Test
    void confirmacaoUsaADuracaoConfiguradaParaOEspecificidadeRankDoRecurso() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "LEITO-03", 3, true)));
        when(alocacaoRepositorio.confirmar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        useCase.confirmar(recursoId, PACIENTE_ID, "corr-1");

        ArgumentCaptor<LiberacaoAgendada> liberacaoCaptor = ArgumentCaptor.forClass(LiberacaoAgendada.class);
        verify(liberacaoAgendadaRepositorio).salvar(liberacaoCaptor.capture());
        // Rank 3 -> 90s (DURACAO_POR_RANK acima), nao a duracao do rank 1.
        assertThat(liberacaoCaptor.getValue().getDelaySegundos()).isEqualTo(90);
    }

    @Test
    void correlationIdAusenteGeraUmUuidNuncaGravaNulo() {
        recursoExistenteEDisponivel();
        when(alocacaoRepositorio.confirmar(any())).thenAnswer(chamada -> chamada.getArgument(0));

        useCase.confirmar(RECURSO_ID, PACIENTE_ID, null);

        ArgumentCaptor<EventoOutbox> eventoCaptor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(eventoCaptor.capture());
        assertThat(UUID.fromString(eventoCaptor.getValue().getCorrelationId())).isNotNull();
    }

    @Test
    void correlationIdAcimaDoLimitePersistivelLancaExcecaoAntesDeQualquerEscrita() {
        String correlationIdMuitoLongo = "x".repeat(129);

        assertThatThrownBy(() -> useCase.confirmar(RECURSO_ID, PACIENTE_ID, correlationIdMuitoLongo))
                .isInstanceOf(CorrelationIdInvalidoException.class);

        verifyNoInteractions(recursoConsultaRepositorio, alocacaoRepositorio, recursoRepositorio,
                eventoOutboxRepositorio, liberacaoAgendadaRepositorio);
    }

    @Test
    void recursoIndisponivelSemAlocacaoAtivaLancaRecursoJaAlocadoENuncaChamaAlocacaoRepositorio() {
        // Achado do code review multi-agente (convergente nos 3 revisores):
        // os 2 indices unicos parciais so protegem contra uma segunda
        // Alocacao ATIVA -- um Recurso marcado indisponivel por outro
        // caminho (ex. POST /internal/recursos), sem nenhuma linha em
        // "alocacao", passaria pelo INSERT sem violar constraint nenhuma se
        // este caso de uso nao checasse isDisponivel() explicitamente.
        when(recursoConsultaRepositorio.buscarPorId(RECURSO_ID))
                .thenReturn(Optional.of(new Recurso(RECURSO_ID, "LEITO-01", 1, false)));

        assertThatThrownBy(() -> useCase.confirmar(RECURSO_ID, PACIENTE_ID, "corr-1"))
                .isInstanceOf(RecursoJaAlocadoException.class);

        verifyNoInteractions(alocacaoRepositorio, recursoRepositorio, eventoOutboxRepositorio,
                liberacaoAgendadaRepositorio);
    }

    @Test
    void recursoInexistenteLancaRecursoNaoEncontradoENuncaChamaAlocacaoRepositorio() {
        when(recursoConsultaRepositorio.buscarPorId(RECURSO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirmar(RECURSO_ID, PACIENTE_ID, "corr-1"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining(RECURSO_ID.toString());

        verifyNoInteractions(alocacaoRepositorio, recursoRepositorio, eventoOutboxRepositorio,
                liberacaoAgendadaRepositorio);
    }

    @Test
    void recursoJaAlocadoPropagaExcecaoSemMarcarIndisponivelNemGravarEventoNemAgendarLiberacao() {
        recursoExistenteEDisponivel();
        when(alocacaoRepositorio.confirmar(any())).thenThrow(new RecursoJaAlocadoException(RECURSO_ID));

        assertThatThrownBy(() -> useCase.confirmar(RECURSO_ID, PACIENTE_ID, "corr-1"))
                .isInstanceOf(RecursoJaAlocadoException.class);

        verify(recursoRepositorio, never()).marcarIndisponivel(any());
        verifyNoInteractions(eventoOutboxRepositorio, liberacaoAgendadaRepositorio);
    }

    @Test
    void pacienteJaAlocadoPropagaExcecaoSemMarcarIndisponivelNemGravarEventoNemAgendarLiberacao() {
        recursoExistenteEDisponivel();
        when(alocacaoRepositorio.confirmar(any())).thenThrow(new PacienteJaAlocadoException(PACIENTE_ID));

        assertThatThrownBy(() -> useCase.confirmar(RECURSO_ID, PACIENTE_ID, "corr-1"))
                .isInstanceOf(PacienteJaAlocadoException.class);

        verify(recursoRepositorio, never()).marcarIndisponivel(any());
        verifyNoInteractions(eventoOutboxRepositorio, liberacaoAgendadaRepositorio);
    }
}
