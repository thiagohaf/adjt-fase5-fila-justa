package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.query.RecursoConsultaRepositorio;
import com.filajusta.matching.domain.Recurso;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link RecursoConsultaRepositorio}
 * (application/query, Story 3.2b3) usando {@link RecursoJpaRepository}
 * (Spring Data, schema {@code matching_alocacao}) -- {@code findById}
 * herdado de {@code JpaRepository} e a query nativa de contagem de tiers
 * adicionadas nesta story. Irmã de leitura de {@link RecursoRepositorioAdapter}
 * (que só faz upsert, Story 3.2b2), mesmo split de
 * {@link com.filajusta.matching.infrastructure.persistence.FilaRepositorioAdapter}
 * vs {@code ScoreReplicaRepositorioAdapter}.
 */
@Component
class RecursoConsultaRepositorioAdapter implements RecursoConsultaRepositorio {

    private final RecursoJpaRepository jpaRepository;

    RecursoConsultaRepositorioAdapter(RecursoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recurso> buscarPorId(UUID recursoId) {
        return jpaRepository.findById(recursoId).map(RecursoJpaEntity::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public int contarTiersMaisGenericosDisponiveis(int especificidadeRank) {
        // Math.toIntExact em vez de um cast simples: a cardinalidade real de
        // especificidadeRank e pequena (4 tiers no seed hoje), mas um cast
        // (int) silencioso sobre o long do COUNT(DISTINCT ...) do JPA
        // corromperia N sem aviso caso a query um dia devolva um valor fora
        // da faixa de int -- achado do code review multi-agente da Story
        // 3.2b3 (blind-hunter + edge-case-hunter, convergente).
        return Math.toIntExact(jpaRepository.contarTiersMaisGenericosDisponiveis(especificidadeRank));
    }
}
