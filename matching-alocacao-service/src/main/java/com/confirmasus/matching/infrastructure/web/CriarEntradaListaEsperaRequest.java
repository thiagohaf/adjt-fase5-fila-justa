package com.confirmasus.matching.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record CriarEntradaListaEsperaRequest(
    @NotBlank String cpf,
    @NotBlank String recursoId,
    @NotNull String dataSolicitacao
) {}
