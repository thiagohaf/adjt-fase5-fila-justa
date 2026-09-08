package com.filajusta.triagem.domain;

/**
 * {@code gravidadePercebida} ausente ou fora do conjunto
 * {LEVE, MODERADA, GRAVE, CRITICA} (Design Notes da spec 2.1). Traduzida
 * para {@code 400} RFC 7807 nomeando o campo {@code gravidadePercebida}.
 */
public class GravidadeInvalidaException extends RuntimeException {

    public GravidadeInvalidaException(String mensagem) {
        super(mensagem);
    }
}
