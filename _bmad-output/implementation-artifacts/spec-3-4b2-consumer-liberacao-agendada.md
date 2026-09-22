---
title: 'Consumer SQS da Liberação Agendada (Story 3-4b2)'
type: 'feature'
created: '2026-09-20'
status: 'done'
review_loop_iteration: 0
context: ['spec-3-4a2-publicacao-liberacao-agendada.md', 'spec-3-4b1-liberacao-real-recurso.md']
baseline_commit: 'd301f59c80107c400bc0a813e2de7f078ef2a940'
---

## Intent

**Problem:** A fila SQS standard com liberações agendadas (`matching-alocacao-service`) está sendo populada (Story 3-4a2) e as mensagens aguardam consumo — sem nenhum listener, as mensagens expiram ou são transferidas para a DLQ, perdendo a oportunidade de liberar recursos em tempo hábil.

**Approach:** Implementar um job `@Scheduled` (`LiberacaoAgendadaSqsConsumerJob`) que consome da fila SQS standard de liberações agendadas (`ConfirmaSusStack`), desserializa cada mensagem, valida o schema, e invoca o comando `LiberarRecurso` já pronto (Story 3-4b1) para executar a liberação real. Idempotência garantida por `alocacaoId` (o comando já é idempotente); reprocessamento de DLQ via script manual fora de escopo. Inclui tratamento de schema versioning (eventos futuros com versão incompatível vão para a DLQ sem quebrar o consumer).

## Boundaries & Constraints

**Always:**
- Consumer roda em poller `@Scheduled(fixedRate = 5000)` (intervalo de 5s, customizável via propriedade), invocando `receiveMessage` com `MaxNumberOfMessages=10` e `WaitTimeSeconds=20` (long-poll).
- Desserialização: `ObjectMapper.readValue(body, LiberacaoAgendadaEvent.class)` esperando `{alocacaoId, recursoId, correlationId, version, occurredAt, ...}`, com suporte a versão additive (`version` presente, mas tolerante a campos novos).
- Rejeição clara de `version` incompatível (major version mismatch — ex.: `version=2` quando esperado `version=1`): log `WARN`, mensagem reenviada para DLQ sem chamar `LiberarRecurso`, prosseguindo para a próxima mensagem.
- Idempotência via `alocacaoId`: se `LiberarRecurso` retorna no-op (já liberada ou inexistente), consumidor apenas deleta a mensagem da fila sem erro.
- Tratamento de erro transiente (ex.: timeout do `LiberarRecurso`, conexão perdida): mensagem volta à fila (não deletada) com número de tentativas rastreado via atributo SQS `ApproximateReceiveCount`; quando `ApproximateReceiveCount >= 5` (de acordo com `maxReceiveCount` da DLQ), mensagem entra em DLQ automaticamente via policy do SQS.
- Log estruturado: cada consumo registra `{messageId, alocacaoId, recursoId, correlationId, receiveCount, outcome}` em nível INFO.
- Sem filtro de correlationId duplicado (out-of-scope: rastreamento de duplicação em nível de aplicação).

**Ask First:** Nenhuma. O consumer depende apenas de `LiberarRecurso` (Story 3-4b1, já pronto) e configuração da fila no CDK (Story 3-4a2, já pronto).

**Never:** 
- Não criar novo endpoint REST. 
- Não alterar a fila SQS nem adicionar delay — isso já existe e é gerenciado por Story 3-4a2/CDK.
- Não reprocessar DLQ automaticamente (manual via script/operação está ok, mas não built-in nesta story).
- Não criar consumer async via `@SqsListener` do Spring Cloud AWS v3 — padrão do projeto é poller síncrono explícito.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HAPPY_PATH | Mensagem válida na fila com `alocacaoId`, `recursoId`, `version=1` | Consumer deserializa, chama `LiberarRecurso`, deleta mensagem da fila | N/A |
| JA_LIBERADA | Mensagem refere a `alocacaoId` já liberada | `LiberarRecurso` retorna no-op; consumer deleta mensagem sem erro | N/A |
| VERSION_INCOMPATIVEL | `version=2` (ou major version incompatível) na mensagem | Log WARN, mensagem reenviada para DLQ, consumer continua | N/A |
| ERRO_TRANSIENTE | Timeout/falha de conexão ao chamar `LiberarRecurso` | Mensagem não deletada; volta à fila, `ApproximateReceiveCount` incrementa | Retry automático via SQS |
| TIMEOUT_RECEBIMENTO | Long-poll de 20s expirou sem mensagens | Consumer retorna vazio; próximo ciclo do poller em 5s | N/A |

