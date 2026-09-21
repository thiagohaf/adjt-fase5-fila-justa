package com.confirmasus.agendamento.domain;

/**
 * {@code recursoId} nulo, vazio ou sem formato de UUID valido (Boundaries
 * da spec 1.1: {@code recursoId} e validado apenas por formato, nunca contra
 * o catalogo real de Recurso, que vive em {@code liberacao-repasse-service},
 * AD-1). Traduzida para {@code 422} RFC 7807 pelo
 * {@code AgendamentoExceptionHandler} (infrastructure/web), nomeando o
 * campo {@code recursoId}.
 */
public class RecursoIdInvalidoException extends RuntimeException {

    public RecursoIdInvalidoException(String mensagem) {
        super(mensagem);
    }
}
