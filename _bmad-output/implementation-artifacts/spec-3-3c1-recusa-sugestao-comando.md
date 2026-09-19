---
title: 'Comando de Recusa da Sugestão de Matching'
type: 'feature'
created: '2026-09-12'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '1273156a002a662e72c21dcc416a98f2a1ea29b1'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Não existe caminho para recusar uma sugestão de Matching — `ConfirmarAlocacao` (3-3b1) só cobre confirmação. Sem registrar o par `(recursoId, pacienteId)` recusado, não há como impedir que o mesmo Paciente seja resugerido para o mesmo Recurso (isso fica para 3-3c2, que consome esta persistência).

**Approach:** Novo comando `RecusarSugestao` (`POST /v1/recursos/{id}/alocacoes/recusa`, motivo obrigatório) grava o par recusado numa tabela nova (`sugestao_recusada`) e publica `SugestaoRecusada` via outbox — mesmo padrão de `ConfirmarAlocacao`. Não altera `ConsultarSugestaoRecurso` nem o algoritmo de sugestão nesta story.

## Boundaries & Constraints

**Always:**
- `RecusarSugestao` segue o molde exato de `ConfirmarAlocacao.java:50-121`: construtor com portas injetadas + `Clock`, método `@Transactional`, `EventoOutbox` com `eventId=UUID.randomUUID()`, `version=1` (`VERSAO_INICIAL_EVENTO`), payload com `recursoId` no nível raiz (contrato exigido por `RelaySnsPublisherJob.messageGroupId`, `infrastructure/relay/RelaySnsPublisherJob.java:205-219`).
- `RecusarSugestaoRequest(Long pacienteId, String motivo)` — `@NotNull @Positive` em `pacienteId`, `@NotBlank` em `motivo`; validação via `MethodArgumentNotValidException`, já tratada por `RecursosExceptionHandler` (400) — nenhum handler novo.
- Recurso inexistente → `RecursoNaoEncontradoException` (404, handler já existe, reutilizar `recursoConsultaRepositorio.buscarPorId`).
- `correlationId` segue o mesmo tratamento de `ConfirmarAlocacao.correlationIdEfetivo` (`ConfirmarAlocacao.java:102-111`): gera UUID se ausente/em branco, `CorrelationIdInvalidoException` (400) se `> 128` caracteres.
- Persistência da recusa é upsert idempotente por PK composta `(recurso_id, paciente_id)` — recusar o mesmo par de novo apenas atualiza `motivo`/`recusado_em`, nunca duplica linha nem falha.
- Migration nova `V6__create_sugestao_recusada.sql`: schema `matching_alocacao`, tabela `sugestao_recusada` (`recurso_id UUID`, `paciente_id BIGINT`, `motivo TEXT`, `recusado_em TIMESTAMPTZ`, `PRIMARY KEY (recurso_id, paciente_id)`) — mesmo estilo de `V5__create_alocacao.sql`.
- Endpoint retorna `201` com corpo mínimo (`recursoId`, `pacienteId`, `motivo`, `recusadoEm`) — mesmo padrão de `AlocacaoResponse`.

**Ask First:** qualquer necessidade de infraestrutura/schema não prevista aqui — HALT antes de prosseguir.

**Never:** alterar `ConfirmarAlocacao`/`Alocacao`/domínio `Alocacao` (recusa não usa a tabela `alocacao`, nem status `"RECUSADA"`); alterar `ConsultarSugestaoRecurso` ou o algoritmo de tiers (fica para 3-3c2); alterar contrato JSON de `GET /v1/recursos/{id}/sugestao` ou `GET /v1/fila`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Recusa feliz | Recurso existe, `pacienteId` e `motivo` preenchidos | `201`, par gravado em `sugestao_recusada`, `SugestaoRecusada` publicado no outbox | N/A |
| Motivo ausente/em branco | `motivo=""` ou omitido | `400` | `MethodArgumentNotValidException` |
| `pacienteId` ausente/não positivo | `pacienteId=null` ou `<= 0` | `400` | `MethodArgumentNotValidException` |
| Recurso inexistente | `id` não existe em `recurso` | `404` | `RecursoNaoEncontradoException` |
| Recusa duplicada | Mesmo par `(recursoId, pacienteId)` já recusado antes | `201` de novo, upsert atualiza `motivo`/`recusado_em` sem duplicar linha | N/A |
| `correlationId` inválido | Header `X-Correlation-Id` `> 128` caracteres | `400` | `CorrelationIdInvalidoException` |

