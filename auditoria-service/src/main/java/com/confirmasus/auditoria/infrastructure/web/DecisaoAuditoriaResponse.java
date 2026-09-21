package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta para GET {@code /v1/auditoria/paciente/{id}} e
 * GET {@code /v1/auditoria/agendamento/{id}}.
 *
 * <p>Contém todos os campos relevantes de uma decisão auditável:
 * {@code eventId}, {@code tipoDecisao}, {@code motivo} (nullable), {@code timestamp},
 * {@code criadoEm}.
 *
 * <p>Factory method {@code de(DecisaoAuditoria)} converte domínio → DTO.
 */
record DecisaoAuditoriaResponse(
        UUID eventId,
        Long agendamentoId,
        Long pacienteId,
        TipoDecisao tipoDecisao,
        String motivo,
        Instant timestamp,
        Instant criadoEm
) {

    /**
     * Converte uma decisão de domínio para DTO de resposta REST.
     *
     * <p>Factory method que garante null-safety: se {@code decisao} for null, lança IllegalArgumentException.
     *
     * @param decisao a decisão de domínio (não pode ser null)
     * @return DTO pronto para serialização JSON
     * @throws IllegalArgumentException se decisao for null
     */
    static DecisaoAuditoriaResponse de(DecisaoAuditoria decisao) {
        if (decisao == null) {
            throw new IllegalArgumentException("DecisaoAuditoria não pode ser null");
        }
        return new DecisaoAuditoriaResponse(
                decisao.getEventId(),
                decisao.getAgendamentoId(),
                decisao.getPacienteId(),
                decisao.getTipoDecisao(),
                decisao.getMotivo(),
                decisao.getTimestamp(),
                decisao.getCriadoEm()
        );
    }
}
