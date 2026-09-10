package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.ScoreReplicaRepositorio;
import com.filajusta.matching.domain.ScoreReplica;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter que implementa a porta {@link ScoreReplicaRepositorio}
 * (application/command) usando {@link ScoreReplicaJpaRepository} (Spring
 * Data, schema {@code matching_alocacao}). A decisão de last-write-wins é
 * inteiramente do UPDATE...WHERE nativo (ver
 * {@link ScoreReplicaJpaRepository#upsertSeMaisRecente}) -- este adapter só
 * traduz o domínio para os parâmetros da query.
 */
@Component
class ScoreReplicaRepositorioAdapter implements ScoreReplicaRepositorio {

    private final ScoreReplicaJpaRepository jpaRepository;

    ScoreReplicaRepositorioAdapter(ScoreReplicaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void upsertSeMaisRecente(ScoreReplica replica) {
        jpaRepository.upsertSeMaisRecente(replica.getPacienteId(), replica.getScore(),
                replica.getOccurredAt(), replica.getEventId(), replica.getUpdatedAt());
    }
}
