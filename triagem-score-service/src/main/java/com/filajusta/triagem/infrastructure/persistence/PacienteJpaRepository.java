package com.filajusta.triagem.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PacienteJpaRepository extends JpaRepository<PacienteJpaEntity, Long> {

    Optional<PacienteJpaEntity> findByCpf(String cpf);
}
