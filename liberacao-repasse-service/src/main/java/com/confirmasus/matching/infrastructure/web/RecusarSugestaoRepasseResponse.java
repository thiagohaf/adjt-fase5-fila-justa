package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.command.RecusarSugestaoRepasse;

import java.time.Instant;
import java.util.UUID;

/** {@code proximoPacienteId} {@code null} = Lista de Espera esgotada, Vaga sem sugestão pendente. */
record RecusarSugestaoRepasseResponse(UUID sugestaoId, Long proximoPacienteId, Instant recusadoEm) {

    static RecusarSugestaoRepasseResponse de(RecusarSugestaoRepasse.Resultado r) {
        return new RecusarSugestaoRepasseResponse(r.sugestaoId(), r.proximoPacienteId(), r.recusadoEm());
    }
}
