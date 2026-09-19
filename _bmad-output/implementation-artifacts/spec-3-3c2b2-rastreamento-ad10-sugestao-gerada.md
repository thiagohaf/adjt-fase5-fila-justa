---
title: 'Rastreamento AD-10: Publicação de SugestaoGerada em ConsultarSugestaoRecurso'
type: 'feature'
created: '2026-09-12'
status: 'done'
review_loop_iteration: 1
context: []
baseline_commit: 'fc951f5cb935c5132d3abc9f6b543b0870192156'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O rastreamento AD-10 (evento `SugestaoGerada` só quando a sugestão de um Recurso muda) tem sua infraestrutura de apoio pronta (porto `UltimaSugestaoRegistradaRepositorio`, Story 3-3c2b1) mas nenhum consumidor real ainda -- `ConsultarSugestaoRecurso` recalcula a sugestão a cada chamada sem nunca comparar com a anterior nem publicar nada.

**Approach:** `ConsultarSugestaoRecurso.consultar()` passa a comparar o `pacienteIdSugerido` calculado com o último registrado (`UltimaSugestaoRegistradaRepositorio#pacienteIdRegistrado`) e, quando muda (e o novo valor não é `null`), grava o novo registro e publica `SugestaoGerada` via outbox -- mesmo molde de `RecusarSugestao`/`ConfirmarAlocacao`.

## Boundaries & Constraints

**Always:**
- `consultar()` ganha `@Transactional` (NÃO `readOnly`) -- propagação `REQUIRED` precisa aceitar a escrita de bootstrap de `ConsultarFilaPriorizada#consultar` (`ScoreReplicaRepositorioAdapter#upsertSeMaisRecente`, `@Transactional` simples) quando ela participa da mesma transação; `readOnly=true` faria o Postgres rejeitar essa escrita (mesmo risco documentado em `FilaRepositorioAdapter`).
- Quando `pacienteIdSugerido != null`: chamar `registrar(recursoId, pacienteIdSugerido, agora)` SEM pré-ler `pacienteIdRegistrado` antes -- `registrar` vira um compare-and-set atômico no próprio SQL (ver Code Map: upsert nativo com `WHERE paciente_id <> excluded.paciente_id`, retornando `boolean`/linhas afetadas) para fechar a corrida de 2 requisições concorrentes lendo o mesmo valor antigo e publicando 2 eventos duplicados (achado convergente do code review multi-agente: blind-hunter + edge-case-hunter + verification-gap). Só quando `registrar` retorna `true` (linha realmente inserida/alterada): publicar `EventoOutbox` (`eventType="SugestaoGerada"`, `id=null`, `eventId=UUID.randomUUID()`, `version=1`, `correlationId=UUID.randomUUID()` sempre -- o `GET` não recebe `X-Correlation-Id` -- `payload={recursoId, pacienteId, sugeridoEm}`).
- `agora` (via `clock.instant()`) chamado uma única vez por consulta, reaproveitado no registro e no payload -- mesmo padrão de `RecusarSugestao`.
- Quando `pacienteIdSugerido` é `null` (fila esgotada/recurso indisponível), NENHUMA chamada a `registrar` nem evento -- mesmo havendo um registro anterior diferente.
- `UltimaSugestaoRegistradaRepositorio#registrar` (porto da Story 3-3c2b1, JÁ MERGEADO) muda de `void` para `boolean` (retorna se a linha foi de fato inserida/alterada) -- `pacienteIdRegistrado` (leitura) do mesmo porto permanece inalterado e sem uso nesta story (fica disponível para consumidores futuros).

**Ask First:** nenhuma decisão nova requer aprovação humana durante a execução desta story.

