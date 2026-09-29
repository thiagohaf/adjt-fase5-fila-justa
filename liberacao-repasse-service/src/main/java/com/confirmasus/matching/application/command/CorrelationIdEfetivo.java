package com.confirmasus.matching.application.command;

import java.util.UUID;

/**
 * Regra única de {@code correlationId} dos comandos: ausente/em branco vira
 * UUID v4 local (nunca nulo no outbox); acima de 128 caracteres
 * ({@code eventos_outbox.correlation_id} é VARCHAR(128)) vira {@code 400}.
 */
final class CorrelationIdEfetivo {

    private static final int MAX_LENGTH = 128;

    private CorrelationIdEfetivo() {
    }

    static String de(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (correlationId.length() > MAX_LENGTH) {
            throw new CorrelationIdInvalidoException(
                    "correlationId excede o limite de " + MAX_LENGTH + " caracteres");
        }
        return correlationId;
    }
}
