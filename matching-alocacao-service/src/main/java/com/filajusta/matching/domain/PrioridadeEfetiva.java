package com.filajusta.matching.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Cálculo puro da Prioridade Efetiva (Score + Urgência Acumulada via Aging
 * com teto, Story 3.1c, {@code epic-3-context.md}): {@code score + min(k ×
 * max(0, horas_espera), teto)}. {@code k}/{@code teto} vêm calibrados de
 * {@code filajusta.aging.k}/{@code filajusta.aging.teto} (application.yml,
 * montados no {@code @Bean} de {@code MatchingAlocacaoServiceApplication})
 * -- fonte única, nunca hardcoded aqui (mesmo padrão de
 * {@code LimitesSinaisVitais}/{@code FaixaVital} do triagem-score-service).
 *
 * <p>{@code horas_espera} = {@code agora} (passado explicitamente pelo
 * chamador, nunca {@code Instant.now()} direto -- Boundaries da spec 3.1c:
 * "sempre recomputada sob demanda... Clock injetável") menos
 * {@link ScoreReplica#getOccurredAt()}. Defasagem de relógio entre serviços
 * (I/O &amp; Edge-Case Matrix: {@code occurredAt} no futuro) produz uma
 * duração negativa -- truncada para {@code 0} aqui, nunca propagada como
 * Aging negativo: Prioridade Efetiva nunca cai abaixo do Score puro.
 *
 * <p>Sem estado, sem persistência: cada consulta a {@code GET /v1/fila}
 * recalcula do zero a partir da réplica local (Boundaries: "nunca
 * cacheada") -- múltiplas instâncias do serviço concordam por construção,
 * sem precisar sincronizar um valor calculado.
 */
public final class PrioridadeEfetiva {

    private final double k;
    private final double teto;

    public PrioridadeEfetiva(double k, double teto) {
        if (k < 0) {
            throw new IllegalArgumentException("k deve ser >= 0: " + k);
        }
        if (teto < 0) {
            throw new IllegalArgumentException("teto deve ser >= 0: " + teto);
        }
        this.k = k;
        this.teto = teto;
    }

    public double calcular(ScoreReplica replica, Instant agora) {
        Objects.requireNonNull(replica, "replica");
        Objects.requireNonNull(agora, "agora");

        double horasEspera = Duration.between(replica.getOccurredAt(), agora).toMillis() / 3_600_000.0;
        double horasEsperaNaoNegativa = Math.max(0.0, horasEspera);
        double aging = Math.min(k * horasEsperaNaoNegativa, teto);

        return replica.getScore() + aging;
    }
}
