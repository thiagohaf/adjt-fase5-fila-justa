package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.ScoreReplica;

import java.util.List;

/**
 * Porta de leitura da réplica local de Score (Story 3.1c) -- lado de
 * consulta do CQRS lógico da arquitetura, irmã de
 * {@link com.filajusta.matching.application.command.ScoreReplicaRepositorio}
 * (que só escreve, Story 3.1b). Implementada em
 * {@code infrastructure/persistence} reaproveitando {@code count()}/
 * {@code findAll()} já herdados de {@code JpaRepository} -- sem nenhuma
 * query nova (Code Map da spec 3.1c).
 */
public interface FilaRepositorio {

    /**
     * {@code true} quando a réplica não tem nenhuma linha -- dispara o
     * bootstrap síncrono a frio em {@link ConsultarFilaPriorizada}
     * (Boundaries da spec 3.1c).
     */
    boolean estaVazia();

    /**
     * Todas as linhas da réplica, em qualquer ordem --
     * {@link ConsultarFilaPriorizada} é quem calcula a Prioridade Efetiva e
     * ordena decrescente.
     */
    List<ScoreReplica> listarTodas();
}
