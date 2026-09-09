/**
 * Camada de infraestrutura do triagem-score-service. Actuator expoe
 * {@code GET /actuator/health} (porta de management separada, ver
 * application.yml). {@code infrastructure.web} expoe
 * {@code POST /v1/triagens} (FR-1, FR-3); {@code infrastructure.persistence}
 * mapeia o schema {@code triagem_score} (JPA + migration Flyway, AD-9);
 * {@code infrastructure.relay} (Story 3.0) e o publisher real do outbox --
 * {@code RelaySnsPublisherJob} publica {@code ScoreCalculado} no topico SNS
 * FIFO {@code score-calculado.fifo} (declarado em {@code infra-cdk}).
 *
 * <p>Deferido nesta fase (spec 2.1 Boundaries -- "Never"):
 * {@code infrastructure.grpc} ({@code ResolveCpfParaId}/
 * {@code ObterCpfMascarado}, AD-7, sem consumidor ainda).
 */
package com.filajusta.triagem.infrastructure;
