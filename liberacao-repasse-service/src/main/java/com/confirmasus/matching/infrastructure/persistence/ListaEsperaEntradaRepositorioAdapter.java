package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.command.ListaEsperaEntradaRepositorio;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA para ListaEsperaEntradaRepositorio (porta de saída).
 */
@Repository
public class ListaEsperaEntradaRepositorioAdapter implements ListaEsperaEntradaRepositorio {

    private final ListaEsperaEntradaJpaRepository jpaRepository;

    public ListaEsperaEntradaRepositorioAdapter(ListaEsperaEntradaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ListaEsperaEntrada> buscarPorPacienteIdERecursoId(Long pacienteId, UUID recursoId) {
        return jpaRepository.findByPacienteIdAndRecursoId(pacienteId, recursoId)
                .map(entity -> new ListaEsperaEntrada(
                        entity.getEntradaId(),
                        entity.getPacienteId(),
                        entity.getRecursoId(),
                        entity.getDataSolicitacao(),
                        entity.getCriadoEm()
                ));
    }

    @Override
    public ListaEsperaEntrada salvar(ListaEsperaEntrada entrada) {
        ListaEsperaEntradaJpaEntity entity = new ListaEsperaEntradaJpaEntity(
                entrada.getPacienteId(),
                entrada.getRecursoId(),
                entrada.getDataSolicitacao(),
                entrada.getCriadoEm()
        );
        ListaEsperaEntradaJpaEntity salva = jpaRepository.save(entity);
        return new ListaEsperaEntrada(
                salva.getEntradaId(),
                salva.getPacienteId(),
                salva.getRecursoId(),
                salva.getDataSolicitacao(),
                salva.getCriadoEm()
        );
    }
}
