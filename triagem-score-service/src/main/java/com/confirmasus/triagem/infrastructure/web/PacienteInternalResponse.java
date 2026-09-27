package com.confirmasus.triagem.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record PacienteInternalResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("nome") String nome,
        @JsonProperty("cpf") String cpf,
        @JsonProperty("tipoPaciente") String tipoPaciente
) {
}
