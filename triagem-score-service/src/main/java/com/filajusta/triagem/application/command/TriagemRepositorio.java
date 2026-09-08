package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.Triagem;

/**
 * Porta de saida para persistencia de {@link Triagem}. Implementada em
 * {@code infrastructure/persistence} (JPA, schema {@code triagem_score}).
 */
public interface TriagemRepositorio {

    Triagem salvar(Triagem triagem);
}
