/**
 * Casos de uso do matching-alocacao-service (AD-2). Story 3.1b entrega só
 * {@code application.command}: {@link com.filajusta.matching.application.command.AtualizarScoreReplica}
 * muta a réplica local de Score a partir do evento {@code ScoreCalculado}
 * consumido por {@code ScoreCalculadoConsumerJob}
 * (infrastructure/relay). A porta de saída
 * {@link com.filajusta.matching.application.command.ScoreReplicaRepositorio}
 * também vive aqui, implementada em {@code infrastructure/persistence}.
 *
 * <p>{@code application.query} (consulta da fila priorizada) é a Story
 * 3.1c -- não existe ainda nesta fase (Boundaries da spec 3.1b, "Never").
 */
package com.filajusta.matching.application;
