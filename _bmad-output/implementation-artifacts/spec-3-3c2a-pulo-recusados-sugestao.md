---
title: 'Pulo de Pacientes Recusados na Sugestão de Matching'
type: 'feature'
created: '2026-09-12'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'bed5d84d8e24969b7fcf449deae43eebfde55c86'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `ConsultarSugestaoRecurso` (3.2b3) ignora a tabela `sugestao_recusada` (3-3c1) — um Paciente recusado continua sendo resugerido indefinidamente para o mesmo Recurso.

**Approach:** Ao decidir a sugestão, avançar dentro de `filaGlobal` a partir do índice `n` (já calculado) pulando pacientes recusados para aquele `recursoId`, sem alterar `n`.

## Boundaries & Constraints

**Always:**
- Novo port `application/query/SugestaoRecusadaConsultaRepositorio.recusadosPara(UUID recursoId): Set<Long>` — leitura pura, sem efeito colateral, mesma tabela `sugestao_recusada` (3-3c1). Convenção já usada: `AlocacaoConsultaRepositorio` (leitura) vs `AlocacaoRepositorio` (escrita).
- `ConsultarSugestaoRecurso.consultar()`: obter `recusados = sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId)`; percorrer `filaGlobal` a partir do índice `n` (inclusive) e escolher o primeiro `ItemFila` cujo `pacienteId()` não esteja em `recusados`; se nenhum for encontrado, `pacienteIdSugerido = null` (mesmo tratamento de FILA_ESGOTADA já existente). `n` não muda.
- Leitura em massa: novo método nativo em `SugestaoRecusadaJpaRepository` (`SELECT paciente_id FROM matching_alocacao.sugestao_recusada WHERE recurso_id = :recursoId`), consumido por um novo adapter `SugestaoRecusadaConsultaRepositorioAdapter` (converte `List<Long>` em `Set<Long>`).

**Ask First:** mudar o contrato JSON de `GET /v1/recursos/{id}/sugestao` — HALT antes (fora do escopo).

**Never:** alterar `RecusarSugestao`/`ConfirmarAlocacao`/`RecursoSugestaoController`/`SugestaoRecursoResponse`; tornar `consultar()` `@Transactional` ou publicar qualquer evento (rastreamento AD-10/`SugestaoGerada` é a Story 3-3c2b, deferida em `deferred-work.md`); duplicar a contagem de tiers.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Topo já recusado | `filaGlobal.get(n)` está em `recusados`, próximo não está | Sugestão pula para o próximo elegível | N/A |
| Nenhum recusado | `recusados` vazio | Comportamento idêntico ao atual (`filaGlobal.get(n)`) | N/A |
| Todos recusados até o fim | Todo `filaGlobal[n..]` está em `recusados` | `pacienteIdSugerido=null` (mesmo tratamento de FILA_ESGOTADA) | N/A |
| Recurso indisponível | `recurso.isDisponivel()==false` | `null`, sem consultar `recusados` (early return já existente, inalterado) | N/A |
| Recusado é de outro Recurso | Paciente recusado só para Recurso B, consulta é do Recurso A | Não é pulado na sugestão de A (`recusadosPara` é escopado por `recursoId`) | N/A |

</frozen-after-approval>

## Code Map

- `application/query/ConsultarSugestaoRecurso.java:42-68` -- construtor ganha `SugestaoRecusadaConsultaRepositorio`; loop de pulo substitui `filaGlobal.get(n)` (linha 65)
- `application/query/SugestaoRecusadaConsultaRepositorio.java` (novo, porta) -- `Set<Long> recusadosPara(UUID recursoId)`
- `application/query/ConsultarFilaPriorizada.java:104-111` (ref.) -- `ItemFila.pacienteId()` é `long`
- `infrastructure/persistence/SugestaoRecusadaRepositorioAdapter.java:21-35` (ref., não tocar) -- adapter de escrita já existente (3-3c1), mesma tabela
- `infrastructure/persistence/SugestaoRecusadaJpaRepository.java` (mod.) -- novo método nativo `List<Long> buscarPacientesRecusados(UUID recursoId)`, mesmo estilo `@Query(nativeQuery = true)` do `upsert` existente
- `infrastructure/persistence/SugestaoRecusadaConsultaRepositorioAdapter.java` (novo) -- implementa a porta, reusa `SugestaoRecusadaJpaRepository`
- `MatchingAlocacaoServiceApplication.java:118-122` (mod.) -- bean `consultarSugestaoRecurso(...)` ganha a nova dependência
- `test/application/query/ConsultarSugestaoRecursoTest.java` (mod.) -- novo mock; casos: pulo, nenhum recusado, todos recusados, recusado de outro Recurso
- `test/RecursoSugestaoControllerIntegrationTest.java` (mod.) -- E2E Postgres real: popular `sugestao_recusada` via `jdbcTemplate` (molde `seedScoreReplica`), confirmar pulo

