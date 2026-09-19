---
title: 'Comando LiberarRecurso e Persistência da Liberação Real (Story 3-4b1)'
type: 'feature'
created: '2026-09-13'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'a1a35c80ce41f0e37352bf17d14232c7ff3245a8'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Uma Alocação confirmada nunca tem como transicionar para `LIBERADA`, e um Recurso indisponível nunca tem como voltar a ficar disponível — não existe hoje nenhum caminho de código (comando, porta ou update de persistência) que realize a liberação real do Recurso, mesmo com a mensagem agendada já chegando na fila (Story 3-4a2).

**Approach:** Adicionar `Alocacao.STATUS_LIBERADA`, um método de update condicional idempotente `AlocacaoRepositorio#liberar(alocacaoId)` (ATIVA→LIBERADA), o espelho `RecursoRepositorio#marcarDisponivel`, e o novo comando `LiberarRecurso` que orquestra os dois numa única transação e grava `RecursoLiberado` no outbox (o relay SNS FIFO já existente publica sem mudança). Sem consumidor SQS nesta story — isso é a Story 3-4b2, deferida (`deferred-work.md`).

## Boundaries & Constraints

**Always:**
- Idempotência por `alocacaoId`: UPDATE condicional `WHERE status='ATIVA'` retornando linhas afetadas; 0 linhas (já liberada OU `alocacaoId` inexistente) → `LiberarRecurso` não chama `marcarDisponivel` nem grava evento — apenas retorna indicando no-op.
- Update da Alocação + `marcarDisponivel` + `salvar` do outbox na MESMA `@Transactional` (mesmo padrão de `ConfirmarAlocacao`).
- Envelope do evento outbox: `{eventId, eventType="RecursoLiberado", occurredAt, version=1, correlationId, payload}`, `payload` deve incluir `recursoId` (o relay SNS FIFO usa `payload.get("recursoId")` como `MessageGroupId`) e `alocacaoId`.
- `AlocacaoRepositorio#liberar` e `RecursoRepositorio#marcarDisponivel` seguem exatamente o estilo de `marcarComoPublicado`/`marcarComoEnviado` (`@Modifying @Query` nativo retornando `int`/`boolean`) e `marcarIndisponivel` (JPQL simples, void) respectivamente — sem lock otimista.
- Teste de concorrência real (Postgres via Testcontainers, `ExecutorService`/`CountDownLatch`, molde `UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest`) provando que N chamadas concorrentes de `LiberarRecurso` para a MESMA `alocacaoId` produzem exatamente 1 update efetivo e 1 linha de evento no outbox.

**Ask First:** Nenhuma decisão adicional pendente — o tratamento de `alocacaoId` inexistente (idêntico a "já liberada") já está decidido em Design Notes.

**Never:** Não criar `LiberacaoRecursoConsumerJob`, não tocar em `FilaJustaStack` (CDK) nem em `application.yml` — é escopo da Story 3-4b2 (deferida). Não criar endpoint REST de liberação manual. Não criar um novo relay SNS.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HAPPY_PATH | Alocação `ATIVA` existente para `alocacaoId`/`recursoId` | Status vira `LIBERADA`, Recurso fica disponível, `RecursoLiberado` gravado no outbox | N/A |
| JA_LIBERADA_OU_INEXISTENTE | `alocacaoId` já `LIBERADA` ou nunca existiu | UPDATE afeta 0 linhas — nenhum `marcarDisponivel`/evento; comando retorna no-op | N/A |
| CONCORRENCIA_REAL | 2+ chamadas simultâneas para a MESMA `alocacaoId` | Exatamente 1 chamada tem efeito (update+evento); as demais são no-op | N/A |

</frozen-after-approval>

## Code Map

- `matching-alocacao-service/.../domain/Alocacao.java:22` -- só existe `STATUS_ATIVA`; adicionar `STATUS_LIBERADA`.
- `matching-alocacao-service/.../application/command/AlocacaoRepositorio.java:16-19` -- porta, só tem `confirmar`; adicionar `boolean liberar(UUID alocacaoId)`.
- `matching-alocacao-service/.../infrastructure/persistence/AlocacaoJpaRepository.java:12-39` -- molde de UPDATE condicional: `EventoOutboxJpaRepository#marcarPublicado` / `LiberacaoAgendadaJpaRepository#marcarComoEnviado` (`@Modifying @Query` nativo retornando `int`).
- `matching-alocacao-service/.../infrastructure/persistence/AlocacaoRepositorioAdapter.java` -- implementar `liberar` delegando ao JPA repo, retorna `linhasAfetadas > 0`.
- `matching-alocacao-service/.../application/command/RecursoRepositorio.java:29-41` -- porta; `marcarIndisponivel` é o espelho exato para `marcarDisponivel` (void, idempotente, sem lock).
- `matching-alocacao-service/.../infrastructure/persistence/RecursoJpaRepository.java:61-63` -- `UPDATE RecursoJpaEntity r SET r.disponivel=false WHERE r.recursoId=:recursoId`; espelhar com `true`.
- `matching-alocacao-service/.../infrastructure/persistence/RecursoRepositorioAdapter.java:41-45` -- implementar `marcarDisponivel` no mesmo padrão.
- `matching-alocacao-service/.../application/command/ConfirmarAlocacao.java:91-142` -- molde de comando `@Transactional`: outbox via `eventoOutboxRepositorio.salvar(EventoOutbox)`, envelope `{eventId, eventType, occurredAt, version, correlationId, payload}`.
- `matching-alocacao-service/src/test/.../persistence/UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java:47-58,138-188` -- molde do teste de concorrência real (`@Testcontainers`, `PostgreSQLContainer<>("postgres:18")`, `ExecutorService`/`CountDownLatch`).
- `matching-alocacao-service/src/main/resources/db/migration/V5__create_alocacao.sql` -- `status TEXT NOT NULL` sem CHECK constraint — `LIBERADA` não exige migration nova.

