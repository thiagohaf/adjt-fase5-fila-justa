package com.filajusta.matching.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corpo de {@code POST /v1/recursos/{id}/alocacoes/recusa} (Story 3-3c1).
 * Bean Validation, molde {@code ConfirmarAlocacaoRequest} -- {@code
 * @NotNull @Positive} em {@code pacienteId} (mesma combinação, mesma razão:
 * sozinho, {@code @Positive} considera {@code null} válido) e {@code
 * @NotBlank} em {@code motivo} (obrigatório, Boundaries da spec 3-3c1).
 */
record RecusarSugestaoRequest(@NotNull @Positive Long pacienteId, @NotBlank String motivo) {
}
