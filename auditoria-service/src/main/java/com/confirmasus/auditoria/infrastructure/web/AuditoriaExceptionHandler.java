package com.confirmasus.auditoria.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler para a camada REST do auditoria-service.
 *
 * <p>Mapeia exceções de domínio e aplicação para respostas HTTP apropriadas.
 * Todos os erros retornam um corpo JSON estruturado com timestamp, status e mensagem.
 */
@RestControllerAdvice
public class AuditoriaExceptionHandler {

    /**
     * Manipula IllegalArgumentException lançadas pelas regras de negócio.
     *
     * <p>Exemplos: DecisaoAuditoria null, pacienteId/agendamentoId inválido.
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
     * Manipula exceções genéricas nãao tratadas.
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
        Map<Object, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "Erro ao processar requisição");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
