---
title: 'Exclusão de Pacientes com Alocação Ativa da Fila Priorizada'
type: 'feature'
created: '2026-09-12'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'd4ad3e958bd5841cdd76c1d606f72d228a276ee8'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `GET /v1/fila` lista todo `ScoreReplica`, inclusive Pacientes já com `Alocacao` ativa (3-3b1) — o porto `AlocacaoConsultaRepositorio` (3-3b2a) existe mas sem consumidor.

**Approach:** Injetar `AlocacaoConsultaRepositorio` em `ConsultarFilaPriorizada` e filtrar `ScoreReplica` (excluindo `pacienteId` de `pacientesComAlocacaoAtiva()`) antes do `.map`/`.sorted`, sem tocar a Prioridade Efetiva. `ConsultarSugestaoRecurso` reutiliza `consultar()`, então a exclusão passa a valer também em `GET /v1/recursos/{id}/sugestao` — consequência desejada, não escopo novo.

## Boundaries & Constraints

**Always:**
- `.filter` no stream (`ConsultarFilaPriorizada.java:77`), entre `.stream()` e `.map` (linha 78) — nunca após `.sorted`.
- `pacientesComAlocacaoAtiva()` chamado 1x por `consultar()` (variável local `Set<Long>`), nunca dentro do predicado.
- Sem mudança em `PrioridadeEfetiva`, Aging, `Comparator`, ou `ItemFila`.
- Novo parâmetro `AlocacaoConsultaRepositorio` no construtor (injeção por construtor puro, padrão dos 4 existentes) e no `@Bean consultarFilaPriorizada(...)` (`MatchingAlocacaoServiceApplication.java:92-96`).
- `Set` vazio preserva 100% do comportamento atual.

**Ask First:** qualquer necessidade de infraestrutura/schema nova não prevista — HALT antes de prosseguir.

**Never:** alterar `AlocacaoConsultaRepositorio`/adapter (3-3b2a, congelada), `ConfirmarAlocacao`, domínio `Alocacao`; cachear `pacientesComAlocacaoAtiva()`; mudar contrato JSON de `FilaController`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Sem Alocação ativa | `X` não está em `pacientesComAlocacaoAtiva()` | `X` aparece normalmente, ordenado por Prioridade Efetiva | N/A |
| Com Alocação ativa | `X` está em `pacientesComAlocacaoAtiva()` | `X` não aparece em `consultar()` | N/A |
| Nenhuma Alocação no sistema | `Set` vazio | Fila completa, idêntica ao comportamento pré-existente | N/A |
| Todos alocados | `Set` cobre todos os `pacienteId` da réplica | Lista vazia | N/A |

</frozen-after-approval>

## Code Map

Base: `matching-alocacao-service/src/{main,test}/java/com/filajusta/matching/`

- `main/application/query/ConsultarFilaPriorizada.java:49-84` (mod.) -- novo campo `AlocacaoConsultaRepositorio`; `.filter` entre `.stream()` (77) e `.map` (78)
- `main/application/query/AlocacaoConsultaRepositorio.java` (só consumido) -- `Set<Long> pacientesComAlocacaoAtiva()`
- `main/MatchingAlocacaoServiceApplication.java:92-96` (mod.) -- `@Bean consultarFilaPriorizada(...)` ganha parâmetro
- `test/application/query/ConsultarFilaPriorizadaTest.java:44-50` (mod.) -- mock da porta (`Set.of()` default) + testes da I/O Matrix
- `test/FilaBootstrapIntegrationTest.java` (mod.) -- cenário E2E com `Alocacao` ATIVA; `@BeforeEach` (87-91, só trunca `score_replica`) precisa truncar também `alocacao`
- `main/domain/ScoreReplica.java:99-101` (só consumido) -- `getPacienteId(): long`

## Tasks & Acceptance

**Execution:**
- [x] `ConsultarFilaPriorizada.java` -- novo campo + `.filter` no stream -- exclui alocados sem tocar Prioridade Efetiva
- [x] `MatchingAlocacaoServiceApplication.java` -- wiring do novo parâmetro no `@Bean`
- [x] `ConsultarFilaPriorizadaTest.java` -- mock da porta + testes da I/O Matrix -- filtro isolado dos casos já existentes (ordenação, bootstrap, desempates)
- [x] `FilaBootstrapIntegrationTest.java` -- cenário E2E com Alocação real via Postgres -- prova ponta a ponta, não só mock

**Acceptance Criteria:**
- Given `Alocacao` ATIVA para `pacienteId` presente na réplica, when `GET /v1/fila`, then esse `pacienteId` não aparece na resposta
- Given nenhuma Alocação ativa, when `GET /v1/fila`, then comportamento idêntico ao pré-existente
- Given filtro implementado, when `mvn -pl matching-alocacao-service -am verify`, then ordenação/desempates continuam passando inalterados

## Spec Change Log

_Vazio — sem loopbacks de review ainda._

## Design Notes

`Set<Long>.contains(long)` faz autoboxing implícito (`getPacienteId()` retorna `long` primitivo). Ex.:

```java
Set<Long> alocados = alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva();
.filter(replica -> !alocados.contains(replica.getPacienteId()))
```

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%, incluindo o novo teste de integração E2E

## Suggested Review Order

**Filtro na fila (entry point)**

- Lê o `Set` uma única vez e filtra antes do `.map`/`.sorted` -- núcleo da mudança de comportamento.
  [`ConsultarFilaPriorizada.java:93-95`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarFilaPriorizada.java#L93-L95)

**Wiring do novo porto**

- `@Bean` ganha o parâmetro `AlocacaoConsultaRepositorio` e repassa ao construtor -- fecha a injeção ponta a ponta.
  [`MatchingAlocacaoServiceApplication.java:98-101`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/MatchingAlocacaoServiceApplication.java#L98-L101)

**Consequência herdada (patch do code review)**

- Javadoc deixa explícito que `ConsultarSugestaoRecurso` herda o filtro por reutilizar `consultar()`, sem lógica duplicada.
  [`ConsultarSugestaoRecurso.java:14-18`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarSugestaoRecurso.java#L14-L18)

**Testes**

- Cobre os 4 cenários da I/O Matrix mais o boundary "chamado 1x por consultar()".
  [`ConsultarFilaPriorizadaTest.java:200-343`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/query/ConsultarFilaPriorizadaTest.java#L200-L343)

- Prova ponta a ponta contra Postgres real (Testcontainers), não só mock.
  [`FilaBootstrapIntegrationTest.java:261-318`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/FilaBootstrapIntegrationTest.java#L261-L318)
