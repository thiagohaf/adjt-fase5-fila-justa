package com.confirmasus.matching.application.query;

import com.confirmasus.matching.domain.ListaEsperaEntrada;

import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura de {@link ListaEsperaEntrada} -- lado de consulta do
 * CQRS lógico da arquitetura, irmã de
 * {@link com.confirmasus.matching.application.command.ListaEsperaEntradaRepositorio}
 * (que só faz busca pontual/salvar). Base da Sugestão de Repasse FIFO
 * (AD-6, Architecture Spine): a Lista de Espera de um Recurso é ordenada
 * exclusivamente por ordem de chegada, nunca por gravidade ou qualquer
 * outro critério clínico.
 */
public interface ListaEsperaEntradaConsultaRepositorio {

    /**
     * Todas as {@link ListaEsperaEntrada} de um {@code recursoId}, ordenadas
     * por {@code criadoEm} ascendente (quem chegou primeiro aparece
     * primeiro) -- lista vazia quando não há ninguém na Lista de Espera
     * daquele Recurso.
     */
    List<ListaEsperaEntrada> listarPorRecursoOrdenadoPorCriadoEm(UUID recursoId);
}
