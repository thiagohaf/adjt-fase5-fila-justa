---
title: 'Porto de Apoio ao AD-10: Última Sugestão Registrada por Recurso'
type: 'feature'
created: '2026-09-12'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'd07cd41862e137fc5704fb56230547abc914d80b'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O rastreamento AD-10 (evento `SugestaoGerada` só quando a sugestão de um Recurso muda) não tem onde guardar "qual foi a última sugestão registrada para este Recurso" — não existe hoje nenhuma tabela nem porta para isso em `matching-alocacao-service`.

**Approach:** Nova tabela `ultima_sugestao_registrada` (schema `matching_alocacao`, PK `recurso_id`) + novo port `application/command/UltimaSugestaoRegistradaRepositorio` (leitura do último `pacienteId` registrado + upsert idempotente) e seu adapter JPA — infraestrutura pura, sem nenhum consumidor real ainda (mesmo padrão da Story 3-3a para o outbox: pré-requisito puro, consumido pela próxima sub-story, 3-3c2b2, que aplica o rastreamento em `ConsultarSugestaoRecurso`).

## Boundaries & Constraints

**Always:**
- `application/command/UltimaSugestaoRegistradaRepositorio` (porta): `Optional<Long> pacienteIdRegistrado(UUID recursoId)` (sem registro para o Recurso -> `Optional.empty()`, nunca exceção) + `void registrar(UUID recursoId, long pacienteId, Instant registradoEm)` (upsert idempotente pela PK `recurso_id` -- registrar de novo o mesmo Recurso apenas atualiza `paciente_id`/`registrado_em`, mesmo padrão de `SugestaoRecusadaRepositorio#registrar`).
- Migration `V7__create_ultima_sugestao_registrada.sql`: `recurso_id UUID PRIMARY KEY, paciente_id BIGINT NOT NULL, registrado_em TIMESTAMPTZ NOT NULL`.
- Upsert nativo `INSERT ... ON CONFLICT (recurso_id) DO UPDATE SET paciente_id = excluded.paciente_id, registrado_em = excluded.registrado_em` -- mesmo estilo de `SugestaoRecusadaJpaRepository#upsert`.
- Leitura via `findById(UUID)` herdado de `JpaRepository` (não precisa de query nativa própria) mapeado para `Optional<Long>` no adapter.

**Ask First:** nenhuma decisão nova requer aprovação humana durante a execução desta story (escopo puramente aditivo/infraestrutural).

**Never:** tocar `ConsultarSugestaoRecurso`/`MatchingAlocacaoServiceApplication` (wiring e uso real do port são a Story 3-3c2b2, deferida em `deferred-work.md`); publicar qualquer evento de outbox; adicionar `@Transactional` em qualquer caso de uso existente.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Nenhum registro para o Recurso | `recursoId` nunca registrado | `pacienteIdRegistrado` retorna `Optional.empty()` | N/A |
| Primeiro registro | `registrar(recursoId, 42, agora)` | Linha inserida; `pacienteIdRegistrado(recursoId)` passa a retornar `Optional.of(42L)` | N/A |
| Registro repetido do mesmo Recurso | Já registrado `42`; `registrar(recursoId, 99, agora2)` | Linha atualizada (`paciente_id=99`, `registrado_em=agora2`), sem duplicar nem falhar | N/A |
| Isolamento entre Recursos | Registro só existe para o Recurso A | `pacienteIdRegistrado` do Recurso B retorna `Optional.empty()` | N/A |

</frozen-after-approval>

## Code Map

