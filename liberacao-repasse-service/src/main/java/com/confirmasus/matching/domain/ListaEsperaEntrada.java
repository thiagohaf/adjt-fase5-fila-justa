package com.confirmasus.matching.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada de Lista de Espera (Story 5.3) — aguardando vaga disponível no
 * Recurso. Agregado simples: pacienteId + recursoId + dataSolicitacao.
 * Idempotência por UNIQUE(paciente_id, recurso_id).
 */
public final class ListaEsperaEntrada {

    private final Long id;
    private final Long pacienteId;
    private final UUID recursoId;
    private final Instant dataSolicitacao;
    private final Instant criadoEm;

    public ListaEsperaEntrada(Long id, Long pacienteId, UUID recursoId,
                              Instant dataSolicitacao, Instant criadoEm) {
        this.id = id;
        this.pacienteId = Objects.requireNonNull(pacienteId, "pacienteId");
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId");
        this.dataSolicitacao = Objects.requireNonNull(dataSolicitacao, "dataSolicitacao");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
    }

    public static ListaEsperaEntrada nova(Long pacienteId, UUID recursoId,
                                          Instant dataSolicitacao, Instant agora) {
        return new ListaEsperaEntrada(null, pacienteId, recursoId, dataSolicitacao, agora);
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public Instant getDataSolicitacao() {
        return dataSolicitacao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListaEsperaEntrada that)) return false;
        return Objects.equals(pacienteId, that.pacienteId) &&
               Objects.equals(recursoId, that.recursoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pacienteId, recursoId);
    }
}