**Never:** alterar o formato do JSON de resposta do endpoint; tocar o pulo de recusados (`SugestaoRecusadaConsultaRepositorio`, Story 3-3c2a); adicionar lock explícito (`SELECT ... FOR UPDATE`) ou qualquer mecanismo além do `WHERE` condicional no upsert nativo para resolver a concorrência.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Primeira sugestão do Recurso | Nenhum registro anterior; `pacienteIdSugerido=X` | Registra `X`; publica `SugestaoGerada` com `pacienteId=X` | N/A |
| Sugestão muda | Registrado `X`; calculado `Y` (`Y≠X`, `Y≠null`) | Upsert para `Y`; publica `SugestaoGerada` com `pacienteId=Y` | N/A |
| Sugestão repete | Registrado `X`; calculado `X` | Nenhuma escrita; nenhum evento | N/A |
| Fila esgota após sugestão anterior | Registrado `X`; calculado `null` | Nenhuma escrita; nenhum evento (registro `X` permanece) | N/A |
| 2 requisições concorrentes, mesmo `recursoId` | Ambas calculam `Y` (`Y≠X`), correm juntas | Apenas 1 chamada a `registrar` retorna `true` (a que efetivamente muda a linha); só ela publica `SugestaoGerada` -- nunca 2 eventos duplicados | N/A |

</frozen-after-approval>

## Code Map

- `application/command/UltimaSugestaoRegistradaRepositorio.java` (porto da Story 3-3c2b1, JÁ MERGEADO) -- `registrar` muda assinatura de `void` para `boolean`; `pacienteIdRegistrado` inalterado
- `infrastructure/persistence/UltimaSugestaoRegistradaJpaRepository.java:upsert` (Story 3-3c2b1) -- adicionar `WHERE ultima_sugestao_registrada.paciente_id <> excluded.paciente_id` na cláusula `ON CONFLICT DO UPDATE`; retorno muda de `void` para `int` (linhas afetadas, herdado de `@Modifying`)
- `infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapter.java:registrar` (Story 3-3c2b1) -- retorna `jpaRepository.upsert(...) > 0`
- `test/infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java` (Story 3-3c2b1) -- novo teste: `registrar` com o MESMO `pacienteId` já registrado retorna `false` e não altera `registrado_em`; testes existentes continuam válidos (nenhum chama `registrar` 2x com o mesmo valor)
- `application/query/ConsultarSugestaoRecurso.java:51-90` -- `@Transactional` + chamada direta a `registrar` (sem pré-leitura) + publicação condicionada ao retorno `true`; construtor ganha `UltimaSugestaoRegistradaRepositorio`, `EventoOutboxRepositorio`, `Clock` (molde de `RecusarSugestao.java`)
- `MatchingAlocacaoServiceApplication.java:119-125` -- bean `consultarSugestaoRecurso` ganha as 3 dependências novas (`Clock` já existe como bean)
- `test/application/query/ConsultarSugestaoRecursoTest.java` -- atualizar construtor de todos os testes existentes (adicionar mocks + `CLOCK`, constante já declarada e não usada); novos testes cobrindo a I/O Matrix (`verify(...).registrar(...)` retornando `true`/`false`, `verify(...).salvar(...)`, incluindo os casos de "nenhuma escrita"); `pacienteIdRegistrado` NUNCA verificado/chamado nesta classe (não é mais lido por `ConsultarSugestaoRecurso`)
- `test/RecursoSugestaoControllerIntegrationTest.java` -- novos testes E2E: `ultima_sugestao_registrada` e `eventos_outbox` (`event_type='SugestaoGerada'`) via `jdbcTemplate`, molde de `AlocacaoControllerIntegrationTest.java:229-255`; incluir 1 cenário de transição real `A→B` (não só primeiro registro) e 1 cenário "fila esgota após sugestão já registrada" (nenhuma escrita/evento) a nível HTTP/Postgres real; incluir 1 cenário com `score_replica` vazia (bootstrap a frio) através deste endpoint, provando que `@Transactional` não-`readOnly` aceita a escrita de bootstrap (achado do verification-gap: esse caminho nunca era exercitado)

## Tasks & Acceptance

**Execution:**
- [x] `UltimaSugestaoRegistradaJpaRepository.java` + `UltimaSugestaoRegistradaRepositorioAdapter.java` + porto -- `registrar` atômico (`boolean`, `WHERE` condicional) -- fecha a corrida de eventos duplicados (achado convergente do code review)
- [x] `UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java` -- novo teste cobrindo `registrar` com valor repetido retornando `false`
- [x] `ConsultarSugestaoRecurso.java` -- implementar `@Transactional` + `registrar` direto (sem pré-leitura) + publicação condicionada ao retorno -- aplica AD-10
- [x] `MatchingAlocacaoServiceApplication.java` -- wiring das 3 dependências novas no bean
- [x] `ConsultarSugestaoRecursoTest.java` -- atualizar construtor + novos testes unitários da I/O Matrix (incluindo o retorno `boolean` de `registrar`)
- [x] `RecursoSugestaoControllerIntegrationTest.java` -- novos testes E2E: transição real `A→B`, fila esgota após registro anterior, e bootstrap a frio via este endpoint