## Tasks & Acceptance

**Execution:**
- [x] `Alocacao.java` -- adicionar `STATUS_LIBERADA` -- representa o novo estado terminal
- [x] `AlocacaoRepositorio.java` -- adicionar `boolean liberar(UUID alocacaoId)` -- update condicional idempotente
- [x] `AlocacaoJpaRepository.java` -- `@Modifying @Query` nativo `UPDATE ... SET status='LIBERADA' WHERE alocacao_id=:id AND status='ATIVA'`, retorna `int` -- molde `marcarComoPublicado`/`marcarComoEnviado`
- [x] `AlocacaoRepositorioAdapter.java` -- implementar `liberar` -- idempotência via linhas afetadas
- [x] `RecursoRepositorio.java` + `RecursoJpaRepository.java` + `RecursoRepositorioAdapter.java` -- adicionar `marcarDisponivel` -- espelho de `marcarIndisponivel`
- [x] `LiberarRecurso.java` (novo, `application/command`) -- `@Transactional liberar(alocacaoId, recursoId, correlationId)`: chama `alocacaoRepositorio.liberar`; se `true`, `recursoRepositorio.marcarDisponivel` + grava `RecursoLiberado` no outbox; se `false`, no-op -- idempotência central
- [x] Teste unitário `LiberarRecursoTest` -- happy path (verifica `marcarDisponivel` + evento capturado) e já-liberado/inexistente (verifica NENHUMA chamada a `marcarDisponivel`/outbox)
- [x] Teste de integração de concorrência real (molde `UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest`) -- N chamadas concorrentes de `liberar` para a mesma `alocacaoId` produzem só 1 efeito

**Acceptance Criteria:**
- Given uma Alocação `ATIVA`, when `LiberarRecurso.liberar` executa, then o status vira `LIBERADA`, o Recurso fica disponível e `RecursoLiberado` é gravado no outbox na mesma transação.
- Given a mesma `alocacaoId` já `LIBERADA` (ou inexistente), when `LiberarRecurso.liberar` executa de novo, then nenhum efeito ocorre (nem `marcarDisponivel`, nem novo evento).
- Given N chamadas concorrentes reais para a mesma `alocacaoId` (Postgres real), when todas executam simultaneamente, then exatamente 1 produz efeito.

## Spec Change Log

## Design Notes

A idempotência não distingue "já liberada" de "`alocacaoId` nunca existiu" — ambos os casos são 0 linhas afetadas pelo UPDATE condicional e resultam no mesmo no-op. Isso é intencional: distinguir os 2 casos exigiria uma consulta extra sem benefício prático nesta story (nenhum caminho do sistema hoje produz uma `alocacaoId` inexistente — o consumidor real que poderia expor esse cenário é a Story 3-4b2, deferida).

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, incluindo o novo teste de concorrência real

## Suggested Review Order

**Comando e transação real**

- Entrada principal: orquestra o update condicional + `marcarDisponivel` + outbox numa única transação — agora um bean Spring de verdade (ver concern seguinte).
  [`LiberarRecurso.java:67`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/LiberarRecurso.java#L67)

- `@Bean` explícito (achado do code review): sem isto, `@Transactional` acima seria inerte — mesmo padrão de `confirmarAlocacao`.
  [`MatchingAlocacaoServiceApplication.java:195`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/MatchingAlocacaoServiceApplication.java#L195)

**Update condicional idempotente (Alocação)**

- Novo estado terminal `LIBERADA`, atingido só a partir de `ATIVA`.
  [`Alocacao.java:32`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/Alocacao.java#L32)

- Porta: contrato de idempotência documentado (`true`/`false` por linhas afetadas).
  [`AlocacaoRepositorio.java:34`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/AlocacaoRepositorio.java#L34)

- UPDATE nativo condicional `WHERE status='ATIVA'` -- é este WHERE que garante exclusão mútua sob concorrência real, sem lock otimista.
  [`AlocacaoJpaRepository.java:53`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/AlocacaoJpaRepository.java#L53)

- Adapter: traduz linhas afetadas em `boolean`.
  [`AlocacaoRepositorioAdapter.java:62`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/AlocacaoRepositorioAdapter.java#L62)

**Liberação do Recurso (espelho de marcarIndisponivel)**

- Porta: espelho direto e deliberadamente simétrico de `marcarIndisponivel`.
  [`RecursoRepositorio.java:51`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/RecursoRepositorio.java#L51)

- JPQL simples, void, sem WHERE condicional -- idempotente por natureza (mandato da spec).
  [`RecursoJpaRepository.java:71`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoJpaRepository.java#L71)

- Adapter: delega direto, sem lógica extra.
  [`RecursoRepositorioAdapter.java:49`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoRepositorioAdapter.java#L49)

**Testes**

- Unitário: cobre HAPPY_PATH e JA_LIBERADA_OU_INEXISTENTE da I/O Matrix com mocks das 3 portas.
  [`LiberarRecursoTest.java:28`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/command/LiberarRecursoTest.java#L28)

- Concorrência real (Postgres via Testcontainers): 8 threads liberando a mesma `alocacaoId` -- prova CONCORRENCIA_REAL da I/O Matrix.
  [`LiberarRecursoRepositorioAdapterIntegrationTest.java:102`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/LiberarRecursoRepositorioAdapterIntegrationTest.java#L102)

