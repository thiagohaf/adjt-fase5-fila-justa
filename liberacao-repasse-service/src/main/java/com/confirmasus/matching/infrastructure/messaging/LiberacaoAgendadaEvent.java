package com.confirmasus.matching.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Envelope de desserialização da mensagem SQS da fila standard
 * {@code liberacao-agendada} (Story 3-4b2, consumidor). Deserializa o corpo
 * da mensagem publicada por {@code LiberacaoAgendadaRelayJob} (Story 3-4a2)
 * com campos: {@code alocacaoId}, {@code recursoId}, {@code correlationId},
 * {@code version}, {@code occurredAt}.
 *
 * <p>Record imutável com desserialização flexível (Jackson): campos extras
 * (additive schema versioning) são ignorados; campos ausentes causam
 * desserialização falha. {@code version} é obrigatório para validação de
 * compatibilidade no consumidor (Story 3-4b2, Boundaries: rejeitar major
 * version incompatível para DLQ).
 */
public record LiberacaoAgendadaEvent(
        @JsonProperty("alocacaoId") UUID alocacaoId,
        @JsonProperty("recursoId") UUID recursoId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("version") int version,
        @JsonProperty("occurredAt") Instant occurredAt) {
}
