package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.domain.Recurso;

import java.util.UUID;

/**
 * Corpo de {@code 200}/{@code 201} de {@code POST /internal/recursos} e
 * {@code POST /v1/recursos} (Story 3.2b2, expandido em Story 5.1), mesmo
 * padrão de factory {@code de(...)} de {@code ScoreAtualResponse}
 * (triagem-score-service).
 */
record RecursoResponse(UUID recursoId, String codigoRecurso, int especificidadeRank, boolean disponivel,
                       String especialidade, String unidade) {

    static RecursoResponse de(Recurso recurso) {
        return new RecursoResponse(recurso.getRecursoId(), recurso.getCodigoRecurso(),
                recurso.getEspecificidadeRank(), recurso.isDisponivel(),
                recurso.getEspecialidade(), recurso.getUnidade());
    }
}
