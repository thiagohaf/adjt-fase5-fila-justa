package com.filajusta.triagem.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha da tabela outbox (AD-3): {@code eventId} (UUID v4, gerado em
 * {@code application/command} no momento do comando -- nunca regenerado por
 * um relay futuro), {@code eventType}, {@code occurredAt}, {@code version}
 * (envelope fixado no Epic 2, sempre {@code 1} nesta fase),
 * {@code correlationId} (nunca nulo -- propagado do header
 * {@code X-Correlation-Id} ou gerado localmente, ver {@code RegistrarTriagem})
 * e {@code payload}. Gravada na mesma transacao local de
 * {@code RegistrarTriagem}. {@code id} e {@code null} antes da primeira
 * persistencia (mesmo padrao de {@link Triagem}) -- preenchido via
 * {@link #comId(Long)} pelo adapter ao ler linhas pendentes para o relay
 * (Story 3.0, {@code RelaySnsPublisherJob}).
 */
public final class EventoOutbox {

    private final Long id;
    private final UUID eventId;
    private final String eventType;
    private final Instant occurredAt;
    private final int version;
    private final String correlationId;
    private final Map<String, Object> payload;

    public EventoOutbox(Long id,
                         UUID eventId,
                         String eventType,
                         Instant occurredAt,
                         int version,
                         String correlationId,
                         Map<String, Object> payload) {
        this.id = id;
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if (version < 1) {
            throw new IllegalArgumentException("version deve ser >= 1");
        }
        this.version = version;
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId nao pode ser em branco");
        }
        this.payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    }

    /** Retorna uma copia deste EventoOutbox com {@code id} preenchido (pos-persistencia). */
    public EventoOutbox comId(Long idPersistido) {
        return new EventoOutbox(idPersistido, eventId, eventType, occurredAt, version, correlationId, payload);
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

    public Map<String, Object> getPayload() {
        return payload;
    }
}
