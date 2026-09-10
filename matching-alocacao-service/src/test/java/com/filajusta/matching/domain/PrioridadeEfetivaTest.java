package com.filajusta.matching.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Cobre {@link PrioridadeEfetiva#calcular} -- {@code score + min(k *
 * max(0, horas_espera), teto)} (Boundaries da spec 3.1c). Cobre a I/O &amp;
 * Edge-Case Matrix inteira relativa ao cálculo puro de Aging, sem banco/
 * Clock real (Tasks da spec: "Testes unitários de PrioridadeEfetiva (teto,
 * horas_espera negativa)").
 */
class PrioridadeEfetivaTest {

    // teto=20 atingido as 18h de espera (ponto medio da faixa 12-24h,
    // epic-3-context.md) -- k = teto / 18.
    private static final double K = 20.0 / 18.0;
    private static final double TETO = 20.0;

    private final PrioridadeEfetiva prioridadeEfetiva = new PrioridadeEfetiva(K, TETO);

    private static ScoreReplica replicaComOccurredAt(int score, Instant occurredAt) {
        return new ScoreReplica(1L, score, occurredAt, UUID.randomUUID(), Instant.now());
    }

    @Test
    void consultaNormalSomaScoreEAgingProporcionalAsHorasDeEspera() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Instant occurredAt = Instant.parse("2026-09-08T09:00:00Z"); // 9h de espera
        ScoreReplica replica = replicaComOccurredAt(50, occurredAt);

        double prioridade = prioridadeEfetiva.calcular(replica, agora);

        assertThat(prioridade).isCloseTo(50 + K * 9, within(1e-9));
    }

    @Test
    void tetoDeAgingAtingidoComOccurredAt24HorasAtrasNaoUltrapassaScoreMaisTeto() {
        // I/O Matrix: "occurredAt >= 18h atras -- Prioridade Efetiva nao
        // ultrapassa score + 20".
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Instant occurredAt24h = agora.minusSeconds(24 * 3600);
        ScoreReplica replica = replicaComOccurredAt(60, occurredAt24h);

        double prioridade = prioridadeEfetiva.calcular(replica, agora);

        assertThat(prioridade).isEqualTo(60 + TETO);
    }

    @Test
    void horasDeEsperaExatamente18HorasAtingeOTeto() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Instant occurredAt18h = agora.minusSeconds(18 * 3600);
        ScoreReplica replica = replicaComOccurredAt(40, occurredAt18h);

        double prioridade = prioridadeEfetiva.calcular(replica, agora);

        assertThat(prioridade).isCloseTo(40 + TETO, within(1e-9));
    }

    @Test
    void defasagemDeRelogioComOccurredAtNoFuturoTrataHorasDeEsperaComoZero() {
        // I/O Matrix: "occurredAt no futuro (horas_espera negativa) --
        // horas_espera tratada como 0, Prioridade Efetiva nunca cai abaixo
        // do Score".
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Instant occurredAtNoFuturo = agora.plusSeconds(3600);
        ScoreReplica replica = replicaComOccurredAt(45, occurredAtNoFuturo);

        double prioridade = prioridadeEfetiva.calcular(replica, agora);

        assertThat(prioridade).isEqualTo(45.0);
    }

    @Test
    void semEsperaAgingEZeroPrioridadeEfetivaIgualAoScore() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        ScoreReplica replica = replicaComOccurredAt(88, agora);

        assertThat(prioridadeEfetiva.calcular(replica, agora)).isEqualTo(88.0);
    }

    @Test
    void construtorRejeitaKNegativo() {
        assertThatThrownBy(() -> new PrioridadeEfetiva(-0.1, TETO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorRejeitaTetoNegativo() {
        assertThatThrownBy(() -> new PrioridadeEfetiva(K, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calcularRejeitaArgumentosNulos() {
        ScoreReplica replica = replicaComOccurredAt(10, Instant.now());

        assertThatThrownBy(() -> prioridadeEfetiva.calcular(null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> prioridadeEfetiva.calcular(replica, null))
                .isInstanceOf(NullPointerException.class);
    }
}