**Acceptance Criteria:**
- Given a sugestão muda em relação ao último registro, when `GET /v1/recursos/{id}/sugestao` é chamado, then `ultima_sugestao_registrada` é atualizada e uma linha `SugestaoGerada` (`publicado_em IS NULL`) aparece em `eventos_outbox`
- Given a sugestão repete o último registro, when `GET /v1/recursos/{id}/sugestao` é chamado de novo, then nenhuma nova linha de evento é criada e `ultima_sugestao_registrada` não muda
- Given `registrar` é chamado com o mesmo `pacienteId` já persistido, when a chamada retorna, then retorna `false` e `registrado_em` não muda
- Given `mvn -pl matching-alocacao-service -am verify`, then testes verdes, JaCoCo domínio/aplicação ≥90%

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%

## Suggested Review Order

**Rastreamento AD-10 (entry point)**

- `@Transactional` não-`readOnly` -- precisa aceitar a escrita de bootstrap de `ConsultarFilaPriorizada` na mesma transação.
  [`ConsultarSugestaoRecurso.java:113`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarSugestaoRecurso.java#L113)

- Chama `registrar` direto (sem pré-ler o valor anterior); só publica `SugestaoGerada` quando `registrar` retorna `true`.
  [`ConsultarSugestaoRecurso.java:145-153`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarSugestaoRecurso.java#L145-L153)

**Compare-and-set atômico (fecha a corrida de eventos duplicados)**

- `WHERE paciente_id <> excluded.paciente_id` no upsert nativo -- só conta como linha afetada quando o valor de fato muda.
  [`UltimaSugestaoRegistradaJpaRepository.java:42-44`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/UltimaSugestaoRegistradaJpaRepository.java#L42-L44)

- Adapter devolve `linhasAfetadas > 0` como o `boolean` que decide a publicação do evento.
  [`UltimaSugestaoRegistradaRepositorioAdapter.java:41`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapter.java#L41)

- Porto muda de `void` para `boolean` -- contrato agora expõe se a escrita de fato aconteceu.
  [`UltimaSugestaoRegistradaRepositorio.java:49`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/UltimaSugestaoRegistradaRepositorio.java#L49)

**Wiring**

- Bean `consultarSugestaoRecurso` ganha as 3 dependências novas.
  [`MatchingAlocacaoServiceApplication.java:124`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/MatchingAlocacaoServiceApplication.java#L124)

**Testes -- prova de concorrência real**

- N threads via `ExecutorService`/`CountDownLatch` contra Postgres real -- exatamente 1 retorna `true`, fechando a duplicação de eventos.
  [`UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java:137`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java#L137)

**Testes E2E (HTTP + Postgres real)**

- Transição real `A→B` -- 2 eventos `SugestaoGerada` distintos, mesma linha de `ultima_sugestao_registrada` atualizada.
  [`RecursoSugestaoControllerIntegrationTest.java:297`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/RecursoSugestaoControllerIntegrationTest.java#L297)

- Bootstrap a frio através deste endpoint -- prova que `@Transactional` não-`readOnly` aceita a escrita de bootstrap.
  [`RecursoSugestaoControllerIntegrationTest.java:410`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/RecursoSugestaoControllerIntegrationTest.java#L410)

- Assert do campo `sugeridoEm` do payload publicado (não só `pacienteId`).
  [`RecursoSugestaoControllerIntegrationTest.java:331-333`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/RecursoSugestaoControllerIntegrationTest.java#L331-L333)

**Testes unitários**

- Primeira sugestão registra e publica -- cobre a I/O Matrix com mocks.
  [`ConsultarSugestaoRecursoTest.java:267`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/query/ConsultarSugestaoRecursoTest.java#L267)

- 2 chamadas sequenciais ao caso de uso -- nome corrigido no review para não sugerir concorrência real (essa fica no teste do adapter).
  [`ConsultarSugestaoRecursoTest.java:336`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/query/ConsultarSugestaoRecursoTest.java#L336)
