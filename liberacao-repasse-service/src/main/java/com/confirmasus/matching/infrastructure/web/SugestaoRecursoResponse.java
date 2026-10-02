package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.query.ConsultarSugestaoRecurso;

import java.util.UUID;

/**
 * Corpo de {@code 200} de {@code GET /v1/recursos/{id}/sugestao}: {@code
 * sugestaoId}/{@code pacienteId} vêm {@code null} quando não há Sugestão de
 * Repasse pendente para o Recurso.
 */
record SugestaoRecursoResponse(UUID recursoId, UUID sugestaoId, Long pacienteId, String situacao) {

    static SugestaoRecursoResponse de(ConsultarSugestaoRecurso.Resultado resultado) {
        return new SugestaoRecursoResponse(resultado.recursoId(), resultado.sugestaoId(), resultado.pacienteId(),
                resultado.situacao());
    }
}
