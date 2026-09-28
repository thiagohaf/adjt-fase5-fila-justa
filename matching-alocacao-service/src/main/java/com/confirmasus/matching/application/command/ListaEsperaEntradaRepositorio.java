package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.ListaEsperaEntrada;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para busca/persistência de ListaEsperaEntrada.
 */
public interface ListaEsperaEntradaRepositorio {

    Optional<ListaEsperaEntrada> buscarPorPacienteIdERecursoId(Long pacienteId, UUID recursoId);

    ListaEsperaEntrada salvar(ListaEsperaEntrada entrada);
}
