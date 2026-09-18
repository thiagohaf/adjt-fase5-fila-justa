package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;

/**
 * Porta de saida para persistencia de {@link Agendamento}. Implementada em
 * {@code infrastructure/persistence} (JPA, schema
 * {@code agendamento_confirmacao}).
 */
public interface AgendamentoRepositorio {

    Agendamento salvar(Agendamento agendamento);
}
