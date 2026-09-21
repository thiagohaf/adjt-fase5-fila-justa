package com.confirmasus.auditoria.infrastructure.persistence;

import com.confirmasus.auditoria.domain.TipoDecisao;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA que mapeia a tabela {@code auditoria.decisao_auditoria}.
 * Mantém o padrão append-only: nunca é alterada pós-criação.
 *
 * <p>Constraint UNIQUE em {@code eventId} garante idempotência:
 * tentativas de inserir o mesmo {@code eventId} duas vezes resultam em
 * DataIntegrityViolationException, que é capturada no consumer job
 * como "dedup idempotente".
 */
@Entity
@Table(name = "decisao_auditoria", schema = "auditoria",
       indexes = {
           @Index(name = "idx_decisao_auditoria_evento_id", columnList = "evento_id", unique = true),
           @Index(name = "idx_decisao_auditoria_agendamento_id", columnList = "agendamento_id"),
           @Index(name = "idx_decisao_auditoria_paciente_id", columnList = "paciente_id"),
           @Index(name = "idx_decisao_auditoria_tipo_decisao", columnList = "tipo_decisao"),
           @Index(name = "idx_decisao_auditoria_timestamp", columnList = "timestamp")
       })
class DecisaoAuditoriaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "agendamento_id")
    private Long agendamentoId;

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_decisao", nullable = false, length = 50)
    private TipoDecisao tipoDecisao;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "payload_bruto", columnDefinition = "JSONB")
    private String payloadBruto;

    // ========== Construtores ==========

    protected DecisaoAuditoriaJpaEntity() {
        // Construtor sem argumentos obrigatório para JPA
    }

    public DecisaoAuditoriaJpaEntity(UUID eventId,
                                    Long agendamentoId,
                                    Long pacienteId,
                                    TipoDecisao tipoDecisao,
                                    String motivo,
                                    Instant timestamp,
                                    Instant criadoEm,
                                    String payloadBruto) {
        this.eventId = eventId;
        this.agendamentoId = agendamentoId;
        this.pacienteId = pacienteId;
        this.tipoDecisao = tipoDecisao;
        this.motivo = motivo;
        this.timestamp = timestamp;
        this.criadoEm = criadoEm;
        this.payloadBruto = payloadBruto;
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

    public String getPayloadBruto() {
        return payloadBruto;
    }
}
