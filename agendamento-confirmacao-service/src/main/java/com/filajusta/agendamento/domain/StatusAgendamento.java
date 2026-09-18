package com.filajusta.agendamento.domain;

/**
 * Estados do {@link Agendamento} (epic-1-context.md, Technical Decisions):
 * {@code AGUARDANDO_JANELA} -&gt; {@code AGUARDANDO_CONFIRMACAO} -&gt;
 * {@code CONFIRMADO} (terminal) ou {@code LIBERADO} (terminal). Story 1.1
 * so alcanca {@code AGUARDANDO_JANELA} (estado inicial de todo Agendamento
 * registrado) -- os demais existem aqui desde ja (Design Notes da spec 1.1)
 * para que a Story 1.2 nao precise alterar o tipo da coluna
 * {@code agendamentos.status}.
 */
public enum StatusAgendamento {
    AGUARDANDO_JANELA,
    AGUARDANDO_CONFIRMACAO,
    CONFIRMADO,
    LIBERADO
}
