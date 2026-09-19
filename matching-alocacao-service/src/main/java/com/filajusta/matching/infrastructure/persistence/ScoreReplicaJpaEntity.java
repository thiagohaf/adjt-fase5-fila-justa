package com.filajusta.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code matching_alocacao.score_replica} (Story 3.1b,
 * AD-9). Só usado para leitura via {@link ScoreReplicaJpaRepository}
 * (herdada de {@code JpaRepository}, ex.: {@code findById} em testes) --
 * a escrita de produção passa pelo upsert nativo
 * ({@code ScoreReplicaJpaRepository#upsertSeMaisRecente}), não por
 * {@code save()} desta entidade.
 */
@Entity
@Table(name = "score_replica", schema = "matching_alocacao")
public class ScoreReplicaJpaEntity {

    @Id
    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(nullable = false)
    private int score;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "numero_sequencial_triagem")
    private Long numeroSequencialTriagem;

    protected ScoreReplicaJpaEntity() {
        // Exigido pelo JPA.
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public int getScore() {
        return score;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getNumeroSequencialTriagem() {
        return numeroSequencialTriagem;
    }
}
