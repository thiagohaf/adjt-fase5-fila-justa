package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.query.FilaRepositorio;
import com.filajusta.matching.domain.ScoreReplica;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adapter que implementa a porta {@link FilaRepositorio} (application/query,
 * Story 3.1c) reaproveitando {@link ScoreReplicaJpaRepository#count()} e
 * {@link ScoreReplicaJpaRepository#findAll()} -- ambos herdados de
 * {@code JpaRepository}, sem nenhuma query nova (Code Map da spec 3.1c).
 *
 * <p>{@code @Transactional(readOnly = true)} aqui, nunca no caso de uso
 * ({@code ConsultarFilaPriorizada}): o bootstrap a frio dispara escritas
 * (upsert) numa transação própria por linha (ver
 * {@code ScoreReplicaRepositorioAdapter#upsertSeMaisRecente}) -- envolver
 * a consulta inteira numa transação externa {@code readOnly} faria o
 * Postgres rejeitar essas escritas quando elas participassem da mesma
 * transação (propagação padrão {@code REQUIRED}).
 */
@Component
class FilaRepositorioAdapter implements FilaRepositorio {

    private final ScoreReplicaJpaRepository jpaRepository;

    FilaRepositorioAdapter(ScoreReplicaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaVazia() {
        return jpaRepository.count() == 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoreReplica> listarTodas() {
        return jpaRepository.findAll().stream()
                .map(FilaRepositorioAdapter::paraDominio)
                .toList();
    }

    private static ScoreReplica paraDominio(ScoreReplicaJpaEntity entidade) {
        return new ScoreReplica(entidade.getPacienteId(), entidade.getScore(), entidade.getOccurredAt(),
                entidade.getEventId(), entidade.getUpdatedAt());
    }
}
