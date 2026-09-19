package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.query.AlocacaoConsultaRepositorio;
import com.filajusta.matching.domain.Alocacao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Adapter que implementa a porta {@link AlocacaoConsultaRepositorio}
 * (application/query, Story 3-3b2a) usando {@link AlocacaoJpaRepository}
 * (Spring Data, schema {@code matching_alocacao}) -- a query nativa de
 * {@code paciente_id} por status adicionada nesta story. Irmã de leitura de
 * {@link AlocacaoRepositorioAdapter} (que só faz insert, Story 3-3b1), mesmo
 * split de {@link RecursoRepositorioAdapter} vs
 * {@link RecursoConsultaRepositorioAdapter}.
 */
@Component
class AlocacaoConsultaRepositorioAdapter implements AlocacaoConsultaRepositorio {

    private final AlocacaoJpaRepository jpaRepository;

    AlocacaoConsultaRepositorioAdapter(AlocacaoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> pacientesComAlocacaoAtiva() {
        return jpaRepository.pacientesComAlocacaoAtiva(Alocacao.STATUS_ATIVA);
    }
}
