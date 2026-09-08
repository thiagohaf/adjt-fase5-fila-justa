package com.filajusta.triagem.application.query;

/**
 * Excecao lancada por {@link ConsultarTriagem} quando nao existe Triagem
 * persistida para o id informado -- Boundaries da spec 2.2 exige {@code 404}
 * RFC 7807 nomeando o id, entao a mensagem carrega o id ofensivo (mirror de
 * {@code CredencialInvalidaException} do auth-service, mesmo padrao
 * porta/excecao/caso de uso em {@code application/query}).
 */
public class TriagemNaoEncontradaException extends RuntimeException {

    public TriagemNaoEncontradaException(Long id) {
        super("Triagem nao encontrada: " + id);
    }
}
