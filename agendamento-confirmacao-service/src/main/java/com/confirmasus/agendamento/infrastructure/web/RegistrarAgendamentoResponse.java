package com.confirmasus.agendamento.infrastructure.web;

import com.confirmasus.agendamento.domain.Agendamento;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class RegistrarAgendamentoResponse {
    @JsonProperty("agendamentoId")
    private final String agendamentoId;

    @JsonProperty("pacienteId")
    private final Long pacienteId;

    @JsonProperty("recursoId")
    private final String recursoId;

    @JsonProperty("dataHoraAgendamento")
    private final Instant dataHoraAgendamento;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("criadoEm")
    private final Instant criadoEm;

    public RegistrarAgendamentoResponse(String agendamentoId, Long pacienteId, String recursoId,
                                          Instant dataHoraAgendamento, String status, Instant criadoEm) {
        this.agendamentoId = agendamentoId;
        this.pacienteId = pacienteId;
        this.recursoId = recursoId;
        this.dataHoraAgendamento = dataHoraAgendamento;
        this.status = status;
        this.criadoEm = criadoEm;
    }

    public static RegistrarAgendamentoResponse de(Agendamento agendamento) {
        return new RegistrarAgendamentoResponse(
                agendamento.getAgendamentoId().toString(),
                agendamento.getPacienteId(),
                agendamento.getRecursoId().toString(),
                agendamento.getDataHoraAgendamento(),
                agendamento.getStatus().name(),
                agendamento.getCriadoEm());
    }

    public String getAgendamentoId() {
        return agendamentoId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getRecursoId() {
        return recursoId;
    }

    public Instant getDataHoraAgendamento() {
        return dataHoraAgendamento;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
