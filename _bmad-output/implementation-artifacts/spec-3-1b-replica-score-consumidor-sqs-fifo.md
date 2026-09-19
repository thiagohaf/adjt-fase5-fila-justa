---
title: 'Story 3.1b: Réplica local de Score + Consumidor SQS FIFO (matching-alocacao-service)'
type: 'feature'
created: '2026-09-08'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'dd1c7bc1f0df3bf29cb8968eeeb24dc961e51a33'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `matching-alocacao-service` ainda não existe. Sem ele, o relay real do `ScoreCalculado` (Story 3.0, tópico SNS FIFO `score-calculado.fifo`) não tem nenhum consumidor, e não há onde manter uma réplica local de Score para a fila priorizada (Story 3.1c).

**Approach:** Criar o esqueleto Clean Architecture do `matching-alocacao-service` (schema `matching_alocacao`) com um consumidor SQS FIFO — primeiro consumidor real do projeto — assinante de `score-calculado.fifo`, que mantém uma réplica local de Score via upsert idempotente. Sem cálculo de Prioridade Efetiva e sem endpoint REST ainda — isso é a Story 3.1c, onde o cálculo tem um consumidor real (`GET /v1/fila`).

## Boundaries & Constraints

**Always:** Réplica: upsert idempotente, last-write-wins por `occurredAt`, empate desempatado por `eventId` (ordem lexicográfica). Consumidor só remove a mensagem da fila SQS após o upsert confirmado (nunca antes). Cobertura ≥90% linha (JaCoCo) no domínio/aplicação.

**Ask First:** Nenhuma pendente — deploy ECS, rota no `gateway-service` e Dockerfile seguem o mesmo padrão de chore adiado pós-merge já usado nas Stories 2.1/3.0/3.1a.

**Never:** Cálculo de Prioridade Efetiva/Aging, endpoint REST `GET /v1/fila` ou bootstrap a frio via `ListarScoresAtuais` (Story 3.1c). Sugestão de matching, confirmação/recusa, liberação de recurso (Stories 3.2–3.4). Deploy ECS/CDK do serviço, rota no `gateway-service`, Dockerfile — chore adiado.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Consumo normal | Mensagem `ScoreCalculado` válida na fila | Réplica local upsertada; mensagem removida da fila | N/A |
| Redelivery/duplicata | Mesmo `eventId` entregue duas vezes | Upsert idempotente — réplica não duplica nem retrocede | N/A |
| Mensagem fora de ordem | `occurredAt` menor que o já persistido para o Paciente | Réplica mantém a versão mais recente; mensagem antiga não sobrescreve | N/A |
| Mensagem malformada | Payload inválido/schema inesperado | Mensagem não é removida; log de erro; retry até `maxReceiveCount`, depois DLQ | N/A |

</frozen-after-approval>

## Code Map

- `pom.xml:80` -- módulo comentado -- descomentar
- `triagem-score-service/pom.xml` -- deps+JaCoCo(90%)+PIT -- replicar em `matching-alocacao-service/pom.xml`, pacote `com.filajusta.matching.*`
- `.../infrastructure/relay/RelaySnsPublisherJob.java:64-207` + `RelaySnsClientConfig.java:44-70` -- estilo de poller/cliente AWS a seguir; **sem consumidor SQS real no projeto ainda** -- desenhar `ScoreCalculadoConsumerJob` do zero
- `.../db/migration/V1__create_triagem_schema.sql:4` -- padrão de migration -- criar `V1__create_matching_schema.sql` (`matching_alocacao`, tabela `score_replica`: `paciente_id` PK, `score`, `occurred_at`, `event_id`, `updated_at`)
- `.../test/.../RelaySnsPublisherJobIntegrationTest.java:71` -- LocalStack `localstack/localstack:4.12.0` -- reusar
- `infra-cdk/.../FilaJustaStack.java:204-238` (`buildScoreCalculadoTopic`/`buildTriagemScoreServiceTaskRole`) -- mirror: fila SQS FIFO consumidora + subscription + DLQ + task role `grantConsumeMessages`
- `epic-3-context.md` (Technical Decisions) -- semântica de upsert já fixada

