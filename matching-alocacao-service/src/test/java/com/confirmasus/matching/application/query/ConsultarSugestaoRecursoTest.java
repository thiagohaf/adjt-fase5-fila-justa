package com.confirmasus.matching.application.query;

import com.confirmasus.matching.application.command.EventoOutboxRepositorio;
import com.confirmasus.matching.application.command.UltimaSugestaoRegistradaRepositorio;
import com.confirmasus.matching.domain.EventoOutbox;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import com.confirmasus.matching.domain.Recurso;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ConsultarSugestaoRecurso}: Sugestão de Repasse FIFO pura por
 * {@code criadoEm} da Lista de Espera do Recurso (AD-6, Architecture Spine)
 * -- nunca por gravidade, score ou qualquer critério clínico. Cobre também
 * exclusão de paciente já alocado, pulo de paciente já recusado para o
 * Recurso, e o rastreamento AD-10 ({@code SugestaoGerada} só quando a
 * sugestão muda).
 */
class ConsultarSugestaoRecursoTest {

    private static final Instant AGORA = Instant.parse("2026-09-11T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    private final RecursoConsultaRepositorio recursoConsultaRepositorio = mock(RecursoConsultaRepositorio.class);
    private final ListaEsperaEntradaConsultaRepositorio listaEsperaEntradaConsultaRepositorio =
            mock(ListaEsperaEntradaConsultaRepositorio.class);
    private final AlocacaoConsultaRepositorio alocacaoConsultaRepositorio = mock(AlocacaoConsultaRepositorio.class);
    private final SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio =
            mock(SugestaoRecusadaConsultaRepositorio.class);
    private final UltimaSugestaoRegistradaRepositorio ultimaSugestaoRegistradaRepositorio =
            mock(UltimaSugestaoRegistradaRepositorio.class);
    private final EventoOutboxRepositorio eventoOutboxRepositorio = mock(EventoOutboxRepositorio.class);

    private final ConsultarSugestaoRecurso useCase = new ConsultarSugestaoRecurso(
            recursoConsultaRepositorio, listaEsperaEntradaConsultaRepositorio, alocacaoConsultaRepositorio,
            sugestaoRecusadaConsultaRepositorio, ultimaSugestaoRegistradaRepositorio, eventoOutboxRepositorio,
            CLOCK);

    private static ListaEsperaEntrada entrada(long pacienteId, UUID recursoId, Instant criadoEm) {
        return new ListaEsperaEntrada(null, pacienteId, recursoId, criadoEm, criadoEm);
    }

    @Test
    void listaDeEsperaComVariosCandidatosSugereOMaisAntigoPorCriadoEm() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-01", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId)).thenReturn(List.of(
                entrada(10L, recursoId, Instant.parse("2026-09-11T09:00:00Z")),
                entrada(20L, recursoId, Instant.parse("2026-09-11T10:00:00Z"))));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.recursoId()).isEqualTo(recursoId);
        assertThat(resultado.pacienteId()).isEqualTo(10L);
    }

    @Test
    void listaDeEsperaVaziaRetornaResultadoSemErroComPacienteIdNulo() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-02", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId))
                .thenReturn(List.of());

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.recursoId()).isEqualTo(recursoId);
        assertThat(resultado.pacienteId()).isNull();
        verify(ultimaSugestaoRegistradaRepositorio, never()).registrar(any(), anyLong(), any());
        verify(eventoOutboxRepositorio, never()).salvar(any());
    }

    @Test
    void recursoIndisponivelRetornaResultadoSemErroComPacienteIdNuloENuncaConsultaAListaDeEspera() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-03", 2, false, null, null)));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.recursoId()).isEqualTo(recursoId);
        assertThat(resultado.pacienteId()).isNull();
        verify(listaEsperaEntradaConsultaRepositorio, never()).listarPorRecursoOrdenadoPorCriadoEm(any());
        verify(sugestaoRecusadaConsultaRepositorio, never()).recusadosPara(recursoId);
        verify(ultimaSugestaoRegistradaRepositorio, never()).registrar(any(), anyLong(), any());
        verify(eventoOutboxRepositorio, never()).salvar(any());
    }

    @Test
    void recursoInexistenteLancaRecursoNaoEncontradoENuncaConsultaAListaDeEspera() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.consultar(recursoId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining(recursoId.toString());

        verify(listaEsperaEntradaConsultaRepositorio, never()).listarPorRecursoOrdenadoPorCriadoEm(any());
        verify(ultimaSugestaoRegistradaRepositorio, never()).registrar(any(), anyLong(), any());
        verify(eventoOutboxRepositorio, never()).salvar(any());
    }

    @Test
    void pacienteComAlocacaoAtivaEExcluidoEOProximoDaListaAssume() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-04", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId)).thenReturn(List.of(
                entrada(10L, recursoId, Instant.parse("2026-09-11T09:00:00Z")),
                entrada(20L, recursoId, Instant.parse("2026-09-11T10:00:00Z"))));
        when(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva()).thenReturn(Set.of(10L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(20L);
    }

    @Test
    void pacienteRecusadoParaOMesmoRecursoEPuladoNaSugestao() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-05", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId)).thenReturn(List.of(
                entrada(10L, recursoId, Instant.parse("2026-09-11T09:00:00Z")),
                entrada(20L, recursoId, Instant.parse("2026-09-11T10:00:00Z"))));
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId)).thenReturn(Set.of(10L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(20L);
    }

    @Test
    void todosOsCandidatosRecusadosOuAlocadosRetornaPacienteIdNulo() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-06", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId)).thenReturn(List.of(
                entrada(10L, recursoId, Instant.parse("2026-09-11T09:00:00Z")),
                entrada(20L, recursoId, Instant.parse("2026-09-11T10:00:00Z"))));
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId)).thenReturn(Set.of(10L, 20L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isNull();
        verify(ultimaSugestaoRegistradaRepositorio, never()).registrar(any(), anyLong(), any());
        verify(eventoOutboxRepositorio, never()).salvar(any());
    }

    @Test
    void recusaRegistradaParaOutroRecursoNaoAfetaEsteResultado() {
        UUID recursoId = UUID.randomUUID();
        UUID outroRecursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-07", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId)).thenReturn(List.of(
                entrada(10L, recursoId, Instant.parse("2026-09-11T09:00:00Z")),
                entrada(20L, recursoId, Instant.parse("2026-09-11T10:00:00Z"))));
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId)).thenReturn(Set.of());
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(outroRecursoId)).thenReturn(Set.of(10L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(10L);
    }

    // Rastreamento AD-10: consultar() chama registrar(...) direto, SEM
    // pré-ler pacienteIdRegistrado antes -- ultimaSugestaoRegistradaRepositorio
    // é um compare-and-set atômico (retorno boolean) fechado no próprio SQL
    // (ver UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest); aqui
    // só se prova que ConsultarSugestaoRecurso reage corretamente aos 2
    // retornos possíveis.

    @Test
    void primeiraSugestaoDoRecursoRegistraEPublicaSugestaoGerada() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-08", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId))
                .thenReturn(List.of(entrada(10L, recursoId, AGORA)));
        when(ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 10L, AGORA)).thenReturn(true);

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(10L);
        verify(ultimaSugestaoRegistradaRepositorio).registrar(recursoId, 10L, AGORA);

        ArgumentCaptor<EventoOutbox> captor = ArgumentCaptor.forClass(EventoOutbox.class);
        verify(eventoOutboxRepositorio).salvar(captor.capture());
        EventoOutbox evento = captor.getValue();
        assertThat(evento.getId()).isNull();
        assertThat(evento.getEventId()).isNotNull();
        assertThat(evento.getEventType()).isEqualTo("SugestaoGerada");
        assertThat(evento.getOccurredAt()).isEqualTo(AGORA);
        assertThat(evento.getVersion()).isEqualTo(1);
        assertThat(evento.getCorrelationId()).isNotBlank();
        assertThat(evento.getPayload())
                .containsEntry("recursoId", recursoId)
                .containsEntry("pacienteId", 10L)
                .containsEntry("sugeridoEm", AGORA);
    }

    @Test
    void sugestaoQueRepeteOUltimoRegistroNaoPublicaSugestaoGerada() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-09", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId))
                .thenReturn(List.of(entrada(10L, recursoId, AGORA)));
        when(ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 10L, AGORA)).thenReturn(false);

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(10L);
        verify(ultimaSugestaoRegistradaRepositorio).registrar(recursoId, 10L, AGORA);
        verify(eventoOutboxRepositorio, never()).salvar(any());
    }

    @Test
    void duasChamadasSequenciaisApenasUmaComRegistrarTrueEEssaPublicaOEvento() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-10", 1, true, null, null)));
        when(listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId))
                .thenReturn(List.of(entrada(30L, recursoId, AGORA)));
        when(ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 30L, AGORA))
                .thenReturn(true, false);

        useCase.consultar(recursoId);
        useCase.consultar(recursoId);

        verify(ultimaSugestaoRegistradaRepositorio, times(2))
                .registrar(recursoId, 30L, AGORA);
        verify(eventoOutboxRepositorio, times(1)).salvar(any());
    }
}
