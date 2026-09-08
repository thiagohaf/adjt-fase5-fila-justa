package com.filajusta.triagem.domain;

/**
 * Um sinal vital ausente ou fora da faixa fisiologica plausivel (AD-11).
 * Carrega o nome do campo ofensivo -- Boundaries da spec 2.1 exige
 * {@code 400} "no primeiro campo invalido encontrado", entao
 * {@code TriagemExceptionHandler} usa {@link #getCampo()} para nomear o
 * campo na resposta RFC 7807.
 */
public class SinalVitalInvalidoException extends RuntimeException {

    private final String campo;

    public SinalVitalInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
