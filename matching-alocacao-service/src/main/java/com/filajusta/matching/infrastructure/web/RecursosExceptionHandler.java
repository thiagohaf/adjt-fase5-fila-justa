package com.filajusta.matching.infrastructure.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz falhas de Bean Validation de {@code POST /internal/recursos}
 * ({@code UpsertRecursoRequest} inválido) para RFC 7807 (Story 3.2b2, mesmo
 * padrão de {@code AuthExceptionHandler}, auth-service).
 *
 * <p>Necessário apesar de {@code @Valid} já causar {@code
 * MethodArgumentNotValidException} sozinho: {@code @RestControllerAdvice} é
 * global no contexto Spring (não escopado por controller), e o resolvedor
 * de exceções do Spring escolhe, entre TODOS os advice beans registrados, o
 * handler cujo tipo de exceção é mais específico -- sem um handler
 * dedicado a {@link MethodArgumentNotValidException} aqui, o único
 * candidato era o fallback genérico {@code Exception -> 500} de {@code
 * FilaExceptionHandler} (Story 3.1c), mascarando um {@code 400} como
 * {@code 500} (achado ao rodar a I/O &amp; Edge-Case Matrix da spec 3.2b2).
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)}: o resolvedor de exceções do Spring
 * consulta os beans {@code @ControllerAdvice} nessa ordem e usa o PRIMEIRO
 * que tiver QUALQUER handler aplicável -- não compara especificidade entre
 * beans diferentes, só dentro de um mesmo bean. Sem {@code @Order} aqui,
 * {@code FilaExceptionHandler} (sem ordem explícita, {@code
 * LOWEST_PRECEDENCE}) podia ser consultado primeiro e "vencer" trivialmente
 * pelo seu fallback {@code Exception}, mesmo esse handler aqui sendo mais
 * específico -- exatamente o bug observado antes deste {@code @Order}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class RecursosExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidacaoInvalida(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "codigoRecurso, especificidadeRank e disponivel sao obrigatorios "
                        + "e especificidadeRank deve ser positivo");
        problem.setTitle("Requisicao invalida");
        return problem;
    }

    /**
     * Traduz corpo JSON malformado/ilegivel (JSON invalido, tipo de campo
     * errado como {@code "especificidadeRank": "abc"}, ou corpo ausente) de
     * {@code POST /internal/recursos} para {@code 400}. Sem este handler, o
     * {@code HttpMessageConverter} lanca {@link HttpMessageNotReadableException}
     * ANTES do Bean Validation rodar -- {@code
     * MethodArgumentNotValidException} nunca eh lancada nesses casos --
     * entao caia no fallback generico {@code Exception -> 500} de {@code
     * FilaExceptionHandler}, mascarando um erro de entrada do cliente como
     * erro interno (achado no code review multi-agente da Story 3.2b2).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Corpo da requisicao ausente ou ilegivel: "
                        + "esperado JSON com codigoRecurso, especificidadeRank e disponivel");
        problem.setTitle("Requisicao invalida");
        return problem;
    }
}
