package com.confirmasus.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository para ListaEsperaEntrada.
 */
public interface ListaEsperaEntradaJpaRepository extends JpaRepository<ListaEsperaEntradaJpaEntity, Long> {
    Optional<ListaEsperaEntradaJpaEntity> findByPacienteIdAndRecursoId(Long pacienteId, UUID recursoId);

    // Base da Sugestão de Repasse FIFO (AD-6): ordem de chegada pura,
    // nunca por gravidade/score.
    List<ListaEsperaEntradaJpaEntity> findByRecursoIdOrderByCriadoEmAsc(UUID recursoId);
}
