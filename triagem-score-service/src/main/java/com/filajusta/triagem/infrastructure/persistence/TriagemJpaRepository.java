package com.filajusta.triagem.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface TriagemJpaRepository extends JpaRepository<TriagemJpaEntity, Long> {
}
