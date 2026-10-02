package com.confirmasus.agendamento.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PacienteInternalResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("nome") String nome,
        @JsonProperty("cpf") String cpf,
        @JsonProperty("tipoPaciente") String tipoPaciente
) {
}
