package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.domain.FatorContribuinte;
import com.filajusta.triagem.domain.Score;
import com.filajusta.triagem.domain.SinaisVitais;
import com.filajusta.triagem.domain.Triagem;

import java.time.Instant;
import java.util.List;

/**
 * Resposta {@code 200} de {@code GET /v1/triagens/{id}} (FR-3, Story 2.2).
 * Nunca inclui CPF em texto claro (Boundaries da spec 2.2) -- so
 * {@code pacienteId}, mesma fronteira de {@code RegistrarTriagemResponse}
 * (Story 2.1). Score e fatores sao os persistidos no registro original,
 * nunca recalculados.
 */
record ConsultarTriagemResponse(
        Long triagemId,
        Long pacienteId,
        SinaisVitaisResponse sinaisVitais,
        String gravidadePercebida,
        List<String> sintomas,
        ScoreResponse score,
        Instant criadoEm) {

    static ConsultarTriagemResponse de(Triagem triagem) {
        return new ConsultarTriagemResponse(
                triagem.getId(),
                triagem.getPacienteId(),
                SinaisVitaisResponse.de(triagem.getSinaisVitais()),
                triagem.getGravidadePercebida().name(),
                triagem.getSintomas(),
                ScoreResponse.de(triagem.getScore()),
                triagem.getCriadoEm());
    }

    record SinaisVitaisResponse(
            double frequenciaCardiaca,
            double pressaoArterialSistolica,
            double pressaoArterialDiastolica,
            double saturacaoOxigenio,
            double frequenciaRespiratoria,
            double temperatura) {

        static SinaisVitaisResponse de(SinaisVitais sinaisVitais) {
            return new SinaisVitaisResponse(
                    sinaisVitais.getFrequenciaCardiaca(),
                    sinaisVitais.getPressaoArterialSistolica(),
                    sinaisVitais.getPressaoArterialDiastolica(),
                    sinaisVitais.getSaturacaoOxigenio(),
                    sinaisVitais.getFrequenciaRespiratoria(),
                    sinaisVitais.getTemperatura());
        }
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
