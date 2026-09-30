package com.confirmasus.agendamento.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoOutboxValidacaoTest {

    private static final Instant QUANDO = Instant.parse("2026-09-18T12:00:00Z");

    private static EventoOutbox evento(String tipo, int versao, String correlationId) {
        return new EventoOutbox(null, UUID.randomUUID(), tipo, QUANDO, versao, correlationId, Map.of("k", "v"));
    }

    @Test
    void comIdDevolveCopiaComIdPreenchido() {
        EventoOutbox original = evento("VagaLiberada", 1, "corr-1");
        EventoOutbox persistido = original.comId(5L);
        assertThat(persistido.getId()).isEqualTo(5L);
        assertThat(persistido.getEventId()).isEqualTo(original.getEventId());
        assertThat(original.getId()).isNull();
    }

    @Test
    void rejeitaEventTypeEmBranco() {
        assertThatThrownBy(() -> evento(" ", 1, "corr")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaVersaoMenorQueUm() {
        assertThatThrownBy(() -> evento("VagaLiberada", 0, "corr")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaCorrelationIdEmBranco() {
        assertThatThrownBy(() -> evento("VagaLiberada", 1, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
