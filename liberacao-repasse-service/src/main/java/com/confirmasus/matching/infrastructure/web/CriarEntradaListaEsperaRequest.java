package com.confirmasus.matching.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

record CriarEntradaListaEsperaRequest(
    @NotBlank String cpf,
    @NotBlank String recursoId,
    @NotBlank String dataSolicitacao
) {}
