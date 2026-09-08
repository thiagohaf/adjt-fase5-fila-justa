package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.domain.FatorContribuinte;
import com.filajusta.triagem.domain.Score;
import com.filajusta.triagem.domain.Triagem;

import java.time.Instant;
import java.util.List;

/**
 * Resposta {@code 201} de {@code POST /v1/triagens} (FR-1, FR-3). Nunca
 * inclui CPF em texto claro (Boundaries da spec 2.1) -- so
 * {@code pacienteId}.
 */
record RegistrarTriagemResponse(
        Long triagemId,
        Long pacienteId,
        List<String> sintomas,
        ScoreResponse score,
        Instant criadoEm) {

    static RegistrarTriagemResponse de(Triagem triagem) {
        return new RegistrarTriagemResponse(
                triagem.getId(),
                triagem.getPacienteId(),
                triagem.getSintomas(),
                ScoreResponse.de(triagem.getScore()),
                triagem.getCriadoEm());
    }

    record ScoreResponse(int valor, String algoritmoVersao, List<FatorContribuinteResponse> fatores) {

        static ScoreResponse de(Score score) {
            return new ScoreResponse(
                    score.getValor(),
                    score.getAlgoritmoVersao(),
                    score.getFatores().stream().map(FatorContribuinteResponse::de).toList());
        }
    }

    record FatorContribuinteResponse(String fator, double contribuicao) {

        static FatorContribuinteResponse de(FatorContribuinte fatorContribuinte) {
            return new FatorContribuinteResponse(fatorContribuinte.fator(), fatorContribuinte.contribuicao());
        }
    }
}
