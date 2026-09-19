---
title: 'Publicação da Liberação Agendada (3-4a2)'
type: 'feature'
created: '2026-09-13'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '1446a224a7cc00c36fd61defb11abc88e67d762c'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `liberacao_agendada` (Story 3-4a1) é gravada mas nunca lida — nenhum processo publica a mensagem de delay na fila SQS que, eventualmente, dispararia a liberação real do Recurso (3-4b, deferida).

**Approach:** Novo job `LiberacaoAgendadaRelayJob` (`@Scheduled`, molde de `RelaySnsPublisherJob`) faz polling de `LiberacaoAgendadaRepositorio#buscarPendentes`, envia cada linha para uma fila SQS standard nova (`SqsClient.sendMessage` com `DelaySeconds = delaySegundos`) e marca `marcarComoEnviado` — busca+envio+marcação na MESMA transação, fechando a lacuna de concorrência apontada no code review da 3-4a1.

## Boundaries & Constraints

**Always:** `buscarPendentes` + `sendMessage` + `marcarComoEnviado` executam na mesma transação `@Transactional` do job (propagação `REQUIRED`, igual `RelaySnsPublisherJob.publicarPendentes`) — sem isso o `FOR UPDATE SKIP LOCKED` não impede duas instâncias de publicarem a mesma liberação. Falha ao enviar/marcar UM item é isolada (log + segue para o próximo item do lote, nunca aborta o lote inteiro) — mesmo padrão de `publicarEMarcar`. Mensagem carrega `alocacaoId`, `recursoId`, `correlationId` (para rastreio) no corpo.

**Ask First:** nenhuma decisão adicional além das já resolvidas nesta spec.

**Never:** implementar o consumidor/liberação real do Recurso (3-4b, deferida). Adicionar validação de domínio para `delaySegundos` fora do range aceito pelo SQS (achado já deferido separadamente). Reaproveitar o namespace `filajusta.matching.relay.*` ou `outbox-relay.*` — usar `filajusta.matching.liberacao-agendada-relay.*` isolado para não colidir os beans `SqsClient`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HAPPY_PATH | 1 `liberacao_agendada` pendente (`enviado_em IS NULL`) | Mensagem chega na fila com `DelaySeconds = delaySegundos`; `enviado_em` gravado | N/A |
| SEM_PENDENTES | Nenhuma linha pendente | Job não faz nada, sem erro | N/A |
| FALHA_ENVIO_1_ITEM | Lote com 2 pendentes; `sendMessage` falha para o 1º | 2º item ainda é processado nesta execução | Loga falha do 1º; `enviado_em` do 1º continua `NULL` (retry na próxima execução) |
| CONCORRENCIA_2_INSTANCIAS | 2 instâncias do job disparam ao mesmo tempo sobre a mesma linha pendente | Exatamente 1 instância publica e marca `enviado_em` | `SKIP LOCKED` garante exclusão mútua dentro da transação do job |

</frozen-after-approval>

## Code Map

- `infrastructure/relay/LiberacaoAgendadaRelayJob.java` (novo) -- molde `RelaySnsPublisherJob.java:69-194`: `@ConditionalOnProperty(prefix = "filajusta.matching.liberacao-agendada-relay", name = "enabled")`, `@Scheduled` + `@Transactional` em `publicarPendentes()`, chama `buscarPendentes(loteTamanho)`, para cada item `sqsClient.sendMessage(...).delaySeconds(item.getDelaySegundos())` + `liberacaoAgendadaRepositorio.marcarComoEnviado(item.getAlocacaoId())`; corpo da mensagem serializado via `ObjectMapper` (`alocacaoId`, `recursoId`, `correlationId`)
- `infrastructure/relay/LiberacaoAgendadaSqsClientConfig.java` (novo) -- cópia de `ScoreCalculadoSqsClientConfig.java:57-85`, `@Value` lendo `filajusta.matching.liberacao-agendada-relay.{endpoint-override,region}`
- `application.yml` -- novo bloco `filajusta.matching.liberacao-agendada-relay.{enabled,queue-url,region,endpoint-override,poll-interval-ms,batch-size}`, molde de `outbox-relay` (linhas 85-91)
- `infra-cdk/.../FilaJustaStack.java` -- nova `Queue liberacaoAgendadaQueue` ("liberacao-agendada", standard) + `Queue liberacaoAgendadaDlq` ("liberacao-agendada-dlq"), `maxReceiveCount=5`, `visibilityTimeout=60s`, molde `buildScoreCalculadoConsumerQueue` (linhas 278-317); `liberacaoAgendadaQueue.grantSendMessage(matchingAlocacaoServiceTaskRole)` (role já existe, linha 320)
- `MatchingAlocacaoServiceApplication.java` -- wiring do bean `LiberacaoAgendadaRelayJob`
- `test/.../LiberacaoAgendadaRelayJobIntegrationTest.java` (novo) -- molde `ScoreCalculadoConsumerJobIntegrationTest.java` (Testcontainers Postgres + LocalStack `4.12.0` `sqs`, `@DynamicPropertySource` cria fila e injeta `endpoint-override`/`region`/`queue-url`)
- `test/.../LiberacaoAgendadaRelayJobConcurrencyIntegrationTest.java` (novo) -- molde `UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java:137-187` (`ExecutorService`/`CountDownLatch`, N chamadas concorrentes a `publicarPendentes()` contra Postgres real, assere que cada linha pendente é marcada `enviado_em` exatamente 1 vez)

