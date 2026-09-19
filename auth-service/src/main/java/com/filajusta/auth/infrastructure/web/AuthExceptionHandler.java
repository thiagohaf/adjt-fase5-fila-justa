package com.filajusta.auth.infrastructure.web;

import com.filajusta.auth.application.query.CredencialInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as excecoes de {@code POST /v1/auth/login} em RFC 7807
 * (Boundaries da spec 1.2 -- "erro em RFC 7807" e universal, nao so para
 * credencial invalida).
 *
 * <p>{@link CredencialInvalidaException} -&gt; {@code 401}: a mesma excecao
 * cobre usuario inexistente e senha incorreta, resposta identica para os
 * dois casos, sem vazar qual deles ocorreu.
 *
 * <p>{@link MethodArgumentNotValidException} -&gt; {@code 400}: username/
 * password ausentes ou em branco sao requisicao invalida, nao credencial
 * invalida -- {@code 401} fica reservado para credencial que existe mas nao
 * confere.
 *
 * <p>Fallback {@link Exception} -&gt; {@code 500}: nenhum erro nao previsto
 * escapa como corpo default do Spring (nao-RFC-7807).
 */
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(CredencialInvalidaException.class)
    ProblemDetail handleCredencialInvalida(CredencialInvalidaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Credenciais invalidas");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidacaoInvalida(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "username e password sao obrigatorios");
        problem.setTitle("Requisicao invalida");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleErroInesperado(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
        problem.setTitle("Erro interno");
        return problem;
    }
}
