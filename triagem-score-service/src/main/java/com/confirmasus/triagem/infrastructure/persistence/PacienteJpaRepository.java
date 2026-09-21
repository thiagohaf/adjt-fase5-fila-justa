package com.confirmasus.triagem.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PacienteJpaRepository extends JpaRepository<PacienteJpaEntity, UUID> {
  Optional<PacienteJpaEntity> findByCpf(String cpf);
}
