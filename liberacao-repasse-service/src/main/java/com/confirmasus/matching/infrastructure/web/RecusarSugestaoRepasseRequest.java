package com.confirmasus.matching.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /v1/sugestoes-repasse/{id}/recusa}: motivo obrigatório. */
record RecusarSugestaoRepasseRequest(@NotBlank String motivo) {
}
