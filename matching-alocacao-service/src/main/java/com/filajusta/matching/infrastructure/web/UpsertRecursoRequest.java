package com.filajusta.matching.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corpo de {@code POST /internal/recursos} (Story 3.2b2). Bean Validation
 * (diferente de {@code RegistrarTriagemRequest}, triagem-score-service, que
 * delega tudo ao domínio de propósito) -- endpoint interno simples, sem
 * exigência de ordem determinística de erro (Code Map/I/O &amp; Edge-Case
 * Matrix da spec 3.2b2).
 *
 * <p>{@code especificidadeRank} leva {@code @NotNull} além de {@code
 * @Positive}: sozinho, {@code @Positive} considera {@code null} válido
 * (Bean Validation trata ausência como responsabilidade de {@code
 * @NotNull}) -- sem ele, o campo ausente do I/O Matrix não seria rejeitado.
 */
record UpsertRecursoRequest(
        @NotBlank String codigoRecurso,
        @NotNull @Positive Integer especificidadeRank,
        @NotNull Boolean disponivel) {
}
