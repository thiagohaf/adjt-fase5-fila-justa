package com.confirmasus.matching.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sugestão de Repasse (Story 6.1, AD-6): uma por Vaga liberada
 * ({@code agendamentoId}). {@code pacienteId} é o candidato atual da Lista
 * de Espera -- {@code null} apenas em {@link #STATUS_ESGOTADA} (Lista de
 * Espera sem candidato elegível, sem sugestão pendente).
 */
public final class SugestaoRepasse {

    public static final String STATUS_PENDENTE = "PENDENTE";
    public static final String STATUS_CONFIRMADA = "CONFIRMADA";
    public static final String STATUS_ESGOTADA = "ESGOTADA";

    private final UUID sugestaoId;
    private final long agendamentoId;
    private final UUID recursoId;
    private final Long pacienteId;
    private final String status;
    private final Instant criadoEm;

    public SugestaoRepasse(UUID sugestaoId, long agendamentoId, UUID recursoId, Long pacienteId,
                           String status, Instant criadoEm) {
        this.sugestaoId = Objects.requireNonNull(sugestaoId, "sugestaoId");
        this.agendamentoId = agendamentoId;
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId");
        this.status = Objects.requireNonNull(status, "status");
        if (STATUS_ESGOTADA.equals(status) != (pacienteId == null)) {
            throw new IllegalArgumentException(
                    "pacienteId deve ser nulo se, e somente se, status = ESGOTADA: " + status);
        }
        this.pacienteId = pacienteId;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
    }

    public static SugestaoRepasse para(UUID sugestaoId, long agendamentoId, UUID recursoId,
                                       Long pacienteCandidato, Instant agora) {
        return new SugestaoRepasse(sugestaoId, agendamentoId, recursoId, pacienteCandidato,
                pacienteCandidato == null ? STATUS_ESGOTADA : STATUS_PENDENTE, agora);
    }

    public UUID getSugestaoId() {
        return sugestaoId;
    }

    public long getAgendamentoId() {
        return agendamentoId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
