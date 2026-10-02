package com.confirmasus.agendamento.application.query;

import com.confirmasus.agendamento.application.command.AgendamentoRepositorio;
import com.confirmasus.agendamento.domain.Agendamento;

import java.util.List;

/**
 * Query para {@code GET /v1/agendamentos}: retorna todos os agendamentos
 * para o dashboard do regulador (visao geral por status).
 */
public class ListarAgendamentos {

    private final AgendamentoRepositorio agendamentoRepositorio;

    public ListarAgendamentos(AgendamentoRepositorio agendamentoRepositorio) {
        this.agendamentoRepositorio = agendamentoRepositorio;
    }

    public List<Agendamento> listar() {
        return agendamentoRepositorio.listarTodos();
    }
}
