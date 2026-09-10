/**
 * Camada de infraestrutura do matching-alocacao-service. Actuator expõe
 * {@code GET /actuator/health} (porta de management separada, ver
 * application.yml). {@code infrastructure.persistence} mapeia o schema
 * {@code matching_alocacao} (JPA + migration Flyway, AD-9);
 * {@code infrastructure.relay} (Story 3.1b) é o primeiro consumidor SQS
 * real do projeto -- {@code ScoreCalculadoConsumerJob} lê a fila SQS FIFO
 * assinante de {@code score-calculado.fifo} (declarada em
 * {@code infra-cdk}) e mantém a réplica local de Score via upsert
 * idempotente.
 *
 * <p>Deferido nesta fase (spec 3.1b Boundaries -- "Never"):
 * {@code infrastructure.web} (consulta da fila priorizada, cálculo de
 * Prioridade Efetiva/Aging -- Story 3.1c).
 */
package com.filajusta.matching.infrastructure;
