package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.ScoreReplica;

/**
 * Porta de saída para a réplica local de Score (Story 3.1b). Implementada em
 * {@code infrastructure/persistence} (JPA nativo, schema
 * {@code matching_alocacao}, tabela {@code score_replica}).
 *
 * <p>{@link #upsertSeMaisRecente(ScoreReplica)} aplica a MESMA regra de
 * {@link ScoreReplica#maisRecenteQue(ScoreReplica)} atomicamente no banco
 * (SQL nativo {@code INSERT ... ON CONFLICT ... WHERE}) -- não um
 * read-then-write em Java, que reabriria a corrida entre duas instâncias do
 * consumidor (mesmo raciocínio do {@code FOR UPDATE SKIP LOCKED} do relay
 * da Story 3.0, ver {@code EventoOutboxRepositorio} do
 * {@code triagem-score-service}). Chamar com uma versão igual ou mais
 * antiga da já persistida (redelivery/mensagem fora de ordem, I/O Matrix da
 * spec 3.1b) é um no-op silencioso -- não lança, não sobrescreve.
 */
public interface ScoreReplicaRepositorio {

    void upsertSeMaisRecente(ScoreReplica replica);
}
