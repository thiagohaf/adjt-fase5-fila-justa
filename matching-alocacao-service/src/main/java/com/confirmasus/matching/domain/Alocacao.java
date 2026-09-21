package com.confirmasus.matching.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Alocação (Story 3-3b1): registro de confirmação de uma Sugestão de
 * Matching -- criada por {@code ConfirmarAlocacao} (application/command) a
 * partir de {@code POST /v1/recursos/{id}/alocacoes}.
 *
 * <p>{@code status} tem 2 valores possíveis: {@code "ATIVA"} (criado por
 * {@code ConfirmarAlocacao}) e, desde a Story 3-4b1, {@code "LIBERADA"} --
 * transição única e terminal ({@code ATIVA -> LIBERADA}), aplicada por
 * {@code LiberarRecurso} via {@code AlocacaoRepositorio#liberar} (update
 * condicional idempotente). {@code alocacaoId} é gerado pela aplicação
 * (UUID v4) antes da persistência; ao contrário de {@link Recurso}, não há
 * "candidato descartado": um INSERT ou cria a linha com este id, ou falha
 * inteiro por violação de um dos 2 índices únicos parciais (Boundaries da
 * spec 3-3b1).
 */
public final class Alocacao {

    public static final String STATUS_ATIVA = "ATIVA";

    /**
     * Estado terminal atingido por {@code LiberarRecurso} (Story 3-4b1) --
     * transição {@code ATIVA -> LIBERADA} via
     * {@code AlocacaoRepositorio#liberar}, update condicional idempotente
     * (nunca a partir de outro estado que não {@code ATIVA}).
     */
    public static final String STATUS_LIBERADA = "LIBERADA";

    private final UUID alocacaoId;
    private final UUID recursoId;
    private final long pacienteId;
    private final String status;
    private final Instant confirmadoEm;

    public Alocacao(UUID alocacaoId, UUID recursoId, long pacienteId, String status, Instant confirmadoEm) {
        this.alocacaoId = Objects.requireNonNull(alocacaoId, "alocacaoId");
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId");
        if (pacienteId <= 0) {
            throw new IllegalArgumentException("pacienteId deve ser positivo (>= 1): " + pacienteId);
        }
        this.pacienteId = pacienteId;
        this.status = Objects.requireNonNull(status, "status");
        if (status.isBlank()) {
            throw new IllegalArgumentException("status nao pode ser em branco");
        }
        this.confirmadoEm = Objects.requireNonNull(confirmadoEm, "confirmadoEm");
    }

    public UUID getAlocacaoId() {
        return alocacaoId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public long getPacienteId() {
        return pacienteId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getConfirmadoEm() {
        return confirmadoEm;
    }
}
