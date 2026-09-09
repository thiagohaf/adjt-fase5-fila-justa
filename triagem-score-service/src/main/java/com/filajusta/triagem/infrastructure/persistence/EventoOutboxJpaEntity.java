package com.filajusta.triagem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code triagem_score.eventos_outbox} (AD-3, AD-9).
 * {@code version}/{@code correlationId}/{@code publicadoEm} adicionados na
 * Story 3.0 (relay real para SNS FIFO, migration
 * {@code V2__add_relay_columns_eventos_outbox.sql}) -- {@code publicadoEm}
 * comeca {@code null} e so e preenchido por
 * {@code EventoOutboxJpaRepository#marcarPublicado} apos confirmacao do SNS.
 */
@Entity
@Table(name = "eventos_outbox", schema = "triagem_score")
public class EventoOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private int version;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    protected EventoOutboxJpaEntity() {
        // Exigido pelo JPA.
    }

    public EventoOutboxJpaEntity(UUID eventId, String eventType, Instant occurredAt, int version,
                                  String correlationId, String payloadJson) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.version = version;
        this.correlationId = correlationId;
        this.payload = payloadJson;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public int getVersion() {
        return version;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getPublicadoEm() {
        return publicadoEm;
    }

    public String getPayload() {
        return payload;
    }
}
