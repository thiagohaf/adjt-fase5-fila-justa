package com.confirmasus.auditoria.infrastructure.web;

import java.util.List;

/**
 * DTO wrapper de resposta paginada para endpoints de auditoria com filtros (Story 4.3).
 *
 * <p>Encapsula o array de decisões junto com metadados de paginação:
 * {@code total} (total de registros que satisfazem os filtros),
 * {@code limit} (quantidade de registros retornados nesta página),
 * {@code offset} (posição do primeiro registro nesta página).
 *
 * <p>Usado em:
 * <ul>
 *   <li>GET {@code /v1/auditoria/paciente/{id}?startDate=...&endDate=...&tipoDecisao=...&limit=...&offset=...}
 *   <li>GET {@code /v1/auditoria/agendamento/{id}?startDate=...&endDate=...&tipoDecisao=...&limit=...&offset=...}
 * </ul>
 */
record AuditoriaPaginatedResponse(
        List<DecisaoAuditoriaResponse> items,
        long total,
        int limit,
        int offset
) {

    /**
     * Factory method para construir resposta paginada.
     *
     * @param items lista de decisões auditáveis (pode ser vazia)
     * @param total quantidade total de registros que satisfazem os filtros
     * @param limit quantidade de registros retornados nesta página
     * @param offset posição inicial dos registros nesta página
     * @return resposta paginada
     * @throws IllegalArgumentException se limit ou offset são negativos
     */
    public static AuditoriaPaginatedResponse of(List<DecisaoAuditoriaResponse> items,
                                                  long total,
                                                  int limit,
                                                  int offset) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit não pode ser negativo");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset não pode ser negativo");
        }
        return new AuditoriaPaginatedResponse(items != null ? items : List.of(), total, limit, offset);
    }
}
