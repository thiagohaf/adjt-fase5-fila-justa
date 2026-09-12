package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.PrioridadeEfetiva;
import com.filajusta.matching.domain.ScoreReplica;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ConsultarFilaPriorizada}: dispara o bootstrap síncrono
 * ANTES de ler quando a réplica está vazia, nunca quando já tem linhas,
 * ordena o resultado por Prioridade Efetiva decrescente com desempate
 * estável por {@code occurredAt} (Boundaries/AC da spec 3.1c + Patch 5 do
 * code review), serializa "checar vazia + bootstrapar" para requisições
 * concorrentes nunca dispararem bootstrap em paralelo (Patch 2 do code
 * review), e exclui todo {@code pacienteId} com Alocação ATIVA
 * ({@link AlocacaoConsultaRepositorio#pacientesComAlocacaoAtiva()}, Story
 * 3-3b2b) antes de calcular Prioridade Efetiva/ordenar -- {@code Set} vazio
 * (default do mock) preserva 100% dos casos pré-existentes. Usa
 * {@link PrioridadeEfetiva} real (k/teto fixos, cálculo puro já coberto por
 * {@code PrioridadeEfetivaTest}).
 */
class ConsultarFilaPriorizadaTest {

    private static final Instant AGORA = Instant.parse("2026-09-08T18:00:00Z");

    private final FilaRepositorio filaRepositorio = mock(FilaRepositorio.class);
    private final ScoreBootstrap scoreBootstrap = mock(ScoreBootstrap.class);
    private final PrioridadeEfetiva prioridadeEfetiva = new PrioridadeEfetiva(20.0 / 18.0, 20.0);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
    // Mockito RETURNS_DEFAULTS ja responde Set.of() (vazio) para qualquer
    // metodo de retorno Set nao estubado -- "mock da porta (Set.of()
    // default)" do Code Map da spec 3-3b2b. So os testes desta story
    // estubam explicitamente um Set nao-vazio.
    private final AlocacaoConsultaRepositorio alocacaoConsultaRepositorio =
            mock(AlocacaoConsultaRepositorio.class);

    private final ConsultarFilaPriorizada useCase = new ConsultarFilaPriorizada(
            filaRepositorio, scoreBootstrap, prioridadeEfetiva, clock, alocacaoConsultaRepositorio);

    private static ScoreReplica replica(long pacienteId, int score, Instant occurredAt) {
        return replica(pacienteId, score, occurredAt, null);
    }

    private static ScoreReplica replica(long pacienteId, int score, Instant occurredAt,
                                         Long numeroSequencialTriagem) {
        return new ScoreReplica(
                pacienteId, score, occurredAt, UUID.randomUUID(), Instant.now(), numeroSequencialTriagem);
    }

    @Test
    void consultaNormalOrdenaPorPrioridadeEfetivaDecrescenteESemBootstrap() {
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 50, AGORA), // prioridade = 50
                replica(2L, 40, AGORA.minusSeconds(18 * 3600)), // prioridade = 40 + 20 = 60
                replica(3L, 90, AGORA))); // prioridade = 90

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        assertThat(fila).extracting(ConsultarFilaPriorizada.ItemFila::pacienteId)
                .containsExactly(3L, 2L, 1L);
        verify(scoreBootstrap, never()).bootstrapar();
    }

    @Test
    void replicaVaziaDisparaBootstrapAntesDeLer() {
        when(filaRepositorio.estaVazia()).thenReturn(true);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(replica(1L, 60, AGORA)));

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        InOrder ordem = inOrder(scoreBootstrap, filaRepositorio);
        ordem.verify(scoreBootstrap).bootstrapar();
        ordem.verify(filaRepositorio).listarTodas();
        assertThat(fila).hasSize(1);
    }

    @Test
    void falhaNoBootstrapPropagaENuncaLeAReplica() {
        when(filaRepositorio.estaVazia()).thenReturn(true);
        ScoreBootstrapIndisponivelException falha = new ScoreBootstrapIndisponivelException(
                "triagem-score-service indisponivel", new RuntimeException("boom"));
        doThrow(falha).when(scoreBootstrap).bootstrapar();

        assertThatThrownBy(useCase::consultar).isSameAs(falha);

        verify(filaRepositorio, never()).listarTodas();
    }

    @Test
    void empateDePrioridadeEfetivaDesempataDeFormaEstavelPorOccurredAtAscendente() {
        // Patch 5 do code review: sem chave secundaria, a ordem entre
        // pacientes empatados em Prioridade Efetiva nao era garantida.
        Instant occurredAtMaisAntigo = AGORA.minusSeconds(3600);
        Instant occurredAtMaisRecente = AGORA;
        when(filaRepositorio.estaVazia()).thenReturn(false);
        // Ambos score=50, occurredAt==agora -- aging=0 para os dois -->
        // prioridade efetiva empatada em 50.0 para pacienteId 1 e 2.
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 50, occurredAtMaisRecente),
                replica(2L, 50, occurredAtMaisAntigo)));

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        // occurredAt ascendente desempata -- quem espera ha mais tempo
        // (occurredAt mais antigo) vem primeiro entre os empatados.
        assertThat(fila).extracting(ConsultarFilaPriorizada.ItemFila::pacienteId)
                .containsExactly(2L, 1L);
    }

    @Test
    void empateResidualDePrioridadeEfetivaEOccurredAtDesempataPorNumeroSequencialTriagemAscendente() {
        // AC da spec 3.2b1: dois Pacientes com Prioridade Efetiva e
        // occurredAt identicos -- vence o de menor numeroSequencialTriagem
        // (Triagem mais antiga).
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 50, AGORA, 20L),
                replica(2L, 50, AGORA, 10L)));

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        assertThat(fila).extracting(ConsultarFilaPriorizada.ItemFila::pacienteId)
                .containsExactly(2L, 1L);
    }

    @Test
    void empateResidualComNumeroSequencialTriagemNuloPerdeParaOItemComValorNaoNulo() {
        // I/O Matrix da spec 3.2b1: nulls-last -- item sem o dado nunca
        // quebra a ordenacao nem e excluido, so perde o desempate final.
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 50, AGORA, null),
                replica(2L, 50, AGORA, 10L)));

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        assertThat(fila).extracting(ConsultarFilaPriorizada.ItemFila::pacienteId)
                .containsExactly(2L, 1L);
        assertThat(fila).hasSize(2);
    }

    @Test
    void requisicoesConcorrentesComReplicaVaziaDisparamBootstrapApenasUmaVez() throws Exception {
        // Patch 2 do code review: 1a checagem de estaVazia() (dentro do
        // lock) ve true e dispara o bootstrap; a partir dai o mock passa a
        // responder false (simula a replica deixando de estar vazia apos
        // o bootstrap rodar) -- qualquer outra thread que entre no bloco
        // sincronizado depois nao deve disparar um segundo bootstrap.
        when(filaRepositorio.estaVazia()).thenReturn(true, false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(replica(1L, 60, AGORA)));

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<List<ConsultarFilaPriorizada.ItemFila>>> resultados = new ArrayList<>();
            for (int i = 0; i < totalThreads; i++) {
                resultados.add(executor.submit(() -> {
                    partida.await();
                    return useCase.consultar();
                }));
            }

            partida.countDown();
            for (Future<List<ConsultarFilaPriorizada.ItemFila>> resultado : resultados) {
                assertThat(resultado.get(10, TimeUnit.SECONDS)).hasSize(1);
            }
        } finally {
            executor.shutdown();
        }

        verify(scoreBootstrap, times(1)).bootstrapar();
    }

    @Test
    void pacienteComAlocacaoAtivaNaoApareceNaFila() {
        // I/O Matrix da spec 3-3b2b: "Com Alocacao ativa".
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 90, AGORA),
                replica(2L, 50, AGORA)));
        when(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva()).thenReturn(Set.of(1L));

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        assertThat(fila).extracting(ConsultarFilaPriorizada.ItemFila::pacienteId)
                .containsExactly(2L);
    }

    @Test
    void pacienteSemAlocacaoAtivaApareceNormalmenteOrdenadoPorPrioridadeEfetiva() {
        // I/O Matrix da spec 3-3b2b: "Sem Alocacao ativa".
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 90, AGORA),
                replica(2L, 50, AGORA)));
        when(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva()).thenReturn(Set.of(999L));

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        assertThat(fila).extracting(ConsultarFilaPriorizada.ItemFila::pacienteId)
                .containsExactly(1L, 2L);
    }

    @Test
    void nenhumaAlocacaoNoSistemaPreservaAFilaCompletaIdenticaAoComportamentoPreExistente() {
        // I/O Matrix da spec 3-3b2b: "Nenhuma Alocacao no sistema" -- Set
        // vazio preserva 100% do comportamento pre-existente.
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 90, AGORA),
                replica(2L, 50, AGORA),
                replica(3L, 70, AGORA)));
        when(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva()).thenReturn(Set.of());

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        assertThat(fila).extracting(ConsultarFilaPriorizada.ItemFila::pacienteId)
                .containsExactly(1L, 3L, 2L);
    }

    @Test
    void todosOsPacientesAlocadosResultaEmListaVazia() {
        // I/O Matrix da spec 3-3b2b: "Todos alocados".
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 90, AGORA),
                replica(2L, 50, AGORA)));
        when(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva()).thenReturn(Set.of(1L, 2L));

        List<ConsultarFilaPriorizada.ItemFila> fila = useCase.consultar();

        assertThat(fila).isEmpty();
    }

    @Test
    void pacientesComAlocacaoAtivaELidoUmaUnicaVezPorChamadaAConsultar() {
        // Boundaries da spec 3-3b2b: pacientesComAlocacaoAtiva() chamado 1x
        // por consultar(), nunca dentro do predicado (o que o chamaria uma
        // vez por replica).
        when(filaRepositorio.estaVazia()).thenReturn(false);
        when(filaRepositorio.listarTodas()).thenReturn(List.of(
                replica(1L, 90, AGORA),
                replica(2L, 50, AGORA),
                replica(3L, 70, AGORA)));
        when(alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva()).thenReturn(Set.of());

        useCase.consultar();

        verify(alocacaoConsultaRepositorio, times(1)).pacientesComAlocacaoAtiva();
    }
}
