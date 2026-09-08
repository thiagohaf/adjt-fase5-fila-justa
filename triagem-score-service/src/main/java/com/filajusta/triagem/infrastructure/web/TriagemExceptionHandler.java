package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.application.query.TriagemNaoEncontradaException;
import com.filajusta.triagem.domain.CorrelationIdInvalidoException;
import com.filajusta.triagem.domain.CpfInvalidoException;
import com.filajusta.triagem.domain.GravidadeInvalidaException;
import com.filajusta.triagem.domain.SinalVitalInvalidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduz as excecoes de {@code POST /v1/triagens} e
 * {@code GET /v1/triagens/{id}} em RFC 7807 (Boundaries da spec 2.1/2.2 --
 * todo erro em RFC 7807, nomeando o primeiro campo invalido encontrado, ou o
 * id, conforme o caso). Nenhum destes handlers calcula Score: as excecoes
 * sao lancadas por {@code Cpf}/{@code SinaisVitais}/{@code GravidadePercebida}
 * (domain) antes de {@code CalculadorDeScore} ser chamado.
 *
 * <p>{@link CpfInvalidoException} e {@link GravidadeInvalidaException} ->
 * {@code 400}, propriedade {@code campo} fixa ({@code cpf} /
 * {@code gravidadePercebida}).
 *
 * <p>{@link SinalVitalInvalidoException} -> {@code 400}, propriedade
 * {@code campo} dinamica (o sinal vital ofensivo).
 *
 * <p>{@link CorrelationIdInvalidoException} -> {@code 400}, propriedade
 * {@code campo} fixa ({@code correlationId}): header {@code X-Correlation-Id}
 * maior que o limite persistivel em {@code eventos_outbox.correlation_id}
 * (achado do code review da Story 3.0).
 *
 * <p>{@link HttpMessageNotReadableException} -> {@code 400}: corpo ausente
 * ou JSON malformado tambem e requisicao invalida, nao deve escapar como o
 * 400 default (nao-RFC-7807) do Spring.
 *
 * <p>{@link TriagemNaoEncontradaException} -> {@code 404}: id numerico
 * valido sem Triagem correspondente (Boundaries da spec 2.2).
 *
 * <p>{@link MethodArgumentTypeMismatchException} -> {@code 400}: id nao
 * numerico no path de {@code GET /v1/triagens/{id}} nao deve escapar como
 * {@code 500} generico (Boundaries da spec 2.2).
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

    @ExceptionHandler(CorrelationIdInvalidoException.class)
    ProblemDetail handleCorrelationIdInvalido(CorrelationIdInvalidoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Correlation ID invalido");
        problem.setProperty("campo", "correlationId");
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

    @ExceptionHandler(TriagemNaoEncontradaException.class)
    ProblemDetail handleTriagemNaoEncontrada(TriagemNaoEncontradaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Triagem nao encontrada");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleIdInvalido(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Parametro '" + ex.getName() + "' invalido: '" + ex.getValue() + "'");
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