## Tasks & Acceptance

**Execution:**
- [x] `pom.xml` -- descomentar módulo -- ativa o build
- [x] `matching-alocacao-service/pom.xml` -- novo módulo espelhando `triagem-score-service/pom.xml` -- esqueleto do serviço
- [x] `.../db/migration/V1__create_matching_schema.sql` -- schema `matching_alocacao`, tabela `score_replica`
- [x] `.../application/command/AtualizarScoreReplica.java` + porta `ScoreReplicaRepositorio` -- caso de uso de upsert
- [x] `.../infrastructure/persistence/*` -- JPA entity + adapter da réplica (upsert idempotente)
- [x] `.../infrastructure/relay/ScoreCalculadoConsumerJob.java` -- poller `@Scheduled`, `SqsClient.receiveMessage`, parse do envelope, upsert, `deleteMessage` só após sucesso
- [x] `infra-cdk/.../FilaJustaStack.java` -- fila SQS FIFO + subscription + DLQ + task role de consumo
- [x] Teste unitário do upsert (last-write-wins, tie-break por `eventId`) -- cobre a I/O Matrix
- [x] Teste de integração Testcontainers-Postgres (upsert idempotente) + Testcontainers-LocalStack (consumo ponta a ponta, `localstack:4.12.0`)

**Acceptance Criteria:**
- Given uma mensagem `ScoreCalculado` válida na fila SQS, when o consumidor roda, then a réplica local é upsertada e a mensagem é removida da fila
- Given duas mensagens do mesmo Paciente chegando fora de ordem por `occurredAt`, when ambas são consumidas, then a réplica reflete sempre a versão mais recente, nunca retrocede

## Spec Change Log

- 2026-09-10: Aplicados os 9 patches apontados pelo code review multi-agente (severidade `patch`): (1) `wait-time-seconds` agora limitado a `0..20` (teto do SQS); (2) timeout do `SqsClient` (`ScoreCalculadoSqsClientConfig`) elevado de 10s para 30s, folgando acima do long-poll de até 20s; (3) `validarEnvelope` agora rejeita `scoreValor` não numérico (antes `asInt()` de um valor não numérico virava `0` silenciosamente) -- coberto por teste unitário novo; (4) fila SQS FIFO consumidora (`infra-cdk`) ganhou `visibilityTimeout` explícito de 60s (antes usava o default de 30s do SQS); (5) os `log.error` de falha de upsert/delete em `ScoreCalculadoConsumerJob.processar` agora citam `eventId`/`correlationId` de domínio, não só o `messageId` do SQS; (6) novo teste de domínio prova que o desempate por `eventId` segue a ordem lexicográfica da string mesmo num par de UUIDs onde isso discorda de `UUID#compareTo(UUID)`; (7) o teste de IAM do CDK agora trava `sqs:DeleteMessage` além de `sqs:ReceiveMessage`; (8) novo teste prova que `filajusta.matching.relay.enabled=false` realmente impede a criação do `SqsClient`/`ScoreCalculadoConsumerJob` no contexto Spring; (9) javadoc de `ScoreCalculadoSqsClientConfig` corrigido -- a região não cai na cadeia default do SDK neste serviço (`application.yml` sempre popula `filajusta.matching.relay.region` via `${AWS_REGION:us-east-1}`). `matching-alocacao-service` passou de 31 para 34 testes (patches 3, 6 e 8 adicionaram um teste cada); `infra-cdk` permanece com 23 (patch 7 só reforçou uma asserção existente).

## Design Notes

