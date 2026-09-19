package com.filajusta.matching.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre as invariantes de {@link EventoOutbox} (Story 3-3a) -- mesmo teste
 * de {@code triagem-score-service/.../domain/EventoOutboxTest.java} (Story
 * 3.0), adaptado ao pacote deste serviço. O envelope publicado por
 * {@code RelaySnsPublisherJob} depende de {@code version}/
 * {@code correlationId} nunca faltarem/serem invalidos.
 */
class EventoOutboxTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-11T12:00:00Z");
    private static final Map<String, Object> PAYLOAD = Map.of("recursoId", "leito-42");

    @Test
    void aceitaEventoValidoComIdNuloAntesDaPersistencia() {
        EventoOutbox evento = new EventoOutbox(
                null, EVENT_ID, "AlocacaoConfirmada", OCCURRED_AT, 1, "corr-1", PAYLOAD);

        assertThat(evento.getId()).isNull();
        assertThat(evento.getEventId()).isEqualTo(EVENT_ID);
        assertThat(evento.getEventType()).isEqualTo("AlocacaoConfirmada");
        assertThat(evento.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(evento.getVersion()).isEqualTo(1);
        assertThat(evento.getCorrelationId()).isEqualTo("corr-1");
        assertThat(evento.getPayload()).isEqualTo(PAYLOAD);
    }

    @Test
    void comIdRetornaCopiaComIdPreenchidoMantendoOResto() {
        EventoOutbox semId = new EventoOutbox(null, EVENT_ID, "AlocacaoConfirmada", OCCURRED_AT, 1, "corr-1", PAYLOAD);

        EventoOutbox comId = semId.comId(99L);

        assertThat(comId.getId()).isEqualTo(99L);
        assertThat(comId.getEventId()).isEqualTo(EVENT_ID);
        assertThat(comId.getEventType()).isEqualTo(semId.getEventType());
        assertThat(comId.getOccurredAt()).isEqualTo(semId.getOccurredAt());
        assertThat(comId.getVersion()).isEqualTo(semId.getVersion());
        assertThat(comId.getCorrelationId()).isEqualTo(semId.getCorrelationId());
        assertThat(comId.getPayload()).isEqualTo(semId.getPayload());
    }

    @Test
    void rejeitaEventIdNulo() {
        assertThatThrownBy(() -> new EventoOutbox(null, null, "AlocacaoConfirmada", OCCURRED_AT, 1, "corr-1", PAYLOAD))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejeitaEventTypeNulo() {
        assertThatThrownBy(() -> new EventoOutbox(null, EVENT_ID, null, OCCURRED_AT, 1, "corr-1", PAYLOAD))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejeitaOccurredAtNulo() {
        assertThatThrownBy(() -> new EventoOutbox(null, EVENT_ID, "AlocacaoConfirmada", null, 1, "corr-1", PAYLOAD))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejeitaVersionMenorQueUm() {
        assertThatThrownBy(() -> new EventoOutbox(null, EVENT_ID, "AlocacaoConfirmada", OCCURRED_AT, 0, "corr-1", PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejeitaCorrelationIdNulo() {
        assertThatThrownBy(() -> new EventoOutbox(null, EVENT_ID, "AlocacaoConfirmada", OCCURRED_AT, 1, null, PAYLOAD))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejeitaCorrelationIdEmBranco() {
        assertThatThrownBy(() -> new EventoOutbox(null, EVENT_ID, "AlocacaoConfirmada", OCCURRED_AT, 1, "   ", PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void rejeitaPayloadNulo() {
        assertThatThrownBy(() -> new EventoOutbox(null, EVENT_ID, "AlocacaoConfirmada", OCCURRED_AT, 1, "corr-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void payloadEImutavel() {
        EventoOutbox evento = new EventoOutbox(null, EVENT_ID, "AlocacaoConfirmada", OCCURRED_AT, 1, "corr-1", PAYLOAD);

        assertThatThrownBy(() -> evento.getPayload().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
