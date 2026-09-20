package com.filajusta.triagem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eventos_outbox", schema = "triagem_score")
public class EventoOutboxJpaEntity {
  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private String payload;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  public EventoOutboxJpaEntity() {
  }

  public EventoOutboxJpaEntity(UUID eventId, String eventType, Instant occurredAt, String payload) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.occurredAt = occurredAt;
    this.payload = payload;
    this.criadoEm = Instant.now();
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public void setCriadoEm(Instant criadoEm) {
    this.criadoEm = criadoEm;
  }
}
