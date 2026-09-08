package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.EventoOutbox;

/**
 * Porta de saida para persistencia de {@link EventoOutbox} (AD-3).
 * Implementada em {@code infrastructure/persistence} (JPA, schema
 * {@code triagem_score}, tabela {@code eventos_outbox}) -- sem publisher
 * real nesta fase.
 */
public interface EventoOutboxRepositorio {

    void salvar(EventoOutbox evento);
}
