package com.filajusta.matching.application.command;

/**
 * {@code X-Correlation-Id} maior que o limite persistível em
 * {@code eventos_outbox.correlation_id} ({@code VARCHAR(128)}, migration
 * {@code V4__create_eventos_outbox.sql}) -- mesmo padrão de
 * {@code CorrelationIdInvalidoException} do {@code triagem-score-service}
 * (Story 3.0). Traduzida para {@code 400} RFC 7807 por
 * {@code RecursosExceptionHandler} (infrastructure/web).
 */
public class CorrelationIdInvalidoException extends RuntimeException {

    public CorrelationIdInvalidoException(String mensagem) {
        super(mensagem);
    }
}
