package com.filajusta.matching.infrastructure.bootstrap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Espelha só os campos de {@code GET /internal/scores} (Story 3.1a,
 * triagem-score-service -- contrato documentado no Code Map da spec 3.1c;
 * o controller em si vive numa branch separada, não presente neste
 * checkout) usados pelo bootstrap: {@code score.algoritmoVersao}/
 * {@code score.fatores} não são persistidos na réplica
 * ({@link com.filajusta.matching.domain.ScoreReplica} só guarda
 * {@code pacienteId}/{@code score}/{@code occurredAt}/{@code eventId},
 * Code Map da spec 3.1b) -- {@code @JsonIgnoreProperties} tolera esses
 * campos extras em vez de falhar o parse (e qualquer campo futuro que o
 * contrato venha a ganhar).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ScoreInternalDto(long pacienteId, ScoreDto score, Instant occurredAt, UUID eventId) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ScoreDto(int valor) {
    }
}
