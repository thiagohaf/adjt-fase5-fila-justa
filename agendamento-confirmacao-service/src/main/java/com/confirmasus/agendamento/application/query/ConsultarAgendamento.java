package com.confirmasus.agendamento.application.query;

import com.confirmasus.agendamento.application.command.AgendamentoNaoEncontradoException;
import com.confirmasus.agendamento.application.command.AgendamentoRepositorio;
import com.confirmasus.agendamento.domain.Agendamento;

/**
 * Query para {@code GET /v1/agendamentos/{id}} (FE-1): retorna os detalhes
 * de um agendamento pelo ID para o frontend exibir durante a confirmação.
 */
public class ConsultarAgendamento {

    private final AgendamentoRepositorio agendamentoRepositorio;

    public ConsultarAgendamento(AgendamentoRepositorio agendamentoRepositorio) {
        this.agendamentoRepositorio = agendamentoRepositorio;
    }

    public Agendamento consultar(Long id) {
        return agendamentoRepositorio.buscarPorId(id)
                .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
    }
}
