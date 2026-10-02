package com.confirmasus.matching.application.command;

import com.confirmasus.matching.application.query.AlocacaoConsultaRepositorio;
import com.confirmasus.matching.application.query.ListaEsperaEntradaConsultaRepositorio;
import com.confirmasus.matching.application.query.SugestaoRecusadaConsultaRepositorio;
import com.confirmasus.matching.domain.ListaEsperaEntrada;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Escolhe o próximo candidato de um Recurso (AD-6): Lista de Espera ordenada
 * exclusivamente por {@code criadoEm} (nunca gravidade/score), pulando quem
 * já tem Alocação ATIVA e quem já recusou esta Vaga.
 */
public class SelecionadorCandidatoFifo {

    private final ListaEsperaEntradaConsultaRepositorio listaEspera;
    private final AlocacaoConsultaRepositorio alocacoes;
    private final SugestaoRecusadaConsultaRepositorio recusadas;

    public SelecionadorCandidatoFifo(ListaEsperaEntradaConsultaRepositorio listaEspera,
                                     AlocacaoConsultaRepositorio alocacoes,
                                     SugestaoRecusadaConsultaRepositorio recusadas) {
        this.listaEspera = listaEspera;
        this.alocacoes = alocacoes;
        this.recusadas = recusadas;
    }

    public Optional<Long> proximo(UUID recursoId, long agendamentoId) {
        Set<Long> comAlocacaoAtiva = alocacoes.pacientesComAlocacaoAtiva();
        Set<Long> recusados = recusadas.recusadosPara(agendamentoId);
        for (ListaEsperaEntrada entrada : listaEspera.listarPorRecursoOrdenadoPorCriadoEm(recursoId)) {
            Long candidato = entrada.getPacienteId();
            if (!comAlocacaoAtiva.contains(candidato) && !recusados.contains(candidato)) {
                return Optional.of(candidato);
            }
        }
        return Optional.empty();
    }
}
