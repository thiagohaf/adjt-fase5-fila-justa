package com.confirmasus.matching.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corpo de {@code POST /internal/recursos} e {@code POST /v1/recursos}.
 * Bean Validation -- endpoint interno simples, sem exigência de ordem
 * determinística de erro.
 *
 * <p>{@code especificidadeRank} leva {@code @NotNull} além de {@code
 * @Positive}: sozinho, {@code @Positive} considera {@code null} válido
 * (Bean Validation trata ausência como responsabilidade de {@code
 * @NotNull}) -- sem ele, o campo ausente não seria rejeitado. Atributo de
 * catálogo do Recurso, não usado para priorização de paciente.
 *
 * <p>{@code especialidade} e {@code unidade} são opcionais, para suportar
 * seed-data com categorização de recursos.
 */
record UpsertRecursoRequest(
        @NotBlank String codigoRecurso,
        @NotNull @Positive Integer especificidadeRank,
        @NotNull Boolean disponivel,
        String especialidade,
        String unidade) {
}
