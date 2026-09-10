package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.query.ConsultarFilaPriorizada.ItemFila;

import java.time.Instant;

/**
 * Item de {@code GET /v1/fila} (Story 3.1c). {@code prioridadeEfetiva} já
 * vem recomputada sob demanda por {@code ConsultarFilaPriorizada} -- nunca
 * persistida/cacheada.
 */
record FilaItemResponse(long pacienteId, int score, Instant occurredAt, double prioridadeEfetiva) {

    static FilaItemResponse de(ItemFila item) {
        return new FilaItemResponse(item.pacienteId(), item.score(), item.occurredAt(), item.prioridadeEfetiva());
    }
}
