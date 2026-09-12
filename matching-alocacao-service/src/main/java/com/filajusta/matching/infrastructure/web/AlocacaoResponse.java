package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.domain.Alocacao;

import java.time.Instant;
import java.util.UUID;

/**
 * Corpo de {@code 201} de {@code POST /v1/recursos/{id}/alocacoes}
 * (Story 3-3b1), mesmo padrão de factory {@code de(...)} de
 * {@code RecursoResponse}.
 */
record AlocacaoResponse(UUID alocacaoId, UUID recursoId, long pacienteId, String status, Instant confirmadoEm) {

    static AlocacaoResponse de(Alocacao alocacao) {
        return new AlocacaoResponse(alocacao.getAlocacaoId(), alocacao.getRecursoId(),
                alocacao.getPacienteId(), alocacao.getStatus(), alocacao.getConfirmadoEm());
    }
}
