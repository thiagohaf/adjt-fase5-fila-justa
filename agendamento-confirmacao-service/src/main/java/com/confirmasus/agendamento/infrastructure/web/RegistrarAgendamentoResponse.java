package com.confirmasus.agendamento.infrastructure.web;

import com.confirmasus.agendamento.domain.Agendamento;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class RegistrarAgendamentoResponse {
    @JsonProperty("id")
    private final Long id;

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

    @JsonProperty("janelaAbreEm")
    private final Instant janelaAbreEm;

    @JsonProperty("janelaExpiraEm")
    private final Instant janelaExpiraEm;

    @JsonProperty("motivoLiberacao")
    private final String motivoLiberacao;

    public RegistrarAgendamentoResponse(Long id, String agendamentoId, Long pacienteId, String recursoId,
                                          Instant dataHoraAgendamento, String status, Instant criadoEm,
                                          Instant janelaAbreEm, Instant janelaExpiraEm, String motivoLiberacao) {
        this.id = id;
        this.agendamentoId = agendamentoId;
        this.pacienteId = pacienteId;
        this.recursoId = recursoId;
        this.dataHoraAgendamento = dataHoraAgendamento;
        this.status = status;
        this.criadoEm = criadoEm;
        this.janelaAbreEm = janelaAbreEm;
        this.janelaExpiraEm = janelaExpiraEm;
        this.motivoLiberacao = motivoLiberacao;
    }

    public static RegistrarAgendamentoResponse de(Agendamento agendamento) {
        return new RegistrarAgendamentoResponse(
                agendamento.getId(),
                agendamento.getAgendamentoId().toString(),
                agendamento.getPacienteId(),
                agendamento.getRecursoId().toString(),
                agendamento.getDataHoraAgendamento(),
                agendamento.getStatus().name(),
                agendamento.getCriadoEm(),
                agendamento.getJanelaAbreEm(),
                agendamento.getJanelaExpiraEm(),
                agendamento.getMotivoLiberacao());
    }

    public Long getId() {
        return id;
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

    public Instant getJanelaAbreEm() {
        return janelaAbreEm;
    }

    public Instant getJanelaExpiraEm() {
        return janelaExpiraEm;
    }

    public String getMotivoLiberacao() {
        return motivoLiberacao;
    }
}
