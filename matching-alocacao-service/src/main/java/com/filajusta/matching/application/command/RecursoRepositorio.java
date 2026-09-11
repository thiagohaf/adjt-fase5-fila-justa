package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.Recurso;

/**
 * Porta de saída para persistência de {@link Recurso} (Story 3.2b2).
 * Implementada em {@code infrastructure.persistence} (JPA nativo, schema
 * {@code matching_alocacao}, tabela {@code recurso}).
 *
 * <p>{@link #upsert(Recurso)} cria uma linha nova (quando {@code
 * codigoRecurso} é inédito) ou atualiza {@code especificidadeRank}/{@code
 * disponivel} de uma já existente (mesmo {@code recursoId}) -- upsert
 * direto e idempotente por {@code codigoRecurso}, sem comparação temporal
 * (Boundaries da spec 3.2b2, ao contrário de {@link
 * ScoreReplicaRepositorio#upsertSeMaisRecente}).
 *
 * <p>Devolve o {@link Recurso} efetivamente persistido, não o parâmetro
 * recebido: como {@code recursoId} é uma identidade sintética atribuída
 * pela aplicação (não um dado natural do request, ao contrário de {@code
 * ScoreReplica#pacienteId}), o upsert nativo (ver {@code
 * RecursoJpaRepository#upsert}) nunca sobrescreve {@code recurso_id} num
 * conflito por {@code codigoRecurso} -- o valor devolvido é a única forma
 * de {@code UpsertRecurso} saber se a linha foi de fato criada (mesmo
 * {@code recursoId} do candidato) ou atualizada (um {@code recursoId}
 * diferente, preservado da primeira inserção).
 */
public interface RecursoRepositorio {

    Recurso upsert(Recurso recurso);
}
