package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.domain.Recurso;

import java.util.UUID;

/**
 * Corpo de {@code 200}/{@code 201} de {@code POST /internal/recursos}
 * (Story 3.2b2), mesmo padrão de factory {@code de(...)} de {@code
 * ScoreAtualResponse} (triagem-score-service).
 */
record RecursoResponse(UUID recursoId, String codigoRecurso, int especificidadeRank, boolean disponivel) {

    static RecursoResponse de(Recurso recurso) {
        return new RecursoResponse(recurso.getRecursoId(), recurso.getCodigoRecurso(),
                recurso.getEspecificidadeRank(), recurso.isDisponivel());
    }
}
