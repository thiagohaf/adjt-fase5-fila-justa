package com.confirmasus.auditoria.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link DecisaoAuditoriaJpaEntity}.
 * Define métodos de acesso à tabela {@code auditoria.decisao_auditoria}.
 *
 * <p>A constraint UNIQUE em {@code evento_id} é respeitada pelo banco --
 * tentativas de inserir duplicatas resultam em DataIntegrityViolationException.
 */
@Repository
public interface DecisaoAuditoriaJpaRepository extends JpaRepository<DecisaoAuditoriaJpaEntity, Long> {

    /**
     * Busca uma decisão pelo eventId.
     *
     * @param eventId o UUID do evento
     * @return Optional com a entidade se encontrada
     */
    @Query("SELECT d FROM DecisaoAuditoriaJpaEntity d WHERE d.eventId = :eventId")
    Optional<DecisaoAuditoriaJpaEntity> findByEventId(@Param("eventId") UUID eventId);
}
