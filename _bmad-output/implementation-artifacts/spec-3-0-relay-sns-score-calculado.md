---
title: 'Story 3.0: Relay/Publisher Real do Evento ScoreCalculado (SNS FIFO)'
type: 'feature'
created: '2026-09-08'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'bb0ea8503b477769474bf9fd306a2045befc8326'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `triagem-score-service` só grava o evento `ScoreCalculado` na tabela `eventos_outbox` (Story 2.1) — nenhum publisher/relay para SNS existe. Epic 3 (Story 3.1) e Epic 4 dependem deste evento chegando de fato a um tópico para manter suas réplicas locais de Score; sem o relay, a fila priorizada de 3.1 não tem dado real para consumir via evento.

**Approach:** Job assíncrono (`@Scheduled`, dentro do próprio `triagem-score-service`) que lê linhas não publicadas de `eventos_outbox`, monta o envelope já fixado no Epic 2 (`{eventId, eventType, occurredAt, version, correlationId, payload}`) e publica em um tópico SNS FIFO novo (`MessageGroupId = pacienteId`), marcando a linha como publicada só após confirmação. Tópico declarado via CDK (`infra-cdk`). `correlationId` (hoje só propagado pelo gateway via header `X-Correlation-Id`, nunca lido por `triagem-score-service`) passa a ser capturado no `POST /v1/triagens` e gravado junto com a linha do outbox.

## Boundaries & Constraints

**Always:** Envelope publicado é exatamente `{eventId, eventType, occurredAt, version, correlationId, payload}` (version inicia em `1`); `eventId` do outbox vira o `MessageDeduplicationId` do SNS FIFO; `MessageGroupId = pacienteId` (extraído do `payload`); linha só é marcada publicada após ack do SNS (nunca antes) — reinício do job nunca perde nem publica a mesma linha duas vezes em condição normal; nome do tópico e intervalo do poller ficam em `application.yml` sob `filajusta.triagem.relay.*` (mesma convenção de `filajusta.triagem.limites.*`); credenciais AWS só via variáveis de ambiente/role da task (nunca hardcoded); cobertura ≥90% de linha (JaCoCo) na camada de domínio/aplicação do relay.

**Ask First:** Nenhuma pendente — decidido com o usuário em 2026-09-08: esta story já inclui uma fila SQS FIFO + DLQ de teste via Testcontainers-LocalStack (primeiro precedente de LocalStack no projeto), assinante do tópico só para o teste de integração ler a mensagem publicada de volta e confirmar o envelope ponta a ponta.

**Never:** Implementar o lado consumidor (réplica local de Score em `matching-alocacao-service` — isso é Story 3.1); deploy do `triagem-score-service` no ECS/CDK (chore já adiado separadamente, `deferred-work.md`); os endpoints gRPC `ResolveCpfParaId`/`ObterCpfMascarado` (adiados, sem consumidor ainda); alterar o contrato síncrono de `POST /v1/triagens` (continua `201` imediato, sem esperar publicação).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Publicação normal | Linha em `eventos_outbox` não publicada | SNS `Publish` chamado com envelope correto; linha marcada publicada | N/A |
| SNS indisponível/erro transitório | `Publish` lança exceção | Linha permanece não publicada; próxima execução do job tenta de novo; nenhuma exceção propaga para fora do job | Log estruturado, sem crash da app |
| Falha após publicar, antes de marcar | Simular exceção entre ack do SNS e `UPDATE` da linha | Linha é republicada na próxima execução (at-least-once é aceitável; dedup fica a cargo do `MessageDeduplicationId`) | N/A |
| `POST /v1/triagens` sem header `X-Correlation-Id` | Chamada direta ao serviço, sem passar pelo gateway | `correlationId` gerado localmente (UUID v4) e gravado no outbox, nunca nulo | N/A |
| Múltiplas instâncias do job rodando | Duas instâncias do serviço escalonadas | Nenhuma linha é publicada duas vezes por corrida (lock otimista ou `SELECT ... FOR UPDATE SKIP LOCKED`) | N/A |
| Verificação ponta a ponta (LocalStack) | Fila SQS FIFO de teste assinante do tópico | Mensagem chega na fila com o envelope completo e `MessageGroupId` correto | N/A |