## Tasks & Acceptance

**Execution:**
- [x] `SugestaoRecusadaConsultaRepositorio` (porta) + `SugestaoRecusadaConsultaRepositorioAdapter` + método nativo em `SugestaoRecusadaJpaRepository`
- [x] `ConsultarSugestaoRecurso.java` -- pulo de recusados no loop de seleção do `pacienteIdSugerido`
- [x] `MatchingAlocacaoServiceApplication.java` -- wiring da nova dependência
- [x] `ConsultarSugestaoRecursoTest.java` -- unitário, cobre a I/O Matrix completa
- [x] `RecursoSugestaoControllerIntegrationTest.java` -- E2E: Paciente recusado é pulado na sugestão

**Acceptance Criteria:**
- Given Paciente recusado para o Recurso consultado, when `GET /v1/recursos/{id}/sugestao`, then a sugestão pula esse Paciente sem afetar `n` nem outros Recursos
- Given `mvn -pl matching-alocacao-service -am verify`, then testes verdes, JaCoCo domínio/aplicação ≥90%

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%, incluindo os novos cenários de pulo de recusados

## Suggested Review Order

**Algoritmo de pulo (entry point)**

- Guarda `n < filaGlobal.size()` evita consultar `recusados` quando a fila já esgotou só pela contagem de tiers -- achado do code review multi-agente.
  [`ConsultarSugestaoRecurso.java:77-86`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarSugestaoRecurso.java#L77-L86)

- Construtor ganha a nova porta de leitura, mesmo molde das demais dependências do caso de uso.
  [`ConsultarSugestaoRecurso.java:55-62`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarSugestaoRecurso.java#L55-L62)

**Porta e persistência (leitura em massa)**

- Porta nova, irmã de leitura de `SugestaoRecusadaRepositorio` -- mesmo split CQRS já usado em `AlocacaoConsultaRepositorio`.
  [`SugestaoRecusadaConsultaRepositorio.java:16-23`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/SugestaoRecusadaConsultaRepositorio.java#L16-L23)

- Query nativa `WHERE recurso_id = :recursoId` -- Spring Data devolve `Set` vazio sem linhas, nunca `null`.
  [`SugestaoRecusadaJpaRepository.java:14-20`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/SugestaoRecusadaJpaRepository.java#L14-L20)

- Adapter fino, reusa o mesmo `SugestaoRecusadaJpaRepository` do adapter de escrita (3-3c1).
  [`SugestaoRecusadaConsultaRepositorioAdapter.java:20-32`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/SugestaoRecusadaConsultaRepositorioAdapter.java#L20-L32)

**Wiring**

- Bean `consultarSugestaoRecurso(...)` ganha a nova dependência.
  [`MatchingAlocacaoServiceApplication.java:120-124`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/MatchingAlocacaoServiceApplication.java#L120-L124)

**Testes**

- Cobre a I/O Matrix inteira via mocks: pulo, todos recusados, isolamento entre Recursos (prova negativa com `outroRecursoId`).
  [`ConsultarSugestaoRecursoTest.java:178-226`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/query/ConsultarSugestaoRecursoTest.java#L178-L226)

- Prova a query nativa contra Postgres real: Set vazio, múltiplos recusados, isolamento por `recurso_id` via WHERE.
  [`SugestaoRecusadaConsultaRepositorioAdapterIntegrationTest.java:61-105`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/SugestaoRecusadaConsultaRepositorioAdapterIntegrationTest.java#L61-L105)

- Prova ponta a ponta via HTTP: paciente recusado é pulado na resposta real do endpoint.
  [`RecursoSugestaoControllerIntegrationTest.java:102-146`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/RecursoSugestaoControllerIntegrationTest.java#L102-L146)
