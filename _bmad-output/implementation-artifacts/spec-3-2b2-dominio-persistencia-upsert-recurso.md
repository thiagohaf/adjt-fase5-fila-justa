---
title: 'Story 3.2b2: Domínio, Persistência e Upsert Interno de Recurso (matching-alocacao-service)'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'c7edba3d9d15548f09b822bb2f56ed5d3d68109f'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `Recurso` não existe em `matching-alocacao-service`. A Story 3-2b3 (sugestão com tiers) e o Epic 5 (`seed-adapter`) dependem desse agregado existir com upsert idempotente por `codigoRecurso`.

**Approach:** Criar `Recurso` (domínio + persistência Flyway/JPA + comando `UpsertRecurso`) e expor `POST /internal/recursos` para upsert, no mesmo padrão já usado para `ScoreReplica`/`AtualizarScoreReplica` (3.2b1) e `GET /internal/scores` (3.2a).

## Boundaries & Constraints

**Always:**
- Upsert por `codigoRecurso`: cria `Recurso` novo (recursoId UUID v4 gerado) se inédito; atualiza `especificidadeRank`/`disponivel` (mesmo recursoId) se já existir. Direto e idempotente, sem comparação temporal.
- `especificidadeRank` inteiro positivo (>= 1); valores concretos (1-4) vêm do seed do Epic 5, esta story só valida positividade.
- `codigoRecurso` obrigatório, não-vazio, único no banco (chave do `ON CONFLICT`).
- `disponivel` obrigatório no request, sem default implícito.
- Endpoint interno sem JWT (padrão de `GET /internal/scores`/`GET /v1/fila`, nenhum serviço tem Spring Security ainda); javadoc alerta para nunca expor via gateway/CDK.

**Ask First:** nenhuma — formato já confirmado (`epic-3-context.md`, `deferred-work.md`).

**Never:** `GET /v1/recursos/{id}/sugestao` ou tiers (3-2b3); confirmação/recusa/alocação/liberação (3.3/3.4); expor `Recurso` em `GET /v1/fila`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Cria novo | `codigoRecurso` inédito | Persistido com novo `recursoId`; `201` | N/A |
| Atualiza existente | `codigoRecurso` já cadastrado, valores diferentes | Atualizado, mesmo `recursoId`; `200` | N/A |
| `especificidadeRank` inválido | Ausente, zero ou negativo | Rejeitado | `400` |
| `codigoRecurso` ausente/vazio | Body sem o campo ou string vazia | Rejeitado | `400` |
| `disponivel` ausente | Body sem o campo | Rejeitado | `400` |

</frozen-after-approval>

## Code Map

- `matching-alocacao-service/.../db/migration/V3__create_recurso.sql` (novo) -- tabela `matching_alocacao.recurso` (`recurso_id UUID PK`, `codigo_recurso TEXT NOT NULL UNIQUE`, `especificidade_rank INTEGER NOT NULL`, `disponivel BOOLEAN NOT NULL`), padrão `V1__create_matching_schema.sql`
- `.../domain/Recurso.java` (novo) -- classe final, valida invariantes no construtor, padrão `ScoreReplica.java`
- `.../application/command/RecursoRepositorio.java` (novo) -- porta `void upsert(Recurso)`, padrão `ScoreReplicaRepositorio.java`
- `.../application/command/UpsertRecurso.java` (novo) -- caso de uso, injeta a porta, padrão `AtualizarScoreReplica.java`
- `.../infrastructure/persistence/RecursoJpaEntity.java` (novo) -- `@Entity @Table(schema="matching_alocacao")`, só getters, padrão `ScoreReplicaJpaEntity.java`
- `.../infrastructure/persistence/RecursoJpaRepository.java` (novo) -- `@Modifying @Query(nativeQuery=true)`: `INSERT ... ON CONFLICT (codigo_recurso) DO UPDATE SET especificidade_rank=excluded.*, disponivel=excluded.*`; sem `WHERE` temporal (difere de `ScoreReplicaJpaRepository.java:42`)
- `.../infrastructure/persistence/RecursoRepositorioAdapter.java` (novo) -- `@Component @Transactional`, padrão `ScoreReplicaRepositorioAdapter.java`
- `.../infrastructure/web/RecursosInternalController.java` (novo) -- `POST /internal/recursos`, sem segurança, javadoc de não-exposição, padrão `ScoresInternalController.java:15-19`
- `.../infrastructure/web/UpsertRecursoRequest.java` (novo) -- record com Bean Validation (`@NotBlank`, `@Positive`, `@NotNull`)
- `.../infrastructure/web/RecursoResponse.java` (novo) -- record, factory `de(Recurso)`, padrão `ScoreAtualResponse.java`

