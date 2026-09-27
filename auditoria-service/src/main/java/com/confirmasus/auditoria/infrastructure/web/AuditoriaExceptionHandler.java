package com.confirmasus.auditoria.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler para a camada REST do auditoria-service.
 *
 * <p>Mapeia exceções de domínio e aplicação para respostas HTTP apropriadas.
 * Todos os erros retornam um corpo JSON estruturado com timestamp, status e mensagem.
 *
 * <p>Erros tratados:
 * <ul>
 *   <li>IllegalArgumentException: validação de negócio (ex.: filtros inválidos, Story 4.3)
 *   <li>Exception: erros genéricos não tratados
 * </ul>
 */
@RestControllerAdvice
public class AuditoriaExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuditoriaExceptionHandler.class);

    /**
     * Manipula erros de tipo de argumento (ex.: enum inválido).
     *
     * @param ex a exceção capturada
     * @param request o contexto da request
     * @return ResponseEntity com HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request
    ) {
        Map<Object, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", String.format("Parâmetro '%s' inválido: %s", ex.getName(), ex.getValue()));

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Manipula IllegalArgumentException lançadas pelas regras de negócio (Story 4.3).
     *
     * <p>Exemplos:
     * <ul>
     *   <li>DecisaoAuditoria null
     *   <li>pacienteId/agendamentoId inválido
     *   <li>startDate > endDate
     *   <li>limit > 200
     *   <li>tipoDecisao inválido
     * </ul>
     *
     * @param ex a exceção capturada
     * @param request o contexto da request
     * @return ResponseEntity com HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        Map<Object, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Manipula exceções genéricas não tratadas.
     *
     * @param ex a exceção capturada
     * @param request o contexto da request
     * @return ResponseEntity com HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(
            Exception ex,
            WebRequest request
    ) {
        logger.error("Erro não tratado", ex);
        Map<Object, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "Erro ao processar requisição");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
