package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.query.SugestaoRecusadaConsultaRepositorio;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link SugestaoRecusadaConsultaRepositorio}
 * (application/query, Story 3-3c2a) usando {@link SugestaoRecusadaJpaRepository}
 * (Spring Data, schema {@code matching_alocacao}) -- a query nativa de
 * {@code paciente_id} por {@code recurso_id} adicionada nesta story. Irmã de
 * leitura de {@link SugestaoRecusadaRepositorioAdapter} (que só faz upsert,
 * Story 3-3c1), mesmo split de {@link AlocacaoRepositorioAdapter} vs
 * {@link AlocacaoConsultaRepositorioAdapter}.
 */
@Component
class SugestaoRecusadaConsultaRepositorioAdapter implements SugestaoRecusadaConsultaRepositorio {

    private final SugestaoRecusadaJpaRepository jpaRepository;

    SugestaoRecusadaConsultaRepositorioAdapter(SugestaoRecusadaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> recusadosPara(UUID recursoId) {
        return jpaRepository.buscarPacientesRecusados(recursoId);
    }
}