## Tasks & Acceptance

**Execution:**
- [x] `V3__create_recurso.sql` -- tabela `recurso` com `UNIQUE(codigo_recurso)`
- [x] `Recurso.java` -- domínio com validação de invariantes
- [x] `RecursoRepositorio.java`/`RecursoJpaEntity.java`/`RecursoJpaRepository.java`/`RecursoRepositorioAdapter.java` -- persistência com upsert nativo
- [x] `UpsertRecurso.java` -- comando de aplicação
- [x] `UpsertRecursoRequest.java`/`RecursoResponse.java`/`RecursosInternalController.java` -- `POST /internal/recursos`
- [x] Testes unitários cobrindo a I/O Matrix
- [x] Teste de integração de persistência (Testcontainers, padrão `ScoreReplicaRepositorioAdapterIntegrationTest.java`) cobrindo insert e update

**Acceptance Criteria:**
- Given nenhum `Recurso` com um `codigoRecurso`, when `POST /internal/recursos` é chamado com ele, then é criado com `recursoId` gerado, resposta `201`
- Given um `Recurso` já cadastrado, when `POST /internal/recursos` repete o `codigoRecurso` com valores diferentes, then é atualizado (mesmo `recursoId`), resposta `200`
- Given `especificidadeRank`/`codigoRecurso`/`disponivel` inválidos ou ausentes, when `POST /internal/recursos` é chamado, then resposta `400`

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%

## Suggested Review Order

**Endpoint interno (entrada)**

- `POST /internal/recursos`, sem segurança, javadoc alertando para nunca expor via gateway -- ponto de entrada do fluxo.
  [`RecursosInternalController.java:36`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecursosInternalController.java#L36)

**Decisão criado vs. atualizado (comando de aplicação)**

- `upsertar` delega o upsert nativo e decide `criado`/`atualizado` comparando o `recursoId` retornado com o candidato gerado -- núcleo do AC de status `201`/`200`.
  [`UpsertRecurso.java:37`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/UpsertRecurso.java#L37)

- `Resultado` carrega o `Recurso` persistido e o flag `criado` até o controller.
  [`UpsertRecurso.java:54`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/UpsertRecurso.java#L54)

**Domínio (invariantes + patch de normalização)**

- Construtor valida `especificidadeRank >= 1` e normaliza (`trim`) `codigoRecurso` -- patch do code review, preserva a idempotência do upsert para códigos com espaços ao redor.
  [`Recurso.java:35`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/Recurso.java#L35)

**Persistência (upsert nativo)**

- `INSERT ... ON CONFLICT (codigo_recurso) DO UPDATE` -- upsert direto e idempotente, sem cláusula `WHERE` temporal (diferente do upsert de `ScoreReplica`).
  [`RecursoJpaRepository.java:22`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoJpaRepository.java#L22)

- `upsert` lê o Recurso de volta na mesma transação (a query nativa nunca sobrescreve `recurso_id`) para devolver o estado realmente persistido.
  [`RecursoRepositorioAdapter.java:29`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoRepositorioAdapter.java#L29)

**Tratamento de erros (patches do code review)**

- Patch 1: `HttpMessageNotReadableException` (corpo JSON malformado/ausente) agora retorna `400` RFC 7807 em vez de cair no handler genérico `500` de `FilaExceptionHandler`.
  [`RecursosExceptionHandler.java:61`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecursosExceptionHandler.java#L61)

- Validação de Bean Validation (`especificidadeRank`/`codigoRecurso`/`disponivel` ausentes ou inválidos) também retorna `400` RFC 7807.
  [`RecursosExceptionHandler.java:41`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecursosExceptionHandler.java#L41)

**Peripherals**

- Nova migration cria a tabela com `UNIQUE(codigo_recurso)` -- base do `ON CONFLICT`.
  [`V3__create_recurso.sql:12`](../../matching-alocacao-service/src/main/resources/db/migration/V3__create_recurso.sql#L12)

- Testes cobrindo a I/O Matrix completa (criação, atualização, três cenários de `400`) mais os dois patches (corpo malformado, `codigoRecurso` com espaços) via HTTP real, e insert/update/idempotência na camada de persistência (Testcontainers).
  [`UpsertRecursoIntegrationTest.java`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/UpsertRecursoIntegrationTest.java), [`RecursoRepositorioAdapterIntegrationTest.java`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/RecursoRepositorioAdapterIntegrationTest.java)
