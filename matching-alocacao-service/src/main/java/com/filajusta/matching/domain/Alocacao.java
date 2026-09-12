package com.filajusta.matching.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Alocação (Story 3-3b1): registro de confirmação de uma Sugestão de
 * Matching -- criada por {@code ConfirmarAlocacao} (application/command) a
 * partir de {@code POST /v1/recursos/{id}/alocacoes}.
 *
 * <p>{@code status} tem um único valor possível nesta fase ({@code "ATIVA"})
 * -- não existe ainda transição para outro estado (liberação automática é a
 * Story 3.4, fora de escopo). {@code alocacaoId} é gerado pela aplicação
 * (UUID v4) antes da persistência; ao contrário de {@link Recurso}, não há
 * "candidato descartado": um INSERT ou cria a linha com este id, ou falha
 * inteiro por violação de um dos 2 índices únicos parciais (Boundaries da
 * spec 3-3b1).
 */
public final class Alocacao {

    public static final String STATUS_ATIVA = "ATIVA";

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
