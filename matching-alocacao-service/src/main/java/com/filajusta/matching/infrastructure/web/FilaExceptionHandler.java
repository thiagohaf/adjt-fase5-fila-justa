package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.query.ScoreBootstrapIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz falhas de {@code GET /v1/fila} para RFC 7807 (mesmo padrão de
 * {@code TriagemExceptionHandler}, triagem-score-service).
 *
 * <p>{@link ScoreBootstrapIndisponivelException} -&gt; {@code 503}:
 * bootstrap síncrono a frio falhou (triagem-score-service indisponível ou
 * com erro) -- réplica permanece vazia, nunca fila incompleta silenciosa
 * (Boundaries da spec 3.1c).
 *
 * <p>Fallback {@link Exception} -&gt; {@code 500}: nenhum erro não previsto
 * escapa como corpo default do Spring.
 *
 * <p>Ambos os handlers logam a exceção antes de traduzir (achado do code
 * review -- Patch 4): sem isso, em particular o fallback de {@code 500}
 * engolia qualquer erro inesperado sem deixar rastro nenhum pra
 * diagnosticar em produção -- mesmo padrão de logging estruturado já usado
 * no resto do serviço (ex.: {@code ScoreCalculadoConsumerJob}).
 */
@RestControllerAdvice
class FilaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FilaExceptionHandler.class);

    @ExceptionHandler(ScoreBootstrapIndisponivelException.class)
    ProblemDetail handleScoreBootstrapIndisponivel(ScoreBootstrapIndisponivelException ex) {
        log.warn("Bootstrap sincrono a frio da replica de Score falhou -- GET /v1/fila respondendo 503", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Bootstrap da replica de Score indisponivel");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleErroInesperado(Exception ex) {
        log.error("Erro inesperado em GET /v1/fila -- respondendo 500", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
        problem.setTitle("Erro interno");
        return problem;
    }
}
