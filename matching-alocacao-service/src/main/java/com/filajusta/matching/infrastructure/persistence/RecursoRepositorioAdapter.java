package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.RecursoRepositorio;
import com.filajusta.matching.domain.Recurso;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Adapter que implementa a porta {@link RecursoRepositorio}
 * (application/command) usando {@link RecursoJpaRepository} (Spring Data,
 * schema {@code matching_alocacao}). O upsert em si é o INSERT ... ON
 * CONFLICT nativo ({@link RecursoJpaRepository#upsert}); a leitura de volta
 * ({@link RecursoJpaRepository#findByCodigoRecurso}), na MESMA transação,
 * é o que permite devolver o {@link Recurso} efetivamente persistido (com o
 * {@code recursoId} real, preservado num conflito) -- ver javadoc de
 * {@link RecursoRepositorio#upsert(Recurso)}.
 */
@Component
class RecursoRepositorioAdapter implements RecursoRepositorio {

    private final RecursoJpaRepository jpaRepository;

    RecursoRepositorioAdapter(RecursoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Recurso upsert(Recurso recurso) {
        jpaRepository.upsert(recurso.getRecursoId(), recurso.getCodigoRecurso(),
                recurso.getEspecificidadeRank(), recurso.isDisponivel());

        RecursoJpaEntity persistido = jpaRepository.findByCodigoRecurso(recurso.getCodigoRecurso())
                .orElseThrow(() -> new IllegalStateException(
                        "Recurso nao encontrado logo apos o upsert -- codigoRecurso=" + recurso.getCodigoRecurso()));
        return persistido.paraDominio();
    }

    @Override
    @Transactional
    public void marcarIndisponivel(UUID recursoId) {
        jpaRepository.marcarIndisponivel(recursoId);
    }
}
