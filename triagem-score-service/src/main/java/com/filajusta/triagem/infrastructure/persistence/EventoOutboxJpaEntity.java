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
 * Mapeamento JPA de {@code triagem_score.eventos_outbox} (AD-3, AD-9). Sem
 * publisher/relay real nesta fase (Design Notes da spec 2.1) -- so grava.
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    protected EventoOutboxJpaEntity() {
        // Exigido pelo JPA.
    }

    public EventoOutboxJpaEntity(UUID eventId, String eventType, Instant occurredAt, String payloadJson) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.payload = payloadJson;
    }
}
