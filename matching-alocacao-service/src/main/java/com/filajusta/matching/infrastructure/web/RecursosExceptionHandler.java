package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.command.CorrelationIdInvalidoException;
import com.filajusta.matching.application.command.PacienteJaAlocadoException;
import com.filajusta.matching.application.command.RecursoJaAlocadoException;
import com.filajusta.matching.application.query.RecursoNaoEncontradoException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

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
 *
 * <p>{@link RecursoNaoEncontradoException} -&gt; {@code 404} e {@link
 * MethodArgumentTypeMismatchException} -&gt; {@code 400} (Story 3.2b3, {@code
 * GET /v1/recursos/{id}/sugestao}) seguem o mesmo padrão de {@code
 * TriagemNaoEncontradaException}/{@code MethodArgumentTypeMismatchException}
 * em {@code TriagemExceptionHandler} (triagem-score-service).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class RecursosExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidacaoInvalida(MethodArgumentNotValidException ex) {
        // Mensagem generica por campo (Story 3-3b1, patch do code review
        // multi-agente): este handler e global (@RestControllerAdvice sem
        // escopo por controller), compartilhado por POST /internal/recursos
        // (UpsertRecursoRequest) e POST /v1/recursos/{id}/alocacoes
        // (ConfirmarAlocacaoRequest) -- construida a partir de
        // getFieldErrors() (nome do campo + mensagem padrao do Bean
        // Validation), nunca de ex.getMessage() bruto: essa mensagem padrao
        // do Spring inclui o nome tecnico do record/bean vinculado
        // (ex. "confirmarAlocacaoRequest"), vazamento de detalhe interno
        // achado no code review.
        String detalhe = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Corpo da requisicao invalido: " + detalhe);
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

    /**
     * {@code recursoId} sintaticamente válido (UUID) mas sem registro em
     * {@code GET /v1/recursos/{id}/sugestao} -- {@code 404} nomeando o id
     * (Boundaries da spec 3.2b3, mesmo padrão de {@code
     * TriagemExceptionHandler#handleTriagemNaoEncontrada}).
     */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso nao encontrado");
        return problem;
    }

    /**
     * {@code id} não-UUID no path de {@code GET /v1/recursos/{id}/sugestao}
     * não deve escapar como {@code 500} genérico (Boundaries da spec 3.2b3,
     * mesmo padrão de {@code TriagemExceptionHandler#handleIdInvalido}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleIdInvalido(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Parametro '" + ex.getName() + "' invalido: '" + ex.getValue() + "'");
        problem.setTitle("Requisicao invalida");
        return problem;
    }

    /**
     * {@code recursoId} já tem uma Alocação ativa -- {@code 409} (Story
     * 3-3b1, {@code POST /v1/recursos/{id}/alocacoes}), mapeado do índice
     * único parcial {@code ux_alocacao_recurso_ativa} por
     * {@code AlocacaoRepositorioAdapter}.
     */
    @ExceptionHandler(RecursoJaAlocadoException.class)
    ProblemDetail handleRecursoJaAlocado(RecursoJaAlocadoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Recurso ja alocado");
        return problem;
    }

    /**
     * {@code pacienteId} já tem uma Alocação ativa para outro Recurso --
     * {@code 409} (Story 3-3b1), mapeado do índice único parcial
     * {@code ux_alocacao_paciente_ativa}.
     */
    @ExceptionHandler(PacienteJaAlocadoException.class)
    ProblemDetail handlePacienteJaAlocado(PacienteJaAlocadoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Paciente ja alocado");
        return problem;
    }

    /**
     * Header {@code X-Correlation-Id} maior que o limite persistível
     * ({@code 128} caracteres) -- {@code 400} (Story 3-3b1, mesmo padrão de
     * {@code TriagemExceptionHandler#handleCorrelationIdInvalido},
     * triagem-score-service).
     */
    @ExceptionHandler(CorrelationIdInvalidoException.class)
    ProblemDetail handleCorrelationIdInvalido(CorrelationIdInvalidoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requisicao invalida");
        return problem;
    }
}