</frozen-after-approval>

## Code Map

- `triagem-score-service/src/main/java/com/filajusta/triagem/domain/EventoOutbox.java` -- record atual sem `version`/`correlationId`; precisa dos dois campos novos
- `triagem-score-service/src/main/java/com/filajusta/triagem/application/command/EventoOutboxRepositorio.java` -- porta de saída existente (`salvar`); precisa de novo método de leitura/marcação para o relay
- `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/EventoOutboxJpaEntity.java` -- entidade JPA da tabela `triagem_score.eventos_outbox`; adicionar colunas `version`, `correlation_id`, `publicado_em`
- `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/EventoOutboxRepositorioAdapter.java` -- adapter atual (serialização Jackson do payload)
- `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/EventoOutboxJpaRepository.java` -- sem query custom hoje; precisa de query de linhas pendentes
- `triagem-score-service/src/main/resources/db/migration/V1__create_triagem_schema.sql:46-52` -- schema atual da tabela (referência, não editar); nova migration `V2__` adiciona as 3 colunas
- `triagem-score-service/src/main/java/com/filajusta/triagem/application/command/RegistrarTriagem.java` -- comando que hoje grava o outbox sem correlationId
- `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/TriagemController.java:35` -- `POST /v1/triagens`; precisa ler `X-Correlation-Id` (padrão já usado em `gateway-service/.../CorrelationIdFilter.java:38`)
- `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/package-info.java:11-12` -- comentário hoje documenta o gap do relay; atualizar após implementar
- `triagem-score-service/pom.xml` -- adicionar `software.amazon.awssdk:sns`/`sqs` (nenhum SDK AWS runtime existe em nenhum módulo hoje) e `testcontainers:localstack` (nenhum precedente de LocalStack no projeto; Testcontainers hoje só cobre Postgres)
- `infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java:68-70` -- comentário já avisa que serviços de domínio ainda não têm padrão de infra; adicionar aqui o tópico SNS FIFO + policy IAM de publish
- `triagem-score-service/src/main/resources/application.yml` -- novo namespace `filajusta.triagem.relay.*` (nome do tópico, intervalo do poller), seguindo o precedente de `filajusta.triagem.limites.*`

## Tasks & Acceptance

**Execution:**
- [x] `V2__add_relay_columns_eventos_outbox.sql` -- migration Flyway adicionando `version`, `correlation_id`, `publicado_em` -- fecha o gap entre a tabela atual e o envelope já fixado no Epic 2
- [x] `EventoOutbox.java`, `EventoOutboxJpaEntity.java`, `EventoOutboxRepositorioAdapter.java` -- incluir os 3 campos novos -- envelope completo exige version+correlationId
- [x] `TriagemController.java`, `RegistrarTriagem.java` -- ler `X-Correlation-Id` (gerar UUID se ausente) e propagar até o outbox -- correlationId nunca foi capturado neste serviço
- [x] Novo `RelaySnsPublisherJob` (`infrastructure/relay/`) -- `@Scheduled`, lê linhas pendentes, publica no SNS FIFO, marca publicado -- é o relay em si
- [x] `FilaJustaStack.java` -- declarar tópico SNS FIFO `score-calculado.fifo` + IAM policy de publish para a task do `triagem-score-service` -- destino real da publicação
- [x] Testes unitários do job (mock do cliente SNS) cobrindo a I/O Matrix acima -- garante comportamento em falha parcial
- [x] Teste de integração com Testcontainers-LocalStack (SNS FIFO + fila SQS FIFO assinante + DLQ) -- confirma o envelope ponta a ponta, primeiro precedente de LocalStack no projeto

**Acceptance Criteria:**
- Given uma Triagem registrada com sucesso, when o job de relay roda, then o evento aparece publicado no tópico SNS FIFO com o envelope completo e `MessageGroupId = pacienteId`
- Given uma falha transitória do SNS, when o job roda novamente, then a mesma linha é publicada sem exigir intervenção manual e sem duplicar o registro no outbox

## Design Notes