## Tasks & Acceptance

**Execution:**
- [x] `infrastructure/relay/LiberacaoAgendadaSqsClientConfig.java` -- bean `SqsClient` dedicado -- isola namespace de config e evita colisão com beans existentes
- [x] `infrastructure/relay/LiberacaoAgendadaRelayJob.java` -- polling transacional busca+envia+marca -- fecha a lacuna de concorrência deferida da 3-4a1
- [x] `application.yml`, `MatchingAlocacaoServiceApplication.java` -- config + wiring
- [x] `infra-cdk/.../FilaJustaStack.java` -- fila `liberacao-agendada` + DLQ + `grantSendMessages`
- [x] Testes: unitário do job (mock do porto + `SqsClient`), integração LocalStack (mensagem chega com `DelaySeconds` correto), integração de concorrência real (Postgres, sem duplicidade)

**Acceptance Criteria:**
- Given uma `liberacao_agendada` pendente com `delaySegundos = N`, when o job roda, then a fila recebe 1 mensagem com `DelaySeconds = N` e `enviado_em` é gravado na mesma transação
- Given 2 execuções concorrentes do job sobre a mesma linha pendente, when ambas rodam, then exatamente uma publica e marca `enviado_em`
- Given `mvn -pl matching-alocacao-service -am verify` e `cdk synth` (infra-cdk), then testes verdes e stack sintetiza sem erro

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, incluindo integração LocalStack e concorrência
- `mvn -pl infra-cdk -am verify` -- expected: `FilaJustaStack` sintetiza com a nova fila/DLQ

## Suggested Review Order

**Polling transacional (job novo)**

- Entry point: busca+envia+marca na MESMA `@Transactional`, fechando a lacuna de concorrência da 3-4a1.
  [`LiberacaoAgendadaRelayJob.java:104`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/LiberacaoAgendadaRelayJob.java#L104)

- Falha de UM item (montar corpo, enviar ou marcar) é isolada por try/catch -- nunca aborta o lote.
  [`LiberacaoAgendadaRelayJob.java:122`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/LiberacaoAgendadaRelayJob.java#L122)

**Dois beans `SqsClient` coexistindo (namespace isolado + `@Primary`)**

- Bean nomeado `liberacaoAgendadaSqsClient`, namespace `liberacao-agendada-relay.*` isolado de `relay.*`/`outbox-relay.*`.
  [`LiberacaoAgendadaSqsClientConfig.java:80`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/LiberacaoAgendadaSqsClientConfig.java#L80)

- `@Primary` acrescentado para o consumidor de ScoreCalculado continuar resolvendo sem ambiguidade com o 2º `SqsClient`.
  [`ScoreCalculadoSqsClientConfig.java:76`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoSqsClientConfig.java#L76)

**Infra CDK (fila + DLQ + permissão)**

- Fila standard `liberacao-agendada` + DLQ (`maxReceiveCount=5`), molde de `buildScoreCalculadoConsumerQueue`.
  [`FilaJustaStack.java:361`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L361)

- `grantSendMessages` na `MatchingAlocacaoServiceTaskRole` já existente -- nenhuma role nova.
  [`FilaJustaStack.java:250`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L250)

- Bloco de config novo, `enabled=true` por padrão (mesmo padrão de `outbox-relay`).
  [`application.yml:107`](../../matching-alocacao-service/src/main/resources/application.yml#L107)

**Testes**

- Prova o `@Primary` com os DOIS relays habilitados ao mesmo tempo -- exatamente a config default real de produção.
  [`SqsClientPrimaryBeanIntegrationTest.java:79`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/relay/SqsClientPrimaryBeanIntegrationTest.java#L79)

- Concorrência real (Postgres+LocalStack): 8 threads, exatamente 1 publica e marca `enviado_em`.
  [`LiberacaoAgendadaRelayJobConcurrencyIntegrationTest.java:122`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/relay/LiberacaoAgendadaRelayJobConcurrencyIntegrationTest.java#L122)

- LocalStack real: prova `DelaySeconds` pela (in)visibilidade da mensagem antes/depois do delay.
  [`LiberacaoAgendadaRelayJobIntegrationTest.java:111`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/relay/LiberacaoAgendadaRelayJobIntegrationTest.java#L111)

- Unitário (mocks): HAPPY_PATH, SEM_PENDENTES, FALHA_ENVIO_1_ITEM da I/O Matrix.
  [`LiberacaoAgendadaRelayJobTest.java:38`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/relay/LiberacaoAgendadaRelayJobTest.java#L38)

- CDK: fila standard+DLQ e `VisibilityTimeout`.
  [`FilaJustaStackTest.java:412`](../../infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java#L412)

- CDK: policy de `sqs:SendMessage` presa à role certa.
  [`FilaJustaStackTest.java:456`](../../infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java#L456)