## Code Map

- `matching-alocacao-service/.../application/job/LiberacaoAgendadaSqsConsumerJob.java` (novo) -- `@Component public class LiberacaoAgendadaSqsConsumerJob { @Scheduled(fixedRate=5000) void consumir() { ... } }`
- `matching-alocacao-service/.../infrastructure/messaging/LiberacaoAgendadaEvent.java` (novo ou reutilizar) -- DTO/record com `alocacaoId`, `recursoId`, `correlationId`, `version`, `occurredAt`.
- `matching-alocacao-service/.../infrastructure/config/LiberacaoAgendadaSqsClientConfig.java` -- já existe; consumidor apenas lê daí o `AmazonSQS client` e URL da fila (`matching.queue.liberacao-agendada.url`).
- `matching-alocacao-service/src/test/.../LiberacaoAgendadaSqsConsumerJobIntegrationTest.java` (novo) -- `@Testcontainers`, `LocalStackContainer`, mock de mensagens na fila, verifica chamada a `LiberarRecurso` e deleção.

## Tasks & Acceptance

**Execution:**
- [ ] `LiberacaoAgendadaEvent.java` -- DTO/record representando mensagem SQS (se não reutilizar evento do outbox)
- [ ] `LiberacaoAgendadaSqsConsumerJob.java` -- poller com `@Scheduled(fixedRate=5000)` consumindo até 10 mensagens por ciclo via long-poll (20s)
- [ ] Desserialização com `ObjectMapper.readValue` + validação de `version` (rejeitar major incompatível para DLQ)
- [ ] Invocação de `LiberarRecurso.liberar(alocacaoId, recursoId, correlationId)` para cada mensagem válida
- [ ] Idempotência: se `LiberarRecurso` retorna no-op, consumer deleta mensagem
- [ ] Deleção de mensagem após sucesso via `sqs.deleteMessage(queueUrl, receiptHandle)`
- [ ] Log estruturado em cada ciclo e por mensagem
- [ ] Teste de integração com LocalStack — enfileira mensagem, consumer processa, verifica deleção e chamada a `LiberarRecurso`

**Acceptance Criteria:**
- Given uma fila SQS standard com mensagens de `LiberacaoAgendada`, when o poller executa, then cada mensagem válida é consumida uma vez, `LiberarRecurso` é invocado e a mensagem é deletada.
- Given uma mensagem com `version` incompatível (ex.: `version=2`), when o consumer processa, then a mensagem é movida para DLQ sem chamar `LiberarRecurso`.
- Given `LiberarRecurso` retorna no-op (alocação já liberada), when o consumer processa, then a mensagem é deletada (idempotência).
- Given error transiente (timeout), when o consumer processa, then a mensagem volta à fila e `ApproximateReceiveCount` é incrementado até máximo (DLQ via policy SQS).

## Spec Change Log

### Review Loop 1 (2026-09-20)
**Bad Spec:** AC-4 (error transiente) menciona que "mensagem volta à fila automaticamente via SQS", mas não há teste verificando que a mensagem PERMANECE e NÃO É DELETADA quando LiberarRecurso falha. Adicionado teste `consumerNaoDeleteaMensagemEmCasoDeErroTransiente` para cobrir esse caminho crítico.

