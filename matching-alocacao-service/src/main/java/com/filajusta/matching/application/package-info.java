/**
 * Casos de uso do matching-alocacao-service (AD-2).
 * {@code application.command} (Story 3.1b):
 * {@link com.filajusta.matching.application.command.AtualizarScoreReplica}
 * muta a réplica local de Score a partir do evento {@code ScoreCalculado}
 * consumido por {@code ScoreCalculadoConsumerJob}
 * (infrastructure/relay). A porta de saída
 * {@link com.filajusta.matching.application.command.ScoreReplicaRepositorio}
 * também vive aqui, implementada em {@code infrastructure/persistence}.
 *
 * <p>{@code application.query} (Story 3.1c, CQRS lógico):
 * {@link com.filajusta.matching.application.query.ConsultarFilaPriorizada}
 * atende {@code GET /v1/fila} -- réplica vazia dispara o bootstrap síncrono
 * a frio ({@link com.filajusta.matching.application.query.ScoreBootstrap},
 * implementado em {@code infrastructure/bootstrap} reaproveitando
 * {@code AtualizarScoreReplica}) antes de ler via
 * {@link com.filajusta.matching.application.query.FilaRepositorio}
 * (implementada em {@code infrastructure/persistence}, reaproveitando
 * {@code findAll()}/{@code count()} sem query nova) e calcular a
 * Prioridade Efetiva de cada linha.
 */
package com.filajusta.matching.application;