Padrão outbox clássico: poller lê `WHERE publicado_em IS NULL ORDER BY id`, publica, faz `UPDATE ... SET publicado_em = now() WHERE id = ? AND publicado_em IS NULL` (guarda contra corrida entre instâncias). Não introduzir fila SQS intermediária entre outbox e SNS — o poller publica direto no tópico.

## Verification

**Commands:**
- `mvn -pl triagem-score-service -am verify` -- expected: testes verdes + JaCoCo ≥90% domínio/aplicação do relay
- `mvn -pl infra-cdk -am compile exec:java` -- expected: `cdk synth` sem erro com o novo tópico SNS declarado

## Suggested Review Order

**O relay em si**

- Entrada: lê o lote pendente sob lock, publica, marca -- todo o resto do arquivo existe para sustentar este método.
  [`RelaySnsPublisherJob.java:101`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/relay/RelaySnsPublisherJob.java#L101)

- `@Transactional` é o que faz o `FOR UPDATE SKIP LOCKED` da leitura realmente proteger a publicação, não só o `UPDATE` final (achado do code review).
  [`RelaySnsPublisherJob.java:100`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/relay/RelaySnsPublisherJob.java#L100)

- Publica e só marca após o ack do SNS; distingue falha de serialização de falha de publicação (achado do code review).
  [`RelaySnsPublisherJob.java:131`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/relay/RelaySnsPublisherJob.java#L131)

- Falha alto em vez de publicar `MessageGroupId="null"` quando o payload não tem `pacienteId` (achado do code review).
  [`RelaySnsPublisherJob.java:185`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/relay/RelaySnsPublisherJob.java#L185)

**Concorrência no outbox**

- `SELECT ... FOR UPDATE SKIP LOCKED` -- garante que duas instâncias do job nunca peguem a mesma linha pendente (achado do code review; a spec já exigia isso).
  [`EventoOutboxJpaRepository.java:26`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/EventoOutboxJpaRepository.java#L26)

- `UPDATE ... WHERE publicado_em IS NULL` -- segunda linha de defesa contra corrida, agora redundante com o lock de leitura mas inofensiva.
  [`EventoOutboxJpaRepository.java:39`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/EventoOutboxJpaRepository.java#L39)

- Colunas novas (`version`, `correlation_id`, `publicado_em`) `NOT NULL` sem `DEFAULT` -- seguro porque a tabela nunca foi implantada em ambiente real.
  [`V2__add_relay_columns_eventos_outbox.sql:8`](../../triagem-score-service/src/main/resources/db/migration/V2__add_relay_columns_eventos_outbox.sql#L8)

- Domínio valida `version >= 1` e `correlationId` não-nulo/não-branco -- garante que o envelope publicado nunca sai incompleto.
  [`EventoOutbox.java:42`](../../triagem-score-service/src/main/java/com/filajusta/triagem/domain/EventoOutbox.java#L42)

**Captura e validação de `correlationId`**

- Lê `X-Correlation-Id` do header (nunca lido antes por este serviço) e repassa ao comando.
  [`TriagemController.java:47`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/TriagemController.java#L47)

- Gera UUID se ausente; rejeita com `400` (não `500`) se maior que os 128 chars persistíveis (achado do code review).
  [`RegistrarTriagem.java:126`](../../triagem-score-service/src/main/java/com/filajusta/triagem/application/command/RegistrarTriagem.java#L126)

**Infraestrutura AWS**

- Tópico SNS FIFO + role dedicada com `grantPublish` -- primeiro recurso AWS de domínio no CDK, antes do deploy do próprio serviço (chore separado).
  [`FilaJustaStack.java:215`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L215)

- Cliente SNS com `apiCallTimeout`/`apiCallAttemptTimeout` -- evita travar a thread do scheduler numa chamada de rede pendurada (achado do code review).
  [`RelaySnsClientConfig.java:55`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/relay/RelaySnsClientConfig.java#L55)

**Testes**

- Prova a exclusão mútua real (duas transações concorrentes, Postgres real) -- não apenas o mock que simula a corrida.
  `EventoOutboxJpaRepositoryConcurrencyTest.java`

- Único teste com LocalStack no projeto -- SNS FIFO + fila SQS FIFO assinante + DLQ, ponta a ponta.
  `RelaySnsPublisherJobIntegrationTest.java`
