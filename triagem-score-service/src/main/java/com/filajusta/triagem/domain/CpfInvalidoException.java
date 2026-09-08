package com.filajusta.triagem.domain;

/**
 * CPF com formato ou digito verificador invalido (Boundaries da spec 2.1:
 * "CPF validado (formato/checksum) ... antes de qualquer calculo de Score").
 * Traduzida para {@code 400} RFC 7807 pelo {@code TriagemExceptionHandler}
 * (infrastructure/web), nomeando o campo {@code cpf}.
 */
public class CpfInvalidoException extends RuntimeException {

    public CpfInvalidoException(String mensagem) {
        super(mensagem);
    }
}
