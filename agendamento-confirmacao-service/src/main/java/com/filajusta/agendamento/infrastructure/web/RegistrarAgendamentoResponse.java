package com.filajusta.agendamento.infrastructure.web;

import com.filajusta.agendamento.domain.Agendamento;

import java.time.Instant;

/**
 * Resposta {@code 201} de {@code POST /v1/agendamentos}. Nunca inclui CPF
 * em texto claro (Boundaries da spec 1.1) -- so {@code pacienteId}.
 */
record RegistrarAgendamentoResponse(
        Long agendamentoId,
        Long pacienteId,
        String recursoId,
        Instant dataHoraAgendamento,
        String status,
        Instant criadoEm) {

    static RegistrarAgendamentoResponse de(Agendamento agendamento) {
        return new RegistrarAgendamentoResponse(
                agendamento.getId(),
                agendamento.getPacienteId(),
                agendamento.getRecursoId().toString(),
                agendamento.getDataHoraAgendamento(),
                agendamento.getStatus().name(),
                agendamento.getCriadoEm());
    }
}
