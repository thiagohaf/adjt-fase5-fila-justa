package com.confirmasus.triagem.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio representando um evento em tabela outbox.
 * Envelope: eventId (UUID v4), eventType, occurredAt, payload.
 * Nunca regenerado em retry (idempotência).
 */
public class EventoOutbox {
  private final UUID eventId;
  private final String eventType;
  private final Instant occurredAt;
  private final String payload;

  public EventoOutbox(UUID eventId, String eventType, Instant occurredAt, String payload) {
    Objects.requireNonNull(eventId, "eventId não pode ser nulo");
    Objects.requireNonNull(eventType, "eventType não pode ser nulo");
    Objects.requireNonNull(occurredAt, "occurredAt não pode ser nulo");
    Objects.requireNonNull(payload, "payload não pode ser nulo");

    this.eventId = eventId;
    this.eventType = eventType;
    this.occurredAt = occurredAt;
    this.payload = payload;
  }

  /**
   * Factory para criar novo evento ScoreCalculado.
   */
  public static EventoOutbox scoreCalculado(Instant occurredAt, String payload) {
    return new EventoOutbox(UUID.randomUUID(), "ScoreCalculado", occurredAt, payload);
  }

  public UUID eventId() {
    return eventId;
  }

  public String eventType() {
    return eventType;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public String payload() {
    return payload;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EventoOutbox that = (EventoOutbox) o;
    return Objects.equals(eventId, that.eventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId);
  }

  @Override
  public String toString() {
    return "EventoOutbox{" + eventType + "#" + eventId + '}';
  }
}
