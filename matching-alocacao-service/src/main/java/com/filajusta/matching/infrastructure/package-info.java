/**
 * Camada de infraestrutura do matching-alocacao-service. Actuator expõe
 * {@code GET /actuator/health} (porta de management separada, ver
 * application.yml). {@code infrastructure.persistence} mapeia o schema
 * {@code matching_alocacao} (JPA + migration Flyway, AD-9), tanto o lado de
 * escrita ({@code ScoreReplicaRepositorioAdapter}, Story 3.1b) quanto o de
 * leitura ({@code FilaRepositorioAdapter}, Story 3.1c);
 * {@code infrastructure.relay} (Story 3.1b) é o primeiro consumidor SQS
 * real do projeto -- {@code ScoreCalculadoConsumerJob} lê a fila SQS FIFO
 * assinante de {@code score-calculado.fifo} (declarada em
 * {@code infra-cdk}) e mantém a réplica local de Score via upsert
 * idempotente.
 *
 * <p>{@code infrastructure.bootstrap} (Story 3.1c): {@code TriagemScoreClient}
 * ({@code RestClient} síncrono) e {@code ScoreBootstrapService} implementam
 * o bootstrap a frio da réplica a partir de {@code GET /internal/scores}
 * (Story 3.1a, triagem-score-service), disparado por
 * {@code ConsultarFilaPriorizada} quando a réplica está vazia.
 * {@code infrastructure.web} (Story 3.1c): {@code FilaController} expõe
 * {@code GET /v1/fila}; {@code FilaExceptionHandler} traduz falha do
 * bootstrap para {@code 503} RFC 7807.
 *
 * <p>{@code infrastructure.persistence} (Story 3.2b2):
 * {@code RecursoRepositorioAdapter} implementa o upsert idempotente de
 * {@code Recurso} por {@code codigoRecurso} (INSERT ... ON CONFLICT nativo,
 * {@code RecursoJpaRepository}); {@code infrastructure.web}: {@code
 * RecursosInternalController} expõe {@code POST /internal/recursos}.
 */
package com.filajusta.matching.infrastructure;
