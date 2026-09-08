package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.domain.CpfInvalidoException;
import com.filajusta.triagem.domain.GravidadeInvalidaException;
import com.filajusta.triagem.domain.SinalVitalInvalidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as excecoes de {@code POST /v1/triagens} em RFC 7807 (Boundaries da
 * spec 2.1 -- todo erro em RFC 7807, nomeando o primeiro campo invalido
 * encontrado). Nenhum destes handlers calcula Score: as excecoes sao
 * lancadas por {@code Cpf}/{@code SinaisVitais}/{@code GravidadePercebida}
 * (domain) antes de {@code CalculadorDeScore} ser chamado.
 *
 * <p>{@link CpfInvalidoException} e {@link GravidadeInvalidaException} ->
 * {@code 400}, propriedade {@code campo} fixa ({@code cpf} /
 * {@code gravidadePercebida}).
 *
 * <p>{@link SinalVitalInvalidoException} -> {@code 400}, propriedade
 * {@code campo} dinamica (o sinal vital ofensivo).
 *
 * <p>{@link HttpMessageNotReadableException} -> {@code 400}: corpo ausente
 * ou JSON malformado tambem e requisicao invalida, nao deve escapar como o
 * 400 default (nao-RFC-7807) do Spring.
 *
 * <p>Fallback {@link Exception} -> {@code 500}: nenhum erro nao previsto
 * escapa como corpo default do Spring.
 */
@RestControllerAdvice
class TriagemExceptionHandler {

    @ExceptionHandler(CpfInvalidoException.class)
    ProblemDetail handleCpfInvalido(CpfInvalidoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("CPF invalido");
        problem.setProperty("campo", "cpf");
        return problem;
    }

    @ExceptionHandler(SinalVitalInvalidoException.class)
    ProblemDetail handleSinalVitalInvalido(SinalVitalInvalidoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Sinal vital invalido");
        problem.setProperty("campo", ex.getCampo());
        return problem;
    }

    @ExceptionHandler(GravidadeInvalidaException.class)
    ProblemDetail handleGravidadeInvalida(GravidadeInvalidaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Gravidade percebida invalida");
        problem.setProperty("campo", "gravidadePercebida");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Corpo da requisicao ausente ou ilegivel");
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
