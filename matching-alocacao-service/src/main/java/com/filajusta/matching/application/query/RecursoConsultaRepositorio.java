package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.Recurso;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura de {@link Recurso} (Story 3.2b3) -- lado de consulta do
 * CQRS lógico da arquitetura, irmã de
 * {@link com.filajusta.matching.application.command.RecursoRepositorio}
 * (que só faz upsert, Story 3.2b2), mesmo split de
 * {@link FilaRepositorio} vs {@code ScoreReplicaRepositorio}. Implementada
 * em {@code infrastructure.persistence} reaproveitando o {@code
 * RecursoJpaRepository} já existente -- {@code findById} herdado de
 * {@code JpaRepository}, mais uma query nativa nova para a contagem de
 * tiers (Code Map da spec 3.2b3).
 */
public interface RecursoConsultaRepositorio {

    /**
     * Busca o {@link Recurso} pelo seu {@code recursoId} -- vazio quando não
     * existe registro (traduzido para {@link RecursoNaoEncontradoException}
     * por {@link ConsultarSugestaoRecurso}, Boundaries da spec 3.2b3).
     */
    Optional<Recurso> buscarPorId(UUID recursoId);

    /**
     * Quantidade de valores DISTINTOS de {@code especificidadeRank}
     * estritamente menores que {@code especificidadeRank}, com pelo menos 1
     * {@link Recurso} {@code disponivel=true} naquele tier -- é o "N" do
     * algoritmo de tiers ({@link ConsultarSugestaoRecurso}). Recursos do
     * mesmo tier consomem 1 posição no total, nunca uma por Recurso
     * (Boundaries "Always" da spec 3.2b3).
     */
    int contarTiersMaisGenericosDisponiveis(int especificidadeRank);
}
