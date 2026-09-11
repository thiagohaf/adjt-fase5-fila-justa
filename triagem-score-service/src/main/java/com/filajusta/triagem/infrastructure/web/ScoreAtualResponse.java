package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.application.query.ScoreAtual;
import com.filajusta.triagem.domain.FatorContribuinte;
import com.filajusta.triagem.domain.Score;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Item de {@code 200} de {@code GET /internal/scores} (Story 3.1a):
 * {@code pacienteId}, Score completo (valor, versao, fatores), {@code
 * occurredAt} e {@code eventId} do evento {@code ScoreCalculado} de origem
 * (I/O & Edge-Case Matrix da spec 3.1a). Mesmo padrao de DTO aninhado de
 * {@code ConsultarTriagemResponse}/{@code RegistrarTriagemResponse}.
 *
 * <p>{@code numeroSequencialTriagem} (Story 3.2a) = {@code triagemId} do
 * evento de origem -- consumido pelo {@code matching-alocacao-service}
 * (Story 3.2b) para desempate residual entre Pacientes com Prioridade
 * Efetiva e {@code occurredAt} identicos (AD-5).
 */
record ScoreAtualResponse(
        Long pacienteId, ScoreResponse score, Instant occurredAt, UUID eventId, Long numeroSequencialTriagem) {

    static ScoreAtualResponse de(ScoreAtual scoreAtual) {
        return new ScoreAtualResponse(
                scoreAtual.pacienteId(),
                ScoreResponse.de(scoreAtual.score()),
                scoreAtual.occurredAt(),
                scoreAtual.eventId(),
                scoreAtual.numeroSequencialTriagem());
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
