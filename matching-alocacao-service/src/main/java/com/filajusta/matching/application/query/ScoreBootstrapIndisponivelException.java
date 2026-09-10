package com.filajusta.matching.application.query;

/**
 * Lançada por {@link ScoreBootstrap#bootstrapar()} quando
 * {@code triagem-score-service} está indisponível ou responde com erro ao
 * chamar {@code GET /internal/scores} (Boundaries da spec 3.1c: "Falha no
 * bootstrap retorna 503 (RFC 7807)"). Runtime -- não há recuperação
 * possível no meio da consulta a {@code GET /v1/fila};
 * {@code infrastructure/web} ({@code FilaExceptionHandler}) traduz para
 * RFC 7807.
 */
public class ScoreBootstrapIndisponivelException extends RuntimeException {

    public ScoreBootstrapIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
