package com.confirmasus.triagem.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato de {@code GET /internal/scores}, consumido pelo bootstrap a frio
 * da réplica de Score do matching-alocacao-service ({@code
 * TriagemScoreClient}/{@code ScoreBootstrapService}, Story 3.1c).
 *
 * <p>{@code pacienteId} aqui é numérico (long) porque é assim que o lado
 * consumidor (matching-alocacao-service, {@code ScoreReplica}) já espera --
 * mas o Paciente deste serviço é identificado por UUID internamente (CPF
 * nunca propaga além deste serviço, ver {@code Paciente} domain), e não há
 * hoje nenhum identificador numérico compartilhado entre os bounded
 * contexts para a mesma pessoa (agendamento-confirmacao-service e
 * matching-alocacao-service também resolvem Paciente por CPF de forma
 * independente, com sequências próprias). Por isso {@code pacienteId} é
 * derivado deterministicamente do UUID interno ({@code
 * ScoreInternalController#derivarPacienteIdNumerico}) -- estável para o
 * mesmo paciente entre chamadas, mas só tem significado dentro da réplica
 * de Score do matching-alocacao-service, nunca deve ser comparado com o
 * {@code pacienteId} de outro serviço.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreInternalResponse(
        @JsonProperty("pacienteId") long pacienteId,
        @JsonProperty("score") ScoreResponse score,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("numeroSequencialTriagem") Long numeroSequencialTriagem) {

    public record ScoreResponse(@JsonProperty("valor") int valor) {
    }
}
