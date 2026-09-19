---
title: 'Story 3-3a — Infraestrutura Outbox Própria do matching-alocacao-service'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 1
context: []
baseline_commit: 'bfdf87784ad64ead2fbf33a1b019caff10b12a5a'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `matching-alocacao-service` ainda não publica nenhum evento próprio — só consome (`ScoreCalculado`). As Stories 3.3b (confirmação) e 3.3c (recusa) vão precisar gravar `AlocacaoConfirmada`/`SugestaoRecusada`/`SugestaoGerada` na mesma transação de um comando (AD-3) e publicá-los de forma assíncrona e confiável, mas não existe hoje nenhuma peça de infraestrutura outbox neste serviço.

**Approach:** Replicar neste serviço, sem nenhum produtor real ainda, o mesmo padrão outbox já provado em `triagem-score-service` (Story 3.0/2.1): tabela `eventos_outbox`, domínio `EventoOutbox`, porta/adapter de persistência, e um `RelaySnsPublisherJob` agendado que publica no SNS FIFO e marca a linha como publicada de forma idempotente. Pré-requisito puro de infraestrutura para 3.3b/3.3c — sem HTTP, sem domínio de negócio novo.

## Boundaries & Constraints

**Always:**
- `EventoOutbox` espelha a forma de `triagem-score-service/.../domain/EventoOutbox.java`: `id, eventId(UUID v4), eventType(String), occurredAt(Instant), version(int), correlationId(String não-branco), payload(Map<String,Object> imutável)`.
- Tabela `matching_alocacao.eventos_outbox`: mesmas colunas/tipos de `triagem_score.eventos_outbox` após `V1__create_triagem_schema.sql` + `V2__add_relay_columns_eventos_outbox.sql` combinadas em uma única migration nova (`event_id UNIQUE`, `payload jsonb`, `version`, `correlation_id`, `publicado_em` nullable), incluindo o índice parcial `WHERE publicado_em IS NULL` para leitura eficiente de pendentes.
- `EventoOutboxRepositorio` (porta): `salvar(EventoOutbox)`, `buscarNaoPublicados(int limite)`, `boolean marcarComoPublicado(long id)` — assinatura idêntica à porta homônima de `triagem-score-service`.
- Leitura de pendentes usa `FOR UPDATE SKIP LOCKED` (mesma query nativa de `EventoOutboxJpaRepository.java` do `triagem-score-service`) para permitir múltiplas instâncias do job sem duplicar publicação.
- `RelaySnsPublisherJob`: `@Scheduled` + `@Transactional`, monta o envelope `{eventId, eventType, occurredAt, version, correlationId, payload}`, publica no SNS com `messageGroupId` extraído do payload (aqui sempre `recursoId`, não `pacienteId`) e `messageDeduplicationId = eventId`; só marca a linha como publicada após confirmação (ack) do broker — uma falha de rede antes do ack não perde nem duplica o evento (`eventId` nunca regenerado).
- Configuração (`application.yml`, chaves `filajusta.matching.*`) segue o mesmo padrão/nomes de `filajusta.triagem.relay.*` em `triagem-score-service/src/main/resources/application.yml` (kill switch `enabled` + `topic-arn`), adaptado ao namespace deste serviço sem colidir com `filajusta.matching.relay.*` já usado pelo consumidor SQS de `ScoreCalculado`.
- Cobertura de teste: unitário para `EventoOutboxRepositorioAdapter` (serialização do payload) e integração (Testcontainers) para `RelaySnsPublisherJob` — publica uma linha inserida diretamente via `EventoOutboxRepositorio.salvar(...)` em teste, confirma envelope e idempotência (job rodando duas vezes não republica).
- Tópico SNS FIFO próprio deste serviço (`matching-alocacao-eventos.fifo`) provisionado em `infra-cdk` (`FilaJustaStack.java`), mesmo padrão de `buildScoreCalculadoTopic()`/`buildTriagemScoreServiceTaskRole()` (Story 3.0) — publish concedido à `MatchingAlocacaoServiceTaskRole` já existente (Story 3.1b), sem criar role nova. `filajusta.matching.outbox-relay.enabled` volta ao default `true` (mesmo padrão real de `filajusta.triagem.relay.enabled`), já que o tópico passa a existir; `topic-arn` continua vazio por padrão (`${FILAJUSTA_MATCHING_OUTBOX_RELAY_TOPIC_ARN:}`, injetado só no deploy real, ainda pendente — gap pré-existente documentado no javadoc de `FilaJustaStack`, não desta story). Os testes `@SpringBootTest` pré-existentes deste serviço que não exercitam o relay devem setar `filajusta.matching.outbox-relay.enabled=false` explicitamente, mesmo padrão já usado pelos testes homônimos de `triagem-score-service` (`RegistrarTriagemIntegrationTest`/`ConsultarTriagemIntegrationTest` com `filajusta.triagem.relay.enabled=false`).

