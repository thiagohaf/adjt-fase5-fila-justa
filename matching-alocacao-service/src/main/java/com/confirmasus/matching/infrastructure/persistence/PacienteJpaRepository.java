package com.confirmasus.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository para Paciente.
 */
public interface PacienteJpaRepository extends JpaRepository<PacienteJpaEntity, Long> {
    Optional<PacienteJpaEntity> findByCpf(String cpf);
}