**Patches aplicados:** 
- Adicionar validação de null em `correlationId` (inconsistência com alocacaoId/recursoId)
- Adicionar try-catch para JsonProcessingException (desserialização malformada)
- Adicionar teste para JSON inválido (edge case)
- Adicionar teste para deleteMessage falha (retry implicit)
- Adicionar validação de queue-url/dlq-url vazias (fail-fast)
- Adicionar comentário sobre WaitTimeSeconds=20 (trade-off latência/CPU)
- Adicionar documentação de idempotência e retry automático (SQS + comando)

## Design Notes

A tolerância a schema versioning aditivo é herdada do padrão definido em Story 3.1 (consumidor SNS de `ScoreCalculado`). Major version incompatível (ex.: `version=2`) é uma ruptura contratual — o consumidor não pode tentar desserializar sem risco de dados corrompidos, então DLQ é o caminho correto (operador investigará manualmente depois).

O intervalo do poller (5s) é um trade-off: alta latência de aceitação de liberações (até 5s) vs. overhead de polling frequente. Customizável via propriedade `matching.job.liberacao-consumer.poll-interval-ms` sem mudança de código.

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- testes verdes, incluindo teste de integração com LocalStack

## Suggested Review Order

**Poller e gerenciamento de filas SQS**

- Fail-fast validation do constructor: queue-url e dlq-url não podem estar vazios se consumer habilitado.
  [`LiberacaoAgendadaSqsConsumerJob.java:90-103`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJob.java#L90)

- Método consumirPendentes: poller @Scheduled com long-poll 20s, até 10 mensagens por ciclo.
  [`LiberacaoAgendadaSqsConsumerJob.java:114-135`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJob.java#L114)

**Desserialização, validação e roteamento**

- Desserialização com ObjectMapper, captura de JsonProcessingException com log/DLQ.
  [`LiberacaoAgendadaSqsConsumerJob.java:144-178`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJob.java#L144)

- Validação de version: rejeição clara de version != 1 para DLQ sem chamar LiberarRecurso.
  [`LiberacaoAgendadaSqsConsumerJob.java:148-154`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJob.java#L148)

- Validação de null em alocacaoId, recursoId e correlationId: campos críticos validados antes de invocação.
  [`LiberacaoAgendadaSqsConsumerJob.java:162-168`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJob.java#L162)

**Integração com comando de liberação**

- Invocação de LiberarRecurso.liberar com try-catch: falha transiente não deleta mensagem.
  [`LiberacaoAgendadaSqsConsumerJob.java:181-193`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJob.java#L181)

- Deleção de mensagem apenas após LiberarRecurso confirmado (Boundaries).
  [`LiberacaoAgendadaSqsConsumerJob.java:195-207`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJob.java#L195)

**Event DTO e configuração**

- LiberacaoAgendadaEvent record: desserialização flexível com Jackson, suporte a schema aditivo.
  [`LiberacaoAgendadaEvent.java`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/messaging/LiberacaoAgendadaEvent.java)

- LiberacaoAgendadaSqsClientConfig e fallback: bean compartilhado entre relay e consumer com @ConditionalOnMissingBean.
  [`LiberacaoAgendadaSqsClientConfig.java:51-73`](../../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsClientConfig.java#L51)

**Testes de integração**

- Happy path: mensagem válida consumida, Alocação liberada, Recurso disponível, mensagem deletada.
  [`LiberacaoAgendadaSqsConsumerJobIntegrationTest.java:131-164`](../../../../matching-alocacao-service/src/test/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJobIntegrationTest.java#L131)

- Version incompatível: mensagem rejeitada para DLQ, Alocação permanece ATIVA.
  [`LiberacaoAgendadaSqsConsumerJobIntegrationTest.java:166-209`](../../../../matching-alocacao-service/src/test/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJobIntegrationTest.java#L166)

- Idempotência: Alocação já liberada resulta em no-op seguro, mensagem deletada.
  [`LiberacaoAgendadaSqsConsumerJobIntegrationTest.java:211-268`](../../../../matching-alocacao-service/src/test/java/com/confirmasus/matching/infrastructure/relay/LiberacaoAgendadaSqsConsumerJobIntegrationTest.java#L211)

