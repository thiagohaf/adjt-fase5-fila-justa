package com.confirmasus.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository para ListaEsperaEntrada.
 */
public interface ListaEsperaEntradaJpaRepository extends JpaRepository<ListaEsperaEntradaJpaEntity, Long> {
    Optional<ListaEsperaEntradaJpaEntity> findByPacienteIdAndRecursoId(Long pacienteId, UUID recursoId);
}
