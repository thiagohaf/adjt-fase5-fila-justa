package com.filajusta.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface ScoreReplicaJpaRepository extends JpaRepository<ScoreReplicaJpaEntity, Long> {

    // Upsert atomico, last-write-wins por occurred_at, desempate por
    // event_id em ordem lexicografica (Boundaries da spec 3.1b, mesma regra
    // de ScoreReplica#maisRecenteQue -- ver seu javadoc). O WHERE do DO
    // UPDATE so aplica quando a linha candidata e ESTRITAMENTE mais recente
    // que a persistida; caso contrario o INSERT ON CONFLICT vira no-op --
    // cobre redelivery (mesmo event_id, tupla igual) e mensagem fora de
    // ordem (occurred_at menor) da I/O Matrix num unico comando atomico, sem
    // read-then-write em Java (que reabriria a corrida entre duas instancias
    // do consumidor, mesmo raciocinio do FOR UPDATE SKIP LOCKED do relay da
    // Story 3.0). Comparacao de tupla (occurred_at, event_id): Postgres
    // compara UUID pelos bytes brutos, equivalente a ordem lexicografica da
    // forma textual canonica.
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.score_replica "
            + "(paciente_id, score, occurred_at, event_id, updated_at) "
            + "VALUES (:pacienteId, :score, :occurredAt, :eventId, :updatedAt) "
            + "ON CONFLICT (paciente_id) DO UPDATE SET "
            + "score = excluded.score, "
            + "occurred_at = excluded.occurred_at, "
            + "event_id = excluded.event_id, "
            + "updated_at = excluded.updated_at "
            + "WHERE (excluded.occurred_at, excluded.event_id) "
            + "> (score_replica.occurred_at, score_replica.event_id)",
            nativeQuery = true)
    void upsertSeMaisRecente(@Param("pacienteId") long pacienteId,
                              @Param("score") int score,
                              @Param("occurredAt") Instant occurredAt,
                              @Param("eventId") UUID eventId,
                              @Param("updatedAt") Instant updatedAt);
}
