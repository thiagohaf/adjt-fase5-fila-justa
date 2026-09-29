package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.domain.ListaEsperaEntrada;

import java.time.Instant;
import java.util.UUID;

record CriarEntradaListaEsperaResponse(
    Long pacienteId,
    UUID recursoId,
    Instant dataSolicitacao,
    Instant criadoEm
) {
    static CriarEntradaListaEsperaResponse de(ListaEsperaEntrada entrada) {
        return new CriarEntradaListaEsperaResponse(
                entrada.getPacienteId(),
                entrada.getRecursoId(),
                entrada.getDataSolicitacao(),
                entrada.getCriadoEm()
        );
    }
}
