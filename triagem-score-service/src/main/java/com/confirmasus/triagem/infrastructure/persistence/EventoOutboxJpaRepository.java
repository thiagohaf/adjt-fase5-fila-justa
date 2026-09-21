package com.confirmasus.triagem.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface EventoOutboxJpaRepository extends JpaRepository<EventoOutboxJpaEntity, UUID> {
}