- `db/migration/V7__create_ultima_sugestao_registrada.sql` (novo) -- molde de `V6__create_sugestao_recusada.sql`, mas PK simples (`recurso_id`), sem PK composta
- `application/command/UltimaSugestaoRegistradaRepositorio.java` (novo, porta) -- molde de `application/command/SugestaoRecusadaRepositorio.java`
- `infrastructure/persistence/UltimaSugestaoRegistradaJpaEntity.java` (novo) -- `@Id recursoId` (UUID, sem `@EmbeddedId` -- PK simples, diferente de `SugestaoRecusadaJpaEntity`), `pacienteId` (long), `registradoEm` (Instant)
- `infrastructure/persistence/UltimaSugestaoRegistradaJpaRepository.java` (novo, package-private) -- `extends JpaRepository<UltimaSugestaoRegistradaJpaEntity, UUID>`; `findById` herdado cobre a leitura; `@Modifying @Query` nativo para o `upsert`, molde de `SugestaoRecusadaJpaRepository.java:29-40`
- `infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapter.java` (novo) -- implementa a porta; `pacienteIdRegistrado` via `jpaRepository.findById(recursoId).map(UltimaSugestaoRegistradaJpaEntity::getPacienteId)`; `registrar` delega ao upsert nativo, molde de `SugestaoRecusadaRepositorioAdapter.java`
- `test/infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java` (novo) -- Postgres real, molde de `SugestaoRecusadaConsultaRepositorioAdapterIntegrationTest.java`: cobre a I/O Matrix completa

## Tasks & Acceptance

**Execution:**
- [x] `V7__create_ultima_sugestao_registrada.sql` -- nova tabela
- [x] `UltimaSugestaoRegistradaRepositorio.java` -- porta
- [x] `UltimaSugestaoRegistradaJpaEntity.java` + `UltimaSugestaoRegistradaJpaRepository.java` -- mapeamento JPA + upsert nativo
- [x] `UltimaSugestaoRegistradaRepositorioAdapter.java` -- implementação da porta
- [x] `UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java` -- cobre a I/O Matrix completa contra Postgres real

**Acceptance Criteria:**
- Given nenhum registro para um `recursoId`, when `pacienteIdRegistrado(recursoId)` é chamado, then retorna `Optional.empty()` sem lançar exceção
- Given um `registrar(recursoId, pacienteId, instant)` bem-sucedido, when `pacienteIdRegistrado(recursoId)` é chamado em seguida, then retorna `Optional.of(pacienteId)`
- Given `mvn -pl matching-alocacao-service -am verify`, then testes verdes, JaCoCo domínio/aplicação ≥90%

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%, incluindo o novo teste de integração do adapter

## Suggested Review Order

**Porta (entry point)**

- Porta de apoio ao AD-10, combinando leitura+escrita num único port -- exceção documentada AD-2 (rastreamento não é estado de domínio de Matching).
  [`UltimaSugestaoRegistradaRepositorio.java:28-32`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/UltimaSugestaoRegistradaRepositorio.java#L28-L32)

**Persistência (schema + JPA)**

- Tabela nova com PK simples `recurso_id` -- só existe UMA última sugestão por Recurso, ao contrário da PK composta de `sugestao_recusada`.
  [`V7__create_ultima_sugestao_registrada.sql:14`](../../matching-alocacao-service/src/main/resources/db/migration/V7__create_ultima_sugestao_registrada.sql#L14)

- Upsert nativo `ON CONFLICT (recurso_id) DO UPDATE` -- last-write-wins simples, evita a corrida de findById+save em duas etapas.
  [`UltimaSugestaoRegistradaJpaRepository.java:11-30`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/UltimaSugestaoRegistradaJpaRepository.java#L11-L30)

- Entidade com PK simples (sem `@EmbeddedId`) -- `getRegistradoEm()` adicionado no code review (achado do blind-hunter: campo mapeado sem getter).
  [`UltimaSugestaoRegistradaJpaEntity.java:27-49`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/UltimaSugestaoRegistradaJpaEntity.java#L27-L49)

- Adapter fino delega ao upsert nativo; `@Transactional(readOnly = true)` na leitura adicionado no code review (consistência com `SugestaoRecusadaConsultaRepositorioAdapter`).
  [`UltimaSugestaoRegistradaRepositorioAdapter.java:20-38`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapter.java#L20-L38)

**Testes**

- Cobre a I/O Matrix completa contra Postgres real: vazio, primeiro registro, upsert idempotente, isolamento entre Recursos.
  [`UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java:56-92`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest.java#L56-L92)
