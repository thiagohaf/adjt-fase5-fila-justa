package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.query.RecursoConsultaRepositorio;
import com.confirmasus.matching.domain.Recurso;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link RecursoConsultaRepositorio}
 * (application/query) usando {@link RecursoJpaRepository} (Spring Data,
 * schema {@code matching_alocacao}) -- {@code findById} herdado de {@code
 * JpaRepository}. Irmã de leitura de {@link RecursoRepositorioAdapter}
 * (que só faz upsert).
 */
@Component
class RecursoConsultaRepositorioAdapter implements RecursoConsultaRepositorio {

    private final RecursoJpaRepository jpaRepository;

    RecursoConsultaRepositorioAdapter(RecursoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recurso> buscarPorId(UUID recursoId) {
        return jpaRepository.findById(recursoId).map(RecursoJpaEntity::paraDominio);
    }
}
