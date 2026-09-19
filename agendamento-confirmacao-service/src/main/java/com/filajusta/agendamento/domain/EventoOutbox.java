package com.filajusta.agendamento.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha da tabela outbox (AD-3, spec 1.2) -- copia do molde de
 * {@code matching-alocacao-service/.../domain/EventoOutbox.java}:
 * {@code id, eventId} (UUID v4, gerado em {@code application/command} no
 * momento do comando -- nunca regenerado por um relay futuro),
 * {@code eventType, occurredAt, version} (envelope fixado no Epic 2, sempre
 * {@code 1} nesta fase), {@code correlationId} (nunca em branco) e
 * {@code payload}.
 *
 * <p>Unico produtor nesta story: {@code AbrirJanelaDeConfirmacao}
 * (application/command) grava uma linha {@code NotificacaoConfirmacaoPublicada}
 * na mesma transacao em que transiciona um Agendamento de
 * {@code AGUARDANDO_JANELA} para {@code AGUARDANDO_CONFIRMACAO}. {@code id}
 * e {@code null} antes da primeira persistencia -- preenchido via
 * {@link #comId(Long)} pelo adapter ao ler linhas pendentes para o relay
 * ({@code RelaySnsPublisherJob}).
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
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType nao pode ser em branco");
        }
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
