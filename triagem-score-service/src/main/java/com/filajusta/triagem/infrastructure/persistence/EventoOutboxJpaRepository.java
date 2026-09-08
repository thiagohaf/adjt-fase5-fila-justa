package com.filajusta.triagem.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface EventoOutboxJpaRepository extends JpaRepository<EventoOutboxJpaEntity, Long> {
}
