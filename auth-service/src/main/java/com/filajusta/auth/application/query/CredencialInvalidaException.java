package com.filajusta.auth.application.query;

/**
 * Excecao unica para qualquer credencial invalida em
 * {@link AutenticarUsuario} -- usuario inexistente e senha incorreta lancam
 * exatamente a mesma excecao, para que {@code infrastructure/web} produza a
 * mesma resposta {@code 401} em ambos os casos (nao vaza qual dos dois
 * falhou).
 */
public class CredencialInvalidaException extends RuntimeException {

    public CredencialInvalidaException() {
        super("Credenciais invalidas");
    }
}
