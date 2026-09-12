package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.PrioridadeEfetiva;
import com.filajusta.matching.domain.ScoreReplica;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Caso de uso de {@code GET /v1/fila} (Story 3.1c): réplica vazia dispara o
 * bootstrap síncrono ({@link ScoreBootstrap#bootstrapar()}) ANTES de ler --
 * nunca responde uma fila incompleta silenciosamente (Boundaries da spec).
 * Recomputa a Prioridade Efetiva de cada linha sob demanda
 * ({@link PrioridadeEfetiva#calcular}, {@code agora} vindo do
 * {@link Clock} injetado, nunca cacheado) e ordena decrescente -- sem
 * paginação, com desempate por {@code occurredAt} (achado do code review --
 * Patch 5) para a resposta da API não flutuar de ordem entre requisições
 * quando duas linhas empatam em Prioridade Efetiva (nem
 * {@code FilaRepositorio#listarTodas()} nem o {@code Comparator} original
 * garantiam uma ordem estável nesse caso), seguido do desempate residual de
 * AD-5 por {@code numeroSequencialTriagem} ascendente, nulls-last (Story
 * 3.2b1): quando Prioridade Efetiva E {@code occurredAt} empatam também,
 * vence a Triagem mais antiga (menor número sequencial); um item sem o dado
 * (origem sem o campo, Boundaries da spec 3.2b1) nunca quebra a ordenação
 * nem é excluído -- só perde esse desempate final.
 *
 * <p>{@code synchronized} em volta de "checar vazia + bootstrapar"
 * (achado do code review -- Patch 2): sem essa guarda, requisições
 * concorrentes a {@code GET /v1/fila} durante o boot a frio (ex.:
 * health-check e tráfego real chegando juntos) podiam todas observar a
 * réplica vazia e disparar {@code bootstrapar()} em paralelo, multiplicando
 * chamadas a {@code triagem-score-service} e upserts redundantes. Um lock
 * dedicado (não o objeto {@code this}, para não competir com nenhum outro
 * uso do monitor desta instância) serializa só essa checagem -- a thread
 * que entra depois de um bootstrap concluído por outra vê
 * {@code estaVazia() == false} e não dispara um segundo. Suficiente para
 * concorrência dentro da MESMA instância JVM -- coordenação entre múltiplas
 * instâncias ECS é fora de escopo (deploy ainda adiado, Boundaries da spec
 * 3.1c). A leitura/ordenação em si (depois do bloco sincronizado) não fica
 * serializada.
 *
 * <p>{@link ScoreBootstrapIndisponivelException} lançada por
 * {@link ScoreBootstrap#bootstrapar()} propaga sem tratamento -- não é
 * capturada aqui de propósito, para chegar até
 * {@code infrastructure/web} e virar {@code 503} RFC 7807.
 *
 * <p>Story 3-3b2b: exclui da fila todo {@code pacienteId} com
 * {@code Alocacao} ATIVA ({@link AlocacaoConsultaRepositorio
 * #pacientesComAlocacaoAtiva()}, porto de leitura da Story 3-3b2a) -- um
 * Paciente já alocado a um Recurso não deve mais aparecer disputando a fila
 * nem a Sugestão de Matching ({@code ConsultarSugestaoRecurso} reutiliza
 * este {@code consultar()}, consequência desejada). O {@code Set} é lido
 * 1x por chamada (nunca cacheado, nunca dentro do predicado) e o
 * {@code .filter} entra no stream ANTES do {@code .map}/{@code .sorted} --
 * não participa do cálculo de Prioridade Efetiva nem do desempate, só
 * decide quem chega a ser calculado/ordenado. {@code Set} vazio (nenhuma
 * Alocação ativa no sistema) preserva 100% do comportamento pré-existente.
 */
public class ConsultarFilaPriorizada {

    private final FilaRepositorio filaRepositorio;
    private final ScoreBootstrap scoreBootstrap;
    private final PrioridadeEfetiva prioridadeEfetiva;
    private final Clock clock;
    private final AlocacaoConsultaRepositorio alocacaoConsultaRepositorio;
    private final Object bootstrapLock = new Object();

    public ConsultarFilaPriorizada(FilaRepositorio filaRepositorio, ScoreBootstrap scoreBootstrap,
                                    PrioridadeEfetiva prioridadeEfetiva, Clock clock,
                                    AlocacaoConsultaRepositorio alocacaoConsultaRepositorio) {
        this.filaRepositorio = filaRepositorio;
        this.scoreBootstrap = scoreBootstrap;
        this.prioridadeEfetiva = prioridadeEfetiva;
        this.clock = clock;
        this.alocacaoConsultaRepositorio = alocacaoConsultaRepositorio;
    }

    public List<ItemFila> consultar() {
        synchronized (bootstrapLock) {
            if (filaRepositorio.estaVazia()) {
                // Boot a frio (I/O Matrix da spec 3.1c) -- sincrono, ANTES
                // de ler a replica. Single-flight: uma thread que espera o
                // lock aqui, apos outra ja ter bootstrapado, ve
                // estaVazia() == false e nao dispara de novo.
                scoreBootstrap.bootstrapar();
            }
        }

        Instant agora = clock.instant();
        Set<Long> pacientesComAlocacaoAtiva = alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva();
        return filaRepositorio.listarTodas().stream()
                .filter(replica -> !pacientesComAlocacaoAtiva.contains(replica.getPacienteId()))
                .map(replica -> ItemFila.de(replica, prioridadeEfetiva.calcular(replica, agora)))
                .sorted(Comparator.comparingDouble(ItemFila::prioridadeEfetiva).reversed()
                        .thenComparing(ItemFila::occurredAt)
                        .thenComparing(ItemFila::numeroSequencialTriagem,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public record ItemFila(long pacienteId, int score, Instant occurredAt, double prioridadeEfetiva,
                            Long numeroSequencialTriagem) {

        static ItemFila de(ScoreReplica replica, double prioridadeEfetiva) {
            return new ItemFila(replica.getPacienteId(), replica.getScore(), replica.getOccurredAt(),
                    prioridadeEfetiva, replica.getNumeroSequencialTriagem());
        }
    }
}
