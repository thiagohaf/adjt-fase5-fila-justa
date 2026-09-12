package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.Recurso;
import com.filajusta.matching.domain.ScoreReplica;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ConsultarSugestaoRecurso}: aplica o algoritmo de tiers de
 * desempate sobre a fila global já priorizada por {@link
 * ConsultarFilaPriorizada} (mockada aqui -- seu próprio comportamento de
 * ordenação/bootstrap já é coberto por {@code ConsultarFilaPriorizadaTest}),
 * a I/O &amp; Edge-Case Matrix completa da spec 3.2b3 (HAPPY_PATH,
 * SEM_RECURSO_GENERICO_DISPONIVEL, MESMO_TIER_NAO_BLOQUEIA, FILA_ESGOTADA,
 * RECURSO_INDISPONIVEL, RECURSO_INEXISTENTE) e da spec 3-3c2a (pulo de
 * pacientes recusados -- {@code sugestaoRecusadaConsultaRepositorio} não
 * stubado retorna {@code Set} vazio por padrão do Mockito, preservando o
 * comportamento dos cenários herdados da 3.2b3 sem stub explícito).
 */
class ConsultarSugestaoRecursoTest {

    private static final Instant AGORA = Instant.parse("2026-09-11T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    private final RecursoConsultaRepositorio recursoConsultaRepositorio = mock(RecursoConsultaRepositorio.class);
    private final ConsultarFilaPriorizada consultarFilaPriorizada = mock(ConsultarFilaPriorizada.class);
    private final SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio =
            mock(SugestaoRecusadaConsultaRepositorio.class);

    private final ConsultarSugestaoRecurso useCase = new ConsultarSugestaoRecurso(
            recursoConsultaRepositorio, consultarFilaPriorizada, sugestaoRecusadaConsultaRepositorio);

    private static ConsultarFilaPriorizada.ItemFila item(long pacienteId) {
        return ConsultarFilaPriorizada.ItemFila.de(
                new ScoreReplica(pacienteId, 50, AGORA, UUID.randomUUID(), AGORA, null), 50.0);
    }

    private static List<ConsultarFilaPriorizada.ItemFila> filaComPacientes(long... pacienteIds) {
        return java.util.Arrays.stream(pacienteIds)
                .mapToObj(ConsultarSugestaoRecursoTest::item)
                .toList();
    }

    @Test
    void happyPathComUmTierMaisGenericoDisponivelSugereFilaGlobalDoIndiceN() {
        // HAPPY_PATH: Recurso rank=2, 1 Recurso disponivel rank=1 -> N=1.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-01", 2, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(2)).thenReturn(1);
        when(consultarFilaPriorizada.consultar()).thenReturn(filaComPacientes(10L, 20L, 30L, 40L, 50L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.recursoId()).isEqualTo(recursoId);
        assertThat(resultado.pacienteId()).isEqualTo(20L);
    }

    @Test
    void semRecursoGenericoDisponivelSugereFilaGlobalDoIndiceZero() {
        // SEM_RECURSO_GENERICO_DISPONIVEL: rank=1 (mais generico possivel),
        // nenhum Recurso rank<1 existe -> N=0.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "LEITO-01", 1, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(1)).thenReturn(0);
        when(consultarFilaPriorizada.consultar()).thenReturn(filaComPacientes(10L, 20L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(10L);
    }

    @Test
    void recursosDoMesmoTierNaoIncrementamN() {
        // MESMO_TIER_NAO_BLOQUEIA: 2 Recursos disponiveis rank=1, Recurso
        // consultado rank=2 -> N=1 (nao 2) -- consumido pelo port, aqui so
        // verificamos que o resultado do port (contagem DISTINCT ja feita
        // pela infra) e usado como esta, sem recontar por instancia.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-02", 2, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(2)).thenReturn(1);
        when(consultarFilaPriorizada.consultar()).thenReturn(filaComPacientes(10L, 20L, 30L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(20L);
    }

    @Test
    void filaGlobalEsgotadaAntesDoIndiceNRetornaResultadoSemErroComPacienteIdNulo() {
        // FILA_ESGOTADA: N >= tamanho da fila global -> 200 com ausencia de
        // sugestao (pacienteId null), nunca erro.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-03", 3, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(3)).thenReturn(2);
        when(consultarFilaPriorizada.consultar()).thenReturn(filaComPacientes(10L, 20L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.recursoId()).isEqualTo(recursoId);
        assertThat(resultado.pacienteId()).isNull();
        // n >= filaGlobal.size() -- fila ja esgotada so pela contagem de
        // tiers, nenhum pulo de recusados e possivel, entao a consulta de
        // recusados nem precisa ser feita (spec 3-3c2a: evita round-trip
        // inutil ao banco).
        verify(sugestaoRecusadaConsultaRepositorio, never()).recusadosPara(recursoId);
    }

    @Test
    void filaGlobalVaziaComNZeroRetornaResultadoSemErroComPacienteIdNulo() {
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-04", 1, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(1)).thenReturn(0);
        when(consultarFilaPriorizada.consultar()).thenReturn(List.of());

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isNull();
        verify(sugestaoRecusadaConsultaRepositorio, never()).recusadosPara(recursoId);
    }

    @Test
    void recursoIndisponivelRetornaResultadoSemErroComPacienteIdNuloENuncaConsultaAFilaGlobal() {
        // RECURSO_INDISPONIVEL: Recurso existe mas disponivel=false -- nunca
        // e elegivel (decisao do usuario no loopback de review multi-agente
        // da Story 3.2b3, mesmo tratamento de FILA_ESGOTADA), entao nem a
        // contagem de tiers nem a fila global devem ser consultadas.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-05", 2, false)));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.recursoId()).isEqualTo(recursoId);
        assertThat(resultado.pacienteId()).isNull();
        verify(consultarFilaPriorizada, never()).consultar();
        verify(recursoConsultaRepositorio, never()).contarTiersMaisGenericosDisponiveis(anyInt());
        verify(sugestaoRecusadaConsultaRepositorio, never()).recusadosPara(recursoId);
    }

    @Test
    void recursoInexistenteLancaRecursoNaoEncontradoENuncaConsultaAFilaGlobal() {
        // RECURSO_INEXISTENTE: id valido (UUID) mas sem registro -> excecao
        // propagada (infrastructure/web traduz para 404) -- a fila global
        // nunca deve ser consultada quando o Recurso nem existe.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.consultar(recursoId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining(recursoId.toString());

        verify(consultarFilaPriorizada, never()).consultar();
        verify(recursoConsultaRepositorio, never()).contarTiersMaisGenericosDisponiveis(anyInt());
        verify(sugestaoRecusadaConsultaRepositorio, never()).recusadosPara(recursoId);
    }

    @Test
    void pacienteRecusadoParaOMesmoRecursoEPuladoNaSugestao() {
        // Topo ja recusado (spec 3-3c2a): filaGlobal[n]=20L recusado -> pula
        // para o proximo elegivel, sem alterar n.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-01", 2, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(2)).thenReturn(1);
        when(consultarFilaPriorizada.consultar()).thenReturn(filaComPacientes(10L, 20L, 30L, 40L));
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId)).thenReturn(Set.of(20L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(30L);
    }

    @Test
    void todosOsCandidatosRecusadosAteOFimRetornaPacienteIdNulo() {
        // Todos recusados ate o fim (spec 3-3c2a): mesmo tratamento de
        // FILA_ESGOTADA, sem erro.
        UUID recursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-01", 2, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(2)).thenReturn(1);
        when(consultarFilaPriorizada.consultar()).thenReturn(filaComPacientes(10L, 20L, 30L));
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId)).thenReturn(Set.of(20L, 30L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isNull();
    }

    @Test
    void recusaRegistradaParaOutroRecursoNaoAfetaEsteResultado() {
        // Recusado e de outro Recurso (spec 3-3c2a): recusadosPara e
        // escopado por recursoId -- pacienteId=20L esta recusado APENAS
        // para outroRecursoId, nunca para recursoId (o consultado aqui).
        // Se a implementacao ignorasse o parametro recursoId (ex.:
        // agregasse recusas de todos os Recursos), este teste capturaria o
        // bug: o resultado passaria de 20L para 30L.
        UUID recursoId = UUID.randomUUID();
        UUID outroRecursoId = UUID.randomUUID();
        when(recursoConsultaRepositorio.buscarPorId(recursoId))
                .thenReturn(Optional.of(new Recurso(recursoId, "SALA-01", 2, true)));
        when(recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(2)).thenReturn(1);
        when(consultarFilaPriorizada.consultar()).thenReturn(filaComPacientes(10L, 20L, 30L));
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId)).thenReturn(Set.of());
        when(sugestaoRecusadaConsultaRepositorio.recusadosPara(outroRecursoId)).thenReturn(Set.of(20L));

        ConsultarSugestaoRecurso.Resultado resultado = useCase.consultar(recursoId);

        assertThat(resultado.pacienteId()).isEqualTo(20L);
    }
}
