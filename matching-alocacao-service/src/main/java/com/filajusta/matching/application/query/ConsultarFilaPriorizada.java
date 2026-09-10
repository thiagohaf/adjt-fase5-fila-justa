package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.PrioridadeEfetiva;
import com.filajusta.matching.domain.ScoreReplica;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Caso de uso de {@code GET /v1/fila} (Story 3.1c): réplica vazia dispara o
 * bootstrap síncrono ({@link ScoreBootstrap#bootstrapar()}) ANTES de ler --
 * nunca responde uma fila incompleta silenciosamente (Boundaries da spec).
 * Recomputa a Prioridade Efetiva de cada linha sob demanda
 * ({@link PrioridadeEfetiva#calcular}, {@code agora} vindo do
 * {@link Clock} injetado, nunca cacheado) e ordena decrescente -- sem
 * paginação, sem desempate residual "de negócio" por Triagem/sequência
 * (Story 3.2, "Never" da spec 3.1c) -- só um desempate barato e
 * determinístico por {@code occurredAt} (achado do code review -- Patch 5)
 * para a resposta da API não flutuar de ordem entre requisições quando
 * duas linhas empatam em Prioridade Efetiva (nem
 * {@code FilaRepositorio#listarTodas()} nem o {@code Comparator} original
 * garantiam uma ordem estável nesse caso).
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
 */
public class ConsultarFilaPriorizada {

    private final FilaRepositorio filaRepositorio;
    private final ScoreBootstrap scoreBootstrap;
    private final PrioridadeEfetiva prioridadeEfetiva;
    private final Clock clock;
    private final Object bootstrapLock = new Object();

    public ConsultarFilaPriorizada(FilaRepositorio filaRepositorio, ScoreBootstrap scoreBootstrap,
                                    PrioridadeEfetiva prioridadeEfetiva, Clock clock) {
        this.filaRepositorio = filaRepositorio;
        this.scoreBootstrap = scoreBootstrap;
        this.prioridadeEfetiva = prioridadeEfetiva;
        this.clock = clock;
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
        return filaRepositorio.listarTodas().stream()
                .map(replica -> ItemFila.de(replica, prioridadeEfetiva.calcular(replica, agora)))
                .sorted(Comparator.comparingDouble(ItemFila::prioridadeEfetiva).reversed()
                        .thenComparing(ItemFila::occurredAt))
                .toList();
    }

    public record ItemFila(long pacienteId, int score, Instant occurredAt, double prioridadeEfetiva) {

        static ItemFila de(ScoreReplica replica, double prioridadeEfetiva) {
            return new ItemFila(
                    replica.getPacienteId(), replica.getScore(), replica.getOccurredAt(), prioridadeEfetiva);
        }
    }
}