</frozen-after-approval>

## Code Map

Base: `matching-alocacao-service/src/{main,test}/java/com/filajusta/matching/`

- `application/command/ConfirmarAlocacao.java:50-121` (referência, não tocar) — molde exato para `RecusarSugestao`; ver especialmente `correlationIdEfetivo` (102-111) e construção do `EventoOutbox` (94-97)
- `application/command/ConfirmarAlocacao.java` `payloadAlocacaoConfirmada` (privado) — molde para `payloadSugestaoRecusada` (payload precisa ter `recursoId` no nível raiz)
- `application/command/RecursoNaoEncontradoException.java` — reaproveitar? checar se hoje só existe em `application/query`; se for a mesma exceção, importar de lá (era usada por `ConfirmarAlocacao` também — confirmar import exato antes de duplicar)
- `application/command/RecusarSugestao.java` (novo) — `recusar(UUID recursoId, long pacienteId, String motivo, String correlationId): void`
- `application/command/SugestaoRecusadaRepositorio.java` (novo, porta) — `void registrar(UUID recursoId, long pacienteId, String motivo, Instant recusadoEm)`
- `application/command/EventoOutboxRepositorio.java:36` (só consumido) — `salvar(EventoOutbox)`
- `infrastructure/persistence/AlocacaoJpaEntity.java`, `AlocacaoJpaRepository.java`, `AlocacaoRepositorioAdapter.java` (referência de estilo) — molde para `SugestaoRecusadaJpaEntity`/`SugestaoRecusadaJpaRepository`/`SugestaoRecusadaRepositorioAdapter` (novos), com upsert via `saveAndFlush`/`ON CONFLICT` conforme padrão já usado em `RecursoRepositorioAdapter`/`ScoreReplicaRepositorioAdapter` para upsert idempotente
- `infrastructure/web/AlocacaoController.java:33-51` (mod.) — novo `@PostMapping("/v1/recursos/{id}/alocacoes/recusa")`, injeta `RecusarSugestao`
- `infrastructure/web/RecusarSugestaoRequest.java` (novo) — `record(@NotNull @Positive Long pacienteId, @NotBlank String motivo) {}`, molde `ConfirmarAlocacaoRequest.java:16`
- `infrastructure/web/RecusarSugestaoResponse.java` (novo) — `record(UUID recursoId, long pacienteId, String motivo, Instant recusadoEm)`, molde `AlocacaoResponse.java:13-19`
- `src/main/resources/db/migration/V5__create_alocacao.sql` (referência de estilo) — próxima: `V6__create_sugestao_recusada.sql`
- `MatchingAlocacaoServiceApplication.java:110-124` (mod.) — novo `@Bean recusarSugestao(SugestaoRecusadaRepositorio, EventoOutboxRepositorio, RecursoConsultaRepositorio, Clock)`, molde `confirmarAlocacao(...)`
- `test/application/command/ConfirmarAlocacaoTest.java` (molde) → `RecusarSugestaoTest.java` (novo) — mocks das portas, `ArgumentCaptor<EventoOutbox>`, `Clock.fixed(...)`
- `test/AlocacaoControllerIntegrationTest.java` (mod.) — cenário de recusa via Testcontainers (feliz + 400 + 404 + duplicata)

## Tasks & Acceptance

**Execution:**
- [x] `V6__create_sugestao_recusada.sql` -- migration nova -- base para a porta de persistência
- [x] `SugestaoRecusadaRepositorio` (porta) + `SugestaoRecusadaJpaEntity`/`SugestaoRecusadaJpaRepository`/`SugestaoRecusadaRepositorioAdapter` -- persistência upsert do par recusado
- [x] `RecusarSugestao.java` -- comando (molde `ConfirmarAlocacao`) -- valida Recurso existe, grava recusa, publica `SugestaoRecusada`
- [x] `RecusarSugestaoRequest.java`, `RecusarSugestaoResponse.java` -- DTOs -- molde `ConfirmarAlocacaoRequest`/`AlocacaoResponse`
- [x] `AlocacaoController.java` -- novo endpoint `POST /v1/recursos/{id}/alocacoes/recusa` -- `201`
- [x] `MatchingAlocacaoServiceApplication.java` -- `@Bean recusarSugestao(...)` -- wiring
- [x] `RecusarSugestaoTest.java` -- unitário, molde `ConfirmarAlocacaoTest` -- captura `EventoOutbox`, cobre feliz/404/duplicata/correlationId inválido
- [x] `AlocacaoControllerIntegrationTest.java` -- cenário E2E de recusa contra Postgres real (feliz, duplicata, 404, motivo em branco, pacienteId ausente/não positivo -- Matrix Test Audit completa)

