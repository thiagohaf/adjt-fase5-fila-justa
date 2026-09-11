package com.filajusta.matching.application.query;

import java.util.UUID;

/**
 * Exceção lançada por {@link ConsultarSugestaoRecurso} quando não existe
 * {@code Recurso} persistido para o {@code recursoId} informado -- Boundaries
 * da spec 3.2b3 exige {@code 404} RFC 7807 nomeando o id, entao a mensagem
 * carrega o id ofensivo (mesmo padrão de {@code TriagemNaoEncontradaException},
 * triagem-score-service).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(UUID recursoId) {
        super("Recurso nao encontrado: " + recursoId);
    }
}
