package com.filajusta.triagem.application.query;

import com.filajusta.triagem.domain.Score;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Projecao de leitura de "Score atual" (Story 3.1a, {@code GET
 * /internal/scores}): um {@link Score} tal como publicado no evento {@code
 * ScoreCalculado} de origem -- {@code eventId} e {@code occurredAt} sao os
 * do proprio evento (nao de {@code Triagem}, que nao carrega {@code
 * eventId}), consumidos pelo {@code matching-alocacao-service} para popular
 * sua replica local em boot a frio (Stories 3.1b/3.1c).
 *
 * <p>Uma linha por evento {@code ScoreCalculado} publicado (equivalente a
 * uma por Triagem registrada) -- sem deduplicacao por {@code pacienteId}: um
 * paciente com N Triagens aparece N vezes (Boundaries da spec 3.1a: "Retorna
 * todos os Scores atuais", sem qualificador de "mais recente por
 * paciente"). Vive em {@code application/query} (nao {@code domain}) por ser
 * um modelo de leitura, nao um agregado de dominio.
 */
public record ScoreAtual(Long pacienteId, Score score, Instant occurredAt, UUID eventId) {

    public ScoreAtual {
        Objects.requireNonNull(pacienteId, "pacienteId");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(eventId, "eventId");
    }
}