`ScoreCalculadoConsumerJob` não introduz outbox local: consome direto da SQS FIFO assinante do tópico (diferente do publisher da Story 3.0 -- aqui só leitura). DLQ segue o mesmo `maxReceiveCount=5` já usado nas outras filas do projeto (convenção, não fixado no `epic-3-context.md`). Prioridade Efetiva fica para a 3.1c, onde tem consumidor real -- evita código sem uso nesta fase.

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes + JaCoCo ≥90% domínio
- `mvn -pl infra-cdk -am compile exec:java` -- expected: `cdk synth` sem erro com a fila SQS FIFO nova declarada
- Executado (verificação independente, antes dos 9 patches do code review): `mvn -pl matching-alocacao-service,infra-cdk -am verify` -- BUILD SUCCESS, 31 testes em `matching-alocacao-service` (0 falhas), 23 testes em `infra-cdk` (0 falhas), JaCoCo ≥90% confirmado nos bundles `domain.*`/`application.command.*`
- Executado (após os 9 patches, ver Spec Change Log): `mvn -pl matching-alocacao-service,infra-cdk -am verify` -- BUILD SUCCESS, 34 testes em `matching-alocacao-service` (0 falhas), 23 testes em `infra-cdk` (0 falhas), JaCoCo ≥90% confirmado nos bundles `domain.*`/`application.command.*`

## Suggested Review Order

**Consumidor SQS (entrada)**

- Poller `@Scheduled`: lê o lote, delega a `processar()` por mensagem -- todo o resto do arquivo existe para sustentar este método.
  [`ScoreCalculadoConsumerJob.java:96`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoConsumerJob.java#L96)

- `processar()`: valida envelope, faz upsert, só remove da fila após confirmação -- núcleo da garantia "nunca antes" das Boundaries.
  [`ScoreCalculadoConsumerJob.java:118`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoConsumerJob.java#L118)

- `validarEnvelope()`: rejeita `scoreValor` não numérico em vez de deixar `asInt()` virar `0` silenciosamente (achado do code review).
  [`ScoreCalculadoConsumerJob.java:181`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoConsumerJob.java#L181)

- Logs de falha agora citam `eventId`/`correlationId` de domínio, não só `messageId` do SQS (achado do code review) -- correlacionável com o lado `triagem-score-service`.
  [`ScoreCalculadoConsumerJob.java:157`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoConsumerJob.java#L157)

**Upsert idempotente (decisão de design)**

- `maisRecenteQue()`: regra pura de last-write-wins -- compara `eventId` por string, não `UUID#compareTo`, para concordar com a ordem de bytes do Postgres.
  [`ScoreReplica.java:71`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/ScoreReplica.java#L71)

- `INSERT ... ON CONFLICT ... WHERE`: a garantia atômica real contra corrida entre instâncias do consumidor vive na query nativa, não em comparação no lado Java.
  [`ScoreReplicaJpaRepository.java:26`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/ScoreReplicaJpaRepository.java#L26)

**Infra (CDK)**

- Fila SQS FIFO consumidora + DLQ (`maxReceiveCount=5`) + `visibilityTimeout` explícito de 60s (achado do code review) -- assina `score-calculado.fifo`.
  [`FilaJustaStack.java:260`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L260)

- Task role com `grantConsumeMessages` -- IAM mínimo para o consumidor, mirror do papel de publish da Story 3.0.
  [`FilaJustaStack.java:301`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L301)

**Esqueleto e schema**

- `V1__create_matching_schema.sql`: schema `matching_alocacao`, tabela `score_replica` -- primeira migration do novo serviço.
  [`V1__create_matching_schema.sql:1`](../../matching-alocacao-service/src/main/resources/db/migration/V1__create_matching_schema.sql#L1)

- `AtualizarScoreReplica`: caso de uso fino, só delega ao adapter -- sem lógica própria além de construir o candidato.
  [`AtualizarScoreReplica.java:1`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/AtualizarScoreReplica.java#L1)

**Testes**

- Domínio: aging/tie-break, incluindo o par de UUIDs que discorda de `UUID#compareTo` (achado do code review).
  [`ScoreReplicaTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/domain/ScoreReplicaTest.java#L1)

- Consumidor: casos mockados de SQS (payload malformado, falha transitória, `relay.enabled=false`).
  [`ScoreCalculadoConsumerJobTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoConsumerJobTest.java#L1)

- Integração ponta a ponta: Testcontainers-Postgres + LocalStack, `localstack:4.12.0`.
  [`ScoreCalculadoConsumerJobIntegrationTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/ScoreCalculadoConsumerJobIntegrationTest.java#L1)
