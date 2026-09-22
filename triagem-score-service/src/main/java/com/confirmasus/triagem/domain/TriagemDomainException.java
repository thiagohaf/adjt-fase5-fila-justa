package com.confirmasus.triagem.domain;

/**
 * Exceção base para erros de domínio na Triagem.
 */
public class TriagemDomainException extends RuntimeException {
  public TriagemDomainException(String message) {
    super(message);
  }

  public TriagemDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
