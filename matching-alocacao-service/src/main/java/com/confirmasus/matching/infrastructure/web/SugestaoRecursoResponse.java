package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.query.ConsultarSugestaoRecurso;

import java.util.UUID;

/**
 * Corpo de {@code 200} de {@code GET /v1/recursos/{id}/sugestao}, mesmo
 * padrão de factory {@code de(...)} de {@link RecursoResponse}. {@code
 * pacienteId} vem {@code null} quando a Lista de Espera do Recurso se
 * esgota sem candidato elegível -- indica ausência de sugestão, nunca um
 * erro.
 */
record SugestaoRecursoResponse(UUID recursoId, Long pacienteId) {

    static SugestaoRecursoResponse de(ConsultarSugestaoRecurso.Resultado resultado) {
        return new SugestaoRecursoResponse(resultado.recursoId(), resultado.pacienteId());
    }
}
