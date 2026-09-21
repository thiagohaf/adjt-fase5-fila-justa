package com.confirmasus.agendamento.infrastructure.web;

import com.confirmasus.agendamento.application.command.AgendamentoForaDaJanelaException;
import com.confirmasus.agendamento.application.command.AgendamentoNaoEncontradoException;
import com.confirmasus.agendamento.domain.CpfInvalidoException;
import com.confirmasus.agendamento.domain.DataHoraAgendamentoInvalidaException;
import com.confirmasus.agendamento.domain.RecursoIdInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduz as excecoes de {@code POST /v1/agendamentos} para RFC 7807
 * (Boundaries da spec 1.1: "Toda rejeicao de entrada invalida (CPF,
 * recursoId, dataHoraAgendamento) retorna 422 via ProblemDetail, sem
 * persistir Paciente nem Agendamento" -- mesmo formato de
 * {@code RecursosExceptionHandler}, matching-alocacao-service, mas com
 * {@code 422} em vez de {@code 400}: primeiro precedente de {@code 422} no
 * projeto).
 *
 * <p>{@link CpfInvalidoException}, {@link RecursoIdInvalidoException} e
 * {@link DataHoraAgendamentoInvalidaException} -&gt; {@code 422}, propriedade
 * {@code campo} nomeando o campo ofensivo ({@code cpf} / {@code recursoId} /
 * {@code dataHoraAgendamento}).
 *
 * <p>{@link HttpMessageNotReadableException} -&gt; {@code 422}: corpo ausente
 * ou JSON malformado tambem e entrada invalida (mesma convencao acima).
 *
 * <p>Fallback {@link Exception} -&gt; {@code 500}: nenhum erro nao previsto
 * escapa como corpo default do Spring -- logado em ERROR antes de traduzir
 * (achado do code review adversarial: sem isso um erro inesperado ficava
 * sem nenhum rastro em producao; mesmo padrao ja usado em
 * {@code FilaExceptionHandler}, matching-alocacao-service).
 */
@RestControllerAdvice
class AgendamentoExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoExceptionHandler.class);

    @ExceptionHandler(CpfInvalidoException.class)
    ProblemDetail handleCpfInvalido(CpfInvalidoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("CPF invalido");
        problem.setProperty("campo", "cpf");
        return problem;
    }

    @ExceptionHandler(RecursoIdInvalidoException.class)
    ProblemDetail handleRecursoIdInvalido(RecursoIdInvalidoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("recursoId invalido");
        problem.setProperty("campo", "recursoId");
        return problem;
    }

    @ExceptionHandler(DataHoraAgendamentoInvalidaException.class)
    ProblemDetail handleDataHoraAgendamentoInvalida(DataHoraAgendamentoInvalidaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("dataHoraAgendamento invalida");
        problem.setProperty("campo", "dataHoraAgendamento");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Corpo da requisicao ausente ou ilegivel: "
                        + "esperado JSON com cpf, recursoId e dataHoraAgendamento");
        problem.setTitle("Requisicao invalida");
        return problem;
    }

    /**
     * {@code agendamentoId} sem registro em {@code POST
     * /v1/agendamentos/{id}/confirmacao} -- {@code 404} (I/O &amp; Edge-Case
     * Matrix da spec 1.3).
     */
    @ExceptionHandler(AgendamentoNaoEncontradoException.class)
    ProblemDetail handleAgendamentoNaoEncontrado(AgendamentoNaoEncontradoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Agendamento nao encontrado");
        return problem;
    }

    /**
     * {@code agendamentoId} nao esta em {@code AGUARDANDO_CONFIRMACAO}
     * (janela ainda nao aberta ou vaga ja liberada) -- {@code 409} (spec 1.3,
     * Boundaries; molde {@code RecursosExceptionHandler
     * #handleRecursoJaAlocado}, matching-alocacao-service).
     */
    @ExceptionHandler(AgendamentoForaDaJanelaException.class)
    ProblemDetail handleAgendamentoForaDaJanela(AgendamentoForaDaJanelaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Agendamento fora da janela de confirmacao");
        return problem;
    }

    /**
     * {@code id} nao numerico em {@code POST
     * /v1/agendamentos/{id}/confirmacao} (ex.: {@code /v1/agendamentos/abc/confirmacao})
     * -- o Spring lanca {@link MethodArgumentTypeMismatchException} antes de
     * chegar no controller; sem este handler cairia no fallback generico e
     * responderia {@code 500} para uma entrada invalida (achado do code
     * review adversarial da spec 1.3).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleIdentificadorInvalido(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Identificador de agendamento invalido: " + ex.getValue());
        problem.setTitle("Identificador de agendamento invalido");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleErroInesperado(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {} {} -- respondendo 500", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
        problem.setTitle("Erro interno");
        return problem;
    }
}