**Ask First:** Nenhuma pendência — resolvida (ver Spec Change Log).

**Never:**
- Não cria `domain/Alocacao`, `ConfirmarAlocacao`, `RecusarSugestao` nem qualquer endpoint HTTP novo — isso é 3.3b/3.3c.
- Não grava nenhuma linha de `eventos_outbox` a partir de um caso de uso real nesta story — nenhum produtor existe ainda; a única gravação é a do teste de integração do relay.
- Não modifica `ConsultarFilaPriorizada`, `ConsultarSugestaoRecurso` ou o consumidor SQS de `ScoreCalculado` já existentes.

</frozen-after-approval>

## Code Map

- `domain/EventoOutbox.java` (novo) -- mesma forma de `triagem-score-service/src/main/java/com/filajusta/triagem/domain/EventoOutbox.java`
- `application/command/EventoOutboxRepositorio.java` (novo) -- porta igual `triagem-score-service/.../application/command/EventoOutboxRepositorio.java` (`salvar`/`buscarNaoPublicados`/`marcarComoPublicado`)
- `infrastructure/persistence/EventoOutboxJpaEntity.java` (novo) -- `@Table(name="eventos_outbox", schema="matching_alocacao")`, `event_id UNIQUE`, `payload` via `@JdbcTypeCode(SqlTypes.JSON)`, mesmo padrão de `triagem-score-service/.../EventoOutboxJpaEntity.java`
- `infrastructure/persistence/EventoOutboxJpaRepository.java` (novo) -- query nativa `FOR UPDATE SKIP LOCKED` + `UPDATE ... WHERE publicado_em IS NULL`, mesmo padrão do homônimo em `triagem-score-service`
- `infrastructure/persistence/EventoOutboxRepositorioAdapter.java` (novo) -- serializa payload via Jackson `ObjectMapper`, mesmo padrão do homônimo em `triagem-score-service`
- `infrastructure/relay/RelaySnsPublisherJob.java` (novo) -- `@Scheduled`+`@Transactional`, envelope + `MessageGroupId=recursoId`, mesmo padrão de `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/relay/RelaySnsPublisherJob.java:197-206`
- `resources/db/migration/V4__create_eventos_outbox.sql` (novo) -- schema completo (equivalente a `V1__create_triagem_schema.sql` + `V2__add_relay_columns_eventos_outbox.sql` combinadas) para `matching_alocacao.eventos_outbox`
- `src/main/resources/application.yml` (modificado) -- novas chaves `filajusta.matching.*` para o relay (enabled/topic-arn), mesmo padrão de `filajusta.triagem.relay.*` em `triagem-score-service/src/main/resources/application.yml`
- `MatchingAlocacaoServiceApplication.java` (modificado) -- javadoc apenas; sem `@Bean` explícito -- `EventoOutboxRepositorioAdapter`/`RelaySnsPublisherJob`/`RelaySnsClientConfig` são `@Component`/`@Configuration` auto-detectados via component scan, mesmo padrão real de `RecursoRepositorioAdapter` (confirmado no código: nenhum adapter deste serviço é declarado via `@Bean`, só os casos de uso em `application/`) -- diverge do texto original desta linha, que pedia `@Bean` explícito
- `infrastructure/relay/RelaySnsClientConfig.java` (novo, fora do Code Map original) -- `@Configuration` que fornece o bean `SnsClient`, necessário para o relay compilar; mesmo padrão do homônimo em `triagem-score-service`
- `pom.xml` (modificado, fora do Code Map original) -- adiciona dependência `software.amazon.awssdk:sns`, necessária para compilar o relay
- `infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java` (modificado, emenda) -- novo método `buildMatchingAlocacaoEventosTopic()` (tópico SNS FIFO `matching-alocacao-eventos.fifo`, mesmo padrão de `buildScoreCalculadoTopic()`), `grantPublish` na `MatchingAlocacaoServiceTaskRole` já existente (captura o retorno de `buildMatchingAlocacaoServiceTaskRole(...)`, não cria role nova) e `CfnOutput` `MatchingAlocacaoEventosTopicArn`
- `infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java` (modificado, patch do code review) -- `matchingAlocacaoEventosTopicIsFifo()` e `matchingAlocacaoServiceTaskRoleCanPublishToMatchingAlocacaoEventosTopic()`, mesmo padrão de `scoreCalculadoTopicIsFifo()`/`triagemScoreServiceTaskRoleCanPublishToScoreCalculadoTopic()`

## Tasks & Acceptance

