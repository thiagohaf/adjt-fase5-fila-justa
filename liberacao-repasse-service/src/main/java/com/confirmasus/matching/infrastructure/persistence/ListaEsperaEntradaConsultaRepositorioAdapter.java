package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.query.ListaEsperaEntradaConsultaRepositorio;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link ListaEsperaEntradaConsultaRepositorio}
 * (application/query) usando {@link ListaEsperaEntradaJpaRepository}
 * (Spring Data, schema {@code matching_alocacao}).
 */
@Component
class ListaEsperaEntradaConsultaRepositorioAdapter implements ListaEsperaEntradaConsultaRepositorio {

    private final ListaEsperaEntradaJpaRepository jpaRepository;

    ListaEsperaEntradaConsultaRepositorioAdapter(ListaEsperaEntradaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListaEsperaEntrada> listarPorRecursoOrdenadoPorCriadoEm(UUID recursoId) {
        return jpaRepository.findByRecursoIdOrderByCriadoEmAsc(recursoId).stream()
                .map(entity -> new ListaEsperaEntrada(
                        entity.getEntradaId(),
                        entity.getPacienteId(),
                        entity.getRecursoId(),
                        entity.getDataSolicitacao(),
                        entity.getCriadoEm()))
                .toList();
    }
}
