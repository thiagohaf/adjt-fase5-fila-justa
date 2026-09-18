package com.filajusta.agendamento.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AgendamentoJpaRepository extends JpaRepository<AgendamentoJpaEntity, Long> {
}
