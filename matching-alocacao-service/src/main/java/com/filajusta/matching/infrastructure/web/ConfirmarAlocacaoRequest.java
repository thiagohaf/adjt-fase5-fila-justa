package com.filajusta.matching.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corpo de {@code POST /v1/recursos/{id}/alocacoes} (Story 3-3b1). Bean
 * Validation, mesmo padrão de {@code UpsertRecursoRequest} -- endpoint
 * simples, sem exigência de ordem determinística de erro.
 *
 * <p>{@code @Positive} (achado do code review multi-agente): sozinho,
 * {@code @Positive} considera {@code null} válido -- {@code @NotNull} cobre
 * a ausência, {@code @Positive} rejeita zero/negativo, mesma combinação de
 * {@code UpsertRecursoRequest#especificidadeRank}.
 */
record ConfirmarAlocacaoRequest(@NotNull @Positive Long pacienteId) {
}