**Execution:**
- [x] `V4__create_eventos_outbox.sql` -- schema `eventos_outbox` completo (colunas + índice parcial de pendentes)
- [x] `domain/EventoOutbox.java` -- domínio imutável
- [x] `application/command/EventoOutboxRepositorio.java` -- porta
- [x] `infrastructure/persistence/EventoOutboxJpaEntity.java`/`EventoOutboxJpaRepository.java`/`EventoOutboxRepositorioAdapter.java` -- persistência
- [x] `infrastructure/relay/RelaySnsPublisherJob.java`/`RelaySnsClientConfig.java` -- relay agendado, envelope, `MessageGroupId=recursoId`, idempotência
- [x] `application.yml`/`MatchingAlocacaoServiceApplication.java`/`pom.xml` -- config + wiring + dependência SNS
- [x] Teste unitário `EventoOutboxRepositorioAdapterTest` -- serialização de payload
- [x] Teste de integração (Testcontainers) `RelaySnsPublisherJobIntegrationTest` -- publica, marca publicado, roda 2x sem republicar
- [x] Teste unitário `RelaySnsPublisherJobTest` (mock de `SnsClient`, sem Testcontainers) -- cobre `falhaTransitoriaDoSnsNaoPropagaExcecaoENaoMarcaALinhaComoPublicada` (prova a 3ª AC abaixo por construção: `marcarComoPublicado` nunca chamado em falha de rede antes do ack, logo `eventId` é reencontrado inalterado na próxima execução), além de "nenhuma linha pendente" e "topic-arn vazio falha na construção"
- [x] `infra-cdk/.../FilaJustaStack.java` -- tópico SNS FIFO `matching-alocacao-eventos.fifo` provisionado, `grantPublish` na `MatchingAlocacaoServiceTaskRole` existente (sem criar role nova), `CfnOutput` `MatchingAlocacaoEventosTopicArn`; `mvn -pl infra-cdk -am compile` e `cdk synth` verdes
- [x] `application.yml` -- `filajusta.matching.outbox-relay.enabled` volta a `true` por padrão (tópico real provisionado), comentário `[ASSUMPTION]` removido/reescrito
- [x] 8 testes `@SpringBootTest` pré-existentes ajustados para desligar `filajusta.matching.outbox-relay.enabled=false` explicitamente (`ScoreCalculadoConsumerJobIntegrationTest`, `RecursoSugestaoControllerIntegrationTest`, `FilaBootstrapIntegrationTest`, `UpsertRecursoIntegrationTest`, `ScoreReplicaRepositorioAdapterIntegrationTest`, `RecursoRepositorioAdapterIntegrationTest`, `ScoreCalculadoConsumerJobConditionalOnPropertyDisabledTest`, `RecursoConsultaRepositorioAdapterIntegrationTest`)
- [x] Patch (code review): javadoc desatualizado de `RelaySnsClientConfig.java`/`RelaySnsPublisherJob.java` (dizia default `false`) corrigido para refletir o default real `true`
- [x] Patch (code review): `chaveDeBloqueio(EventoOutbox)` extraído em `RelaySnsPublisherJob.java` -- `recursosBloqueadosNestaExecucao` normaliza `recursoId` com `String.valueOf(...)` (mesma normalização de `messageGroupId(...)`), evitando que um `Long`/`String` do mesmo `recursoId` escape do bloqueio FIFO
- [x] Patch (code review): `FilaJustaStackTest.java` -- 2 testes novos para o tópico/role deste serviço
- [x] Patch (code review): `RelaySnsPublisherJobTest.java` -- 4 testes novos (`falhaNaSegundaLinhaInterrompeOLotePreservandoOrdemFifoDoRecurso`, `falhaDeUmRecursoNaoBloqueiaAPublicacaoDeOutrosRecursosNoMesmoLote`, `messageGroupIdAusenteNoPayloadFalhaAPublicacaoSemChamarSnsNemMarcar`, `batchSizeMenorOuIgualAZeroEClampeadoParaUm`)

**Acceptance Criteria:**
- Given uma linha `EventoOutbox` salva em transação, when `RelaySnsPublisherJob` roda, then é publicada no SNS com o envelope correto (`eventId, eventType, occurredAt, version, correlationId, payload`) e `MessageGroupId=recursoId` -- coberta por `RelaySnsPublisherJobIntegrationTest`
- Given uma linha já marcada como publicada, when o job roda novamente, then não é republicada -- coberta por `RelaySnsPublisherJobIntegrationTest`
- Given uma falha de rede simulada antes do ack do SNS, when o job roda de novo, then o mesmo `eventId` é reusado (nunca regenerado) -- sem duplicação nem perda -- coberta por `RelaySnsPublisherJobTest.falhaTransitoriaDoSnsNaoPropagaExcecaoENaoMarcaALinhaComoPublicada`

## Spec Change Log

