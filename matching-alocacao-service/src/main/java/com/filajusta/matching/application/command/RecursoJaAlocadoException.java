package com.filajusta.matching.application.command;

import java.util.UUID;

/**
 * Lançada por {@link AlocacaoRepositorio#confirmar(com.filajusta.matching.domain.Alocacao)}
 * quando o {@code recursoId} já tem uma {@code Alocacao} com {@code status="ATIVA"} --
 * mapeada do índice único parcial {@code ux_alocacao_recurso_ativa}
 * (Boundaries da spec 3-3b1). Confirmações concorrentes para o mesmo
 * Recurso: a segunda sempre recebe esta exceção -- nunca checagem em
 * memória antes do INSERT.
 */
public class RecursoJaAlocadoException extends RuntimeException {

    public RecursoJaAlocadoException(UUID recursoId) {
        super("Recurso ja possui uma Alocacao ativa: " + recursoId);
    }
}
