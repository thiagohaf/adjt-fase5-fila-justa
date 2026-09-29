package com.confirmasus.matching.application.query;

import com.confirmasus.matching.domain.Recurso;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura de {@link Recurso} -- lado de consulta do CQRS lógico da
 * arquitetura, irmã de
 * {@link com.confirmasus.matching.application.command.RecursoRepositorio}
 * (que só faz upsert). Implementada em {@code infrastructure.persistence}
 * reaproveitando o {@code RecursoJpaRepository} já existente -- {@code
 * findById} herdado de {@code JpaRepository}.
 */
public interface RecursoConsultaRepositorio {

    /**
     * Busca o {@link Recurso} pelo seu {@code recursoId} -- vazio quando não
     * existe registro (traduzido para {@link RecursoNaoEncontradoException}
     * pelos casos de uso que dependem dele).
     */
    Optional<Recurso> buscarPorId(UUID recursoId);
}