- **2026-09-11, loop 1 (ask_first_resolution):** A cláusula "Ask First" original foi acionada durante a implementação: nenhum tópico SNS próprio deste serviço estava provisionado/documentado. Diante disso, a primeira rodada de implementação optou por um default conservador (`filajusta.matching.outbox-relay.enabled=false`) sem parar para perguntar, documentado como `[ASSUMPTION]`. Levado ao usuário para decisão explícita: **provisionar o tópico SNS agora** em vez de manter o relay desligado. Amendado: nova regra em Boundaries "Always" (tópico `matching-alocacao-eventos.fifo` em `infra-cdk`, publish concedido à `MatchingAlocacaoServiceTaskRole` existente, `enabled` volta a `true` por padrão) e a cláusula "Ask First" marcada como resolvida. **KEEP:** todo o resto da infraestrutura outbox (domínio, persistência, relay job, envelope, idempotência) já implementado na primeira rodada está correto e não muda.

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%
- `mvn -pl infra-cdk -am compile` -- expected: BUILD SUCCESS (novo tópico `matching-alocacao-eventos.fifo`)
- `cdk synth` (em `infra-cdk/`) -- expected: sintetiza sem erro; template mostra `MatchingAlocacaoEventosTopic` (SNS FIFO), `sns:Publish` na policy de `MatchingAlocacaoServiceTaskRole` e o output `MatchingAlocacaoEventosTopicArn`
- `mvn -pl infra-cdk -am test` -- expected: testes verdes (25, incluindo os 2 novos de `FilaJustaStackTest`)

## Suggested Review Order

**Domínio outbox (entrada)**

- `EventoOutbox`, mesma forma de `triagem-score-service` -- id, eventId (nunca regenerado), version/correlationId validados no construtor.
  [`EventoOutbox.java:34`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/EventoOutbox.java#L34)

**Relay/publisher (núcleo da lógica)**

- Fail-fast na construção: `topic-arn` vazio com o relay habilitado derruba a subida em vez de falhar em silêncio a cada ciclo.
  [`RelaySnsPublisherJob.java:92`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/RelaySnsPublisherJob.java#L92)

- Ponto de entrada do poller: lê pendentes, isola falhas por `recursoId` sem bloquear outros Recursos no mesmo lote.
  [`RelaySnsPublisherJob.java:107`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/RelaySnsPublisherJob.java#L107)

- `chaveDeBloqueio` extraído no patch do code review -- normaliza `recursoId` com `String.valueOf`, mesma chave usada no `MessageGroupId` do SNS.
  [`RelaySnsPublisherJob.java:205`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/RelaySnsPublisherJob.java#L205)

- Publica e só marca a linha como publicada após o ack do SNS -- nunca antes.
  [`RelaySnsPublisherJob.java:144`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/RelaySnsPublisherJob.java#L144)

**Persistência**

- `FOR UPDATE SKIP LOCKED` só protege corrida entre instâncias quando chamado dentro da mesma transação do job (risco documentado, deferido).
  [`EventoOutboxRepositorioAdapter.java:56`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/EventoOutboxRepositorioAdapter.java#L56)

- Schema novo do zero já com as colunas do relay desde a primeira versão (sem a lacuna histórica de `triagem-score-service`).
  [`V4__create_eventos_outbox.sql:14`](../../matching-alocacao-service/src/main/resources/db/migration/V4__create_eventos_outbox.sql#L14)

**Infraestrutura AWS (emenda -- tópico provisionado)**

- Tópico SNS FIFO próprio deste serviço, `grantPublish` reusa a `MatchingAlocacaoServiceTaskRole` existente em vez de criar uma nova.
  [`FilaJustaStack.java:331`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L331)

- Wiring do tópico + grant + output no fluxo principal da stack.
  [`FilaJustaStack.java:236`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L236)

**Config**

- `outbox-relay.enabled: true` por padrão (tópico real provisionado), `topic-arn` vazio até o deploy ECS real (gap pré-existente).
  [`application.yml:85`](../../matching-alocacao-service/src/main/resources/application.yml#L85)

**Testes**

- Prova (patch do code review) que a falha em um Recurso não bloqueia outro no mesmo lote, e que a ordem FIFO por Recurso é preservada.
  [`RelaySnsPublisherJobTest.java`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/relay/RelaySnsPublisherJobTest.java#L1)

- Ponta a ponta via Testcontainers + LocalStack: envelope completo, `MessageGroupId=recursoId`, idempotência.
  [`RelaySnsPublisherJobIntegrationTest.java`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/relay/RelaySnsPublisherJobIntegrationTest.java#L1)

- Testes novos (patch) provando FIFO e permissão IAM do tópico no CDK.
  [`FilaJustaStackTest.java:367`](../../infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java#L367)
