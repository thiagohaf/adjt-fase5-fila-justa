package com.confirmasus.auditoria.application.port;

import com.confirmasus.auditoria.domain.DecisaoAuditoria;

/**
 * Porta de persistência para a entidade DecisaoAuditoria.
 * Define o contrato entre a camada de aplicação e a infraestrutura (JPA).
 *
 * <p>Implementado por {@link com.confirmasus.auditoria.infrastructure.persistence.DecisaoAuditoriaRepositorioAdapter}.
 */
public interface DecisaoAuditoriaRepositorio {

    /**
     * Persiste uma decisão auditória na tabela {@code decisao_auditoria} (append-only).
     *
     * @param decisao a decisão a ser registrada
     * @throws org.springframework.dao.DataIntegrityViolationException se eventId já existe (dedup)
     */
    void salvar(DecisaoAuditoria decisao);

    /**
     * Busca uma decisão pelo eventId (para validação de idempotência).
     *
     * @param eventId o identificador do evento
     * @return a decisão se encontrada, null caso contrário
     */
    DecisaoAuditoria buscarPorEventId(java.util.UUID eventId);
}