**Acceptance Criteria:**
- Given motivo ausente ou em branco, when `POST /v1/recursos/{id}/alocacoes/recusa`, then `400`
- Given Recurso inexistente, when `POST /v1/recursos/{id}/alocacoes/recusa`, then `404`
- Given recusa bem-sucedida, when se consulta `sugestao_recusada` no banco, then a linha `(recursoId, pacienteId)` existe com o `motivo` informado
- Given `mvn -pl matching-alocacao-service -am verify`, then testes verdes, JaCoCo domínio/aplicação ≥90%

## Design Notes

Upsert idempotente na PK composta `(recurso_id, paciente_id)`: usar `INSERT ... ON CONFLICT (recurso_id, paciente_id) DO UPDATE SET motivo = EXCLUDED.motivo, recusado_em = EXCLUDED.recusado_em` via `@Modifying @Query` nativa no `SugestaoRecusadaJpaRepository` (mesmo estilo de upsert já usado em `RecursoRepositorioAdapter`/`ScoreReplicaRepositorioAdapter` para os outros upserts idempotentes do serviço) — evita a corrida de `findById` + `save` em duas etapas.

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%, incluindo os novos cenários de `RecusarSugestao`

## Suggested Review Order

**Comando (entry point)**

- Orquestra a recusa: valida Recurso, grava o par recusado, publica o evento -- tudo numa transação, molde exato de `ConfirmarAlocacao`.
  [`RecusarSugestao.java:127-144`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/RecusarSugestao.java#L127-L144)

- Contrato do payload do outbox -- `recursoId` no nível raiz, exigido por `RelaySnsPublisherJob`.
  [`RecusarSugestao.java:157-165`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/RecusarSugestao.java#L157-L165)

**Persistência (upsert idempotente)**

- Upsert nativo `ON CONFLICT ... DO UPDATE` pela PK composta -- evita a corrida de `findById`+`save` em duas etapas.
  [`SugestaoRecusadaJpaRepository.java:299-310`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/SugestaoRecusadaJpaRepository.java#L299-L310)

- Migration nova, PK composta sem coluna sintética -- identidade do par recusado é a própria PK.
  [`V6__create_sugestao_recusada.sql`](../../matching-alocacao-service/src/main/resources/db/migration/V6__create_sugestao_recusada.sql)

- `@EmbeddedId` só para satisfazer o genérico de `JpaRepository` -- escrita real passa pelo upsert nativo, não por `save()`.
  [`SugestaoRecusadaJpaEntity.java:228-273`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/SugestaoRecusadaJpaEntity.java#L228-L273)

**Endpoint HTTP**

- Novo `POST /v1/recursos/{id}/alocacoes/recusa`, `201`, mesmo tratamento de `X-Correlation-Id`/404 do endpoint de confirmação.
  [`AlocacaoController.java:401-409`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/AlocacaoController.java#L401-L409)

- Bean Validation do corpo -- `@NotNull @Positive` em `pacienteId`, `@NotBlank` em `motivo`.
  [`RecusarSugestaoRequest.java:14`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecusarSugestaoRequest.java#L14)

**Wiring**

- Novo `@Bean recusarSugestao(...)`, mesmo molde de `confirmarAlocacao(...)`.
  [`MatchingAlocacaoServiceApplication.java:133-140`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/MatchingAlocacaoServiceApplication.java#L133-L140)

**Testes**

- Cobre a I/O Matrix inteira via mocks + `ArgumentCaptor` do `EventoOutbox` publicado.
  [`RecusarSugestaoTest.java:692-757`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/command/RecusarSugestaoTest.java#L692-L757)

- Prova ponta a ponta contra Postgres real (Testcontainers) -- formato HTTP, persistência e os 2 cenários de Bean Validation que `RecusarSugestaoTest` não exercita (`pacienteId` ausente/não positivo).
  [`AlocacaoControllerIntegrationTest.java:225-651`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/AlocacaoControllerIntegrationTest.java#L225-L651)

