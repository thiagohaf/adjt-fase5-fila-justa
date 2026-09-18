package com.filajusta.agendamento.infrastructure.web;

import com.filajusta.agendamento.domain.CpfInvalidoException;
import com.filajusta.agendamento.domain.DataHoraAgendamentoInvalidaException;
import com.filajusta.agendamento.domain.RecursoIdInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    ProblemDetail handleErroInesperado(Exception ex) {
        log.error("Erro inesperado em POST /v1/agendamentos -- respondendo 500", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
        problem.setTitle("Erro interno");
        return problem;
    }
}
