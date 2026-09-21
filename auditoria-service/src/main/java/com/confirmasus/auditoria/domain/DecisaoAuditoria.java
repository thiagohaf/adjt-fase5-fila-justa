package com.confirmasus.auditoria.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio que representa uma decisão auditável registrada no sistema.
 * É imutável pós-persistência e segue o padrão append-only do log de auditoria.
 *
 * <p>Campos:
 * <ul>
 *   <li>{@code id} - PK gerado pelo banco, preenchido pós-persistência
 *   <li>{@code eventId} - UUID único (UNIQUE constraint) de idempotência por evento
 *   <li>{@code agendamentoId} - ID do agendamento relacionado (nullable)
 *   <li>{@code pacienteId} - ID do paciente relacionado (nullable)
 *   <li>{@code tipoDecisao} - Enum com 9 categorias (NOTIFICACAO, CONFIRMACAO, etc.)
 *   <li>{@code motivo} - Motivo/justificativa da decisão (nullable conforme tipo)
 *   <li>{@code timestamp} - Instant em que a decisão foi tomada (vem do evento)
 *   <li>{@code criadoEm} - Instant em que o registro foi criado (agora)
 * </ul>
 *
 * <p>Idempotência: dois registros com o mesmo {@code eventId} nunca existem
 * simultaneamente -- o UNIQUE constraint garante isso.
 */
public final class DecisaoAuditoria {

    private final Long id;
    private final UUID eventId;
    private final Long agendamentoId;
    private final Long pacienteId;
    private final TipoDecisao tipoDecisao;
    private final String motivo;
    private final Instant timestamp;
    private final Instant criadoEm;

    /**
     * Construtor principal (usado pelo adapter de persistência pós-SELECT).
     */
    public DecisaoAuditoria(Long id,
                            UUID eventId,
                            Long agendamentoId,
                            Long pacienteId,
                            TipoDecisao tipoDecisao,
                            String motivo,
                            Instant timestamp,
                            Instant criadoEm) {
        this.id = id;
        this.eventId = Objects.requireNonNull(eventId, "eventId nao pode ser null");
        this.agendamentoId = agendamentoId; // nullable
        this.pacienteId = pacienteId; // nullable
        this.tipoDecisao = Objects.requireNonNull(tipoDecisao, "tipoDecisao nao pode ser null");
        this.motivo = motivo; // nullable
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp nao pode ser null");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm nao pode ser null");
    }

    /**
     * Construtor de domínio (usado pela aplicação ao processar um evento).
     * {@code id} não é passado aqui, pois é gerado pelo banco na persistência.
     */
    public static DecisaoAuditoria criar(UUID eventId,
                                         Long agendamentoId,
                                         Long pacienteId,
                                         TipoDecisao tipoDecisao,
                                         String motivo,
                                         Instant timestamp,
                                         Instant agora) {
        return new DecisaoAuditoria(null, eventId, agendamentoId, pacienteId, tipoDecisao, motivo, timestamp, agora);
    }

    // ========== Getters ==========

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getAgendamentoId() {
        return agendamentoId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public TipoDecisao getTipoDecisao() {
        return tipoDecisao;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DecisaoAuditoria that = (DecisaoAuditoria) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(eventId);
    }

    @Override
    public String toString() {
        return "DecisaoAuditoria{" +
                "id=" + id +
                ", eventId=" + eventId +
                ", agendamentoId=" + agendamentoId +
                ", pacienteId=" + pacienteId +
                ", tipoDecisao=" + tipoDecisao +
                ", motivo='" + motivo + '\'' +
                ", timestamp=" + timestamp +
                ", criadoEm=" + criadoEm +
                '}';
    }
}
