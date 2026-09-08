package com.filajusta.triagem.domain;

/**
 * {@code X-Correlation-Id} maior que o limite persistivel em
 * {@code eventos_outbox.correlation_id} ({@code VARCHAR(128)}, migration
 * {@code V2__add_relay_columns_eventos_outbox.sql}). Achado do code review
 * da Story 3.0: sem esta validacao, um header maior que 128 caracteres
 * quebrava no {@code INSERT} como {@code 500} nao controlado, em vez de
 * {@code 400} -- inconsistente com a convencao ja usada pelo servico ("400
 * no primeiro campo invalido, antes de qualquer trabalho a jusante").
 * Traduzida para {@code 400} RFC 7807 pelo {@code TriagemExceptionHandler}
 * (infrastructure/web), nomeando o campo {@code correlationId}.
 */
public class CorrelationIdInvalidoException extends RuntimeException {

    public CorrelationIdInvalidoException(String mensagem) {
        super(mensagem);
    }
}
