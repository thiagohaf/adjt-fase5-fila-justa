/**
 * Camada de infraestrutura do triagem-score-service. Actuator expoe
 * {@code GET /actuator/health} (porta de management separada, ver
 * application.yml). {@code infrastructure.web} expoe
 * {@code POST /v1/triagens} (FR-1, FR-3); {@code infrastructure.persistence}
 * mapeia o schema {@code triagem_score} (JPA + migration Flyway, AD-9).
 *
 * <p>Deferido nesta fase (spec 2.1 Boundaries -- "Never"):
 * {@code infrastructure.grpc} ({@code ResolveCpfParaId}/
 * {@code ObterCpfMascarado}, AD-7, sem consumidor ainda) e
 * {@code infrastructure.outbox} (publisher real para SNS -- so a tabela
 * outbox existe, sem relay).
 */
package com.filajusta.triagem.infrastructure;
