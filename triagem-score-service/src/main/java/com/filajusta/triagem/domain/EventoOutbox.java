package com.filajusta.triagem.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha da tabela outbox (AD-3): {@code eventId} (UUID v4, gerado em
 * {@code application/command} no momento do comando -- nunca regenerado por
 * um relay futuro), {@code eventType}, {@code occurredAt} e {@code payload}.
 * Gravada na mesma transacao local de {@code RegistrarTriagem}; nada a le
 * ainda nesta fase (publisher/SNS deferido, ver Design Notes da spec 2.1).
 */
public final class EventoOutbox {

    private final UUID eventId;
    private final String eventType;
    private final Instant occurredAt;
    private final Map<String, Object> payload;

    public EventoOutbox(UUID eventId, String eventType, Instant occurredAt, Map<String, Object> payload) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
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

    public Map<String, Object> getPayload() {
        return payload;
    }
}
