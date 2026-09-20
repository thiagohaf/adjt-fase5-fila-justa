package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.domain.TriagemDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.net.URI;
import java.time.Instant;

/**
 * Mapeia exceções de domínio para respostas RFC 7807 (Problem Detail).
 * Cada exceção é mapeada para 400 nomeando o campo/problema inválido.
 */
@RestControllerAdvice
public class TriagemExceptionHandler {
  private static final URI TRIAGEM_ERROR_TYPE = URI.create("https://filajusta.local/triagem/error");

  @ExceptionHandler(TriagemDomainException.class)
  public ResponseEntity<ProblemDetail> handleTriagemDomainException(TriagemDomainException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      ex.getMessage()
    );
    problem.setType(TRIAGEM_ERROR_TYPE);
    problem.setTitle("Erro de Domínio da Triagem");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      ex.getMessage()
    );
    problem.setType(TRIAGEM_ERROR_TYPE);
    problem.setTitle("Argumento Inválido");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
    String detail = ex.getBindingResult().getFieldErrors().stream()
      .map(e -> e.getField() + ": " + e.getDefaultMessage())
      .findFirst()
      .orElse("Erro de validação na requisição");

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      detail
    );
    problem.setType(TRIAGEM_ERROR_TYPE);
    problem.setTitle("Validação de Entrada Falhou");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "Corpo da requisição ausente ou malformado: " + ex.getMessage()
    );
    problem.setType(TRIAGEM_ERROR_TYPE);
    problem.setTitle("Corpo Inválido");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }
}
