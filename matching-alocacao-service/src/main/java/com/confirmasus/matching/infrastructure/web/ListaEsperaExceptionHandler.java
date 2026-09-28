package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.command.EntradaJaExisteException;
import com.confirmasus.matching.domain.CpfInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;

/**
 * Traduz falhas de POST /v1/lista-espera para RFC 7807.
 */
@RestControllerAdvice
class ListaEsperaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaExceptionHandler.class);

    @ExceptionHandler(EntradaJaExisteException.class)
    ProblemDetail handleEntradaJaExiste(EntradaJaExisteException ex) {
        log.info("Entrada de Lista de Espera já existe -- POST /v1/lista-espera respondendo 409");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Entrada já existe");
        return problem;
    }

    @ExceptionHandler(CpfInvalidoException.class)
    ProblemDetail handleCpfInvalido(CpfInvalidoException ex) {
        log.warn("CPF inválido -- POST /v1/lista-espera respondendo 422", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
        problem.setTitle("Validação falhou");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleArgumentoIllegal(IllegalArgumentException ex) {
        log.warn("Argumento inválido -- POST /v1/lista-espera respondendo 422", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
        problem.setTitle("Validação falhou");
        return problem;
    }

    @ExceptionHandler(DateTimeParseException.class)
    ProblemDetail handleDateTimeParseException(DateTimeParseException ex) {
        log.warn("Data/hora inválida -- POST /v1/lista-espera respondendo 422", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), "Formato de data/hora inválido");
        problem.setTitle("Validação falhou");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleErroInesperado(Exception ex) {
        log.error("Erro inesperado em POST /v1/lista-espera -- respondendo 500", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
        problem.setTitle("Erro interno");
        return problem;
    }
}
