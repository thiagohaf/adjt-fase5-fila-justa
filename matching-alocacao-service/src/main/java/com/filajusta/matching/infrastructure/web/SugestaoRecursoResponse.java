package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.query.ConsultarSugestaoRecurso;

import java.util.UUID;

/**
 * Corpo de {@code 200} de {@code GET /v1/recursos/{id}/sugestao} (Story
 * 3.2b3), mesmo padrão de factory {@code de(...)} de {@link RecursoResponse}.
 * {@code pacienteId} vem {@code null} quando a fila global se esgota antes
 * do índice {@code N} do algoritmo de tiers (I/O Matrix "FILA_ESGOTADA" da
 * spec 3.2b3) -- indica ausência de sugestão, nunca um erro. Não expõe
 * {@code numeroSequencialTriagem} (fator interno de desempate, mesmo padrão
 * de {@link FilaItemResponse}, Boundaries "Never" da spec 3.2b3).
 */
record SugestaoRecursoResponse(UUID recursoId, Long pacienteId) {

    static SugestaoRecursoResponse de(ConsultarSugestaoRecurso.Resultado resultado) {
        return new SugestaoRecursoResponse(resultado.recursoId(), resultado.pacienteId());
    }
}
