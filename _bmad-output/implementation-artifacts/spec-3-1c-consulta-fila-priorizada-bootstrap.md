---
title: 'Story 3.1c: GET /v1/fila + Prioridade Efetiva + Bootstrap a Frio (matching-alocacao-service)'
type: 'feature'
created: '2026-09-10'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '328af05c2f757940beb1daf15c21f7af3956ab8a'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `matching-alocacao-service` já mantém uma réplica local de Score (Story 3.1b), mas não expõe nenhuma forma de consultá-la priorizada, e a réplica fica vazia em boot a frio sem nenhum jeito de populá-la a partir do estado atual de `triagem-score-service` (Story 3.1a já expõe o endpoint interno, mas nada o chama ainda).

**Approach:** Expor `GET /v1/fila`, que recomputa Prioridade Efetiva (Aging com teto) sob demanda a partir da réplica e ordena decrescente. Réplica vazia dispara bootstrap síncrono a `GET /internal/scores` (Story 3.1a) antes de responder, reaproveitando o upsert idempotente já existente (Story 3.1b) — sem nova lógica de deduplicação.

## Boundaries & Constraints

**Always:** Prioridade Efetiva = `score + min(k × max(0, horas_espera), teto)`, `teto=20`, `k≈1,111/h` (`filajusta.aging.k`/`teto`), sempre recomputada sob demanda (`Clock` injetável, nunca cacheada); `horas_espera` = agora − `occurredAt`, nunca negativa. Réplica vazia dispara bootstrap síncrono via `GET /internal/scores` antes de responder -- nunca fila incompleta silenciosa; cada linha do bootstrap passa pelo `upsertSeMaisRecente` já existente (converge para o mais recente por paciente mesmo com múltiplas linhas). Falha no bootstrap retorna `503` (RFC 7807). Sem paginação.

**Ask First:** Nenhuma pendente -- rota/JWT e deploy ECS seguem o mesmo chore adiado de 2.1/3.0/3.1a/3.1b.

**Never:** Paginação. Desempate residual por Triagem mais antiga/sequência (é Story 3.2, na sugestão, não na listagem). Autenticação JWT/rota no gateway -- chore adiado. Sugestão de matching, confirmação/recusa, liberação de recurso (3.2–3.4).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Consulta normal | Réplica populada com Scores distintos | Lista ordenada por Prioridade Efetiva decrescente | N/A |
| Teto de Aging atingido | Réplica com `occurredAt` ≥18h atrás | Prioridade Efetiva não ultrapassa `score + 20` | N/A |
| Réplica vazia (boot a frio) | Nenhuma linha em `score_replica` | Bootstrap síncrono roda antes de responder; fila retornada já reflete os Scores atuais | N/A |
| Falha no bootstrap | `triagem-score-service` indisponível/erro | Réplica permanece vazia | `503` (RFC 7807), nunca fila incompleta |
| Defasagem de relógio | `occurredAt` no futuro (horas_espera negativa) | `horas_espera` tratada como `0` — Prioridade Efetiva nunca cai abaixo do Score | N/A |

</frozen-after-approval>

## Code Map

- `.../application/command/ScoreReplicaRepositorio.java` -- porta existente, só upsert (3.1b) -- adicionar leitura em novo port de query (CQRS lógico)
- `.../infrastructure/persistence/ScoreReplicaJpaRepository.java` -- já `extends JpaRepository` (`findAll`/`count`) -- reaproveitar, sem query nova
- `.../domain/ScoreReplica.java` -- getters existentes (`pacienteId`, `score`, `occurredAt`) -- reaproveitar
- `triagem-score-service/.../infrastructure/web/ScoresInternalController.java` -- contrato de `GET /internal/scores` (3.1a, branch separada, não presente neste checkout): array de `{pacienteId, score:{valor,algoritmoVersao,fatores}, occurredAt, eventId}`
- `.../infrastructure/relay/ScoreCalculadoSqsClientConfig.java` -- padrão de client externo configurável/testável -- mesmo princípio para o `RestClient` do bootstrap
- `triagem-score-service/.../infrastructure/web/TriagemExceptionHandler.java` -- padrão RFC 7807 a espelhar (`503` de falha de bootstrap)
- `epic-3-context.md` (Requirements) -- fórmula de Prioridade Efetiva já fixada

## Tasks & Acceptance

**Execution:**
- [x] `.../domain/PrioridadeEfetiva.java` -- cálculo puro (score + aging com teto); `agora` passado explicitamente em vez de `Clock` injetado no construtor
- [x] `.../application/query/FilaRepositorio.java` (novo port) + `FilaRepositorioAdapter` reaproveitando `findAll()`/`count()`
- [x] `.../application/query/ConsultarFilaPriorizada.java` -- réplica vazia dispara bootstrap; lê, calcula Prioridade Efetiva, ordena decrescente
- [x] `.../infrastructure/bootstrap/TriagemScoreClient.java` -- `RestClient` síncrono para `GET /internal/scores`, base-url configurável
- [x] `.../infrastructure/bootstrap/ScoreBootstrapService.java` -- mapeia a resposta, upserta cada linha via `AtualizarScoreReplica` (3.1b)
- [x] `.../infrastructure/web/FilaController.java` + `FilaExceptionHandler` RFC 7807 (`503` em falha de bootstrap) -- `GET /v1/fila`
- [x] `application.yml` -- `filajusta.aging.k`/`teto`, `filajusta.matching.bootstrap.base-url`
- [x] Testes unitários de `PrioridadeEfetiva` (teto, `horas_espera` negativa) e `ConsultarFilaPriorizada` (mock do bootstrap) -- cobre a I/O Matrix
- [x] Teste de integração: Testcontainers-Postgres + WireMock (stub de `GET /internal/scores`) -- boot a frio populando a réplica e falha do bootstrap → `503` (primeiro precedente de WireMock no projeto)

**Acceptance Criteria:**
- Given réplica populada com Scores distintos, when `GET /v1/fila`, then a lista vem ordenada por Prioridade Efetiva decrescente
- Given réplica vazia, when `GET /v1/fila`, then o bootstrap síncrono roda antes de responder e a fila retornada já reflete os Scores atuais de `triagem-score-service`

## Spec Change Log

- 2026-09-10: Code review multi-agente (3 reviewers) achou 1 bug real (confirmado por 2 reviewers) + 4 achados `patch` de robustez. Todos corrigidos:
  - **Patch 1 (bug real)** -- `ScoreBootstrapService#bootstrapar()` fazia um upsert por transação própria (padrão default do `@Transactional` do adapter); falha no meio de um lote deixava linhas anteriores commitadas, a réplica deixava de estar vazia e todo `GET /v1/fila` seguinte pulava o bootstrap para sempre (fila incompleta permanente, violando as Boundaries congeladas). Fix: `@Transactional` no método inteiro -- falha em qualquer linha reverte o lote inteiro. Coberto por `FilaBootstrapIntegrationTest#falhaNoMeioDoLoteReverteOBootstrapInteiroEReplicaContinuaVazia` (WireMock com 2 linhas, a 2ª com score fora de `0..100`).
  - **Patch 2** -- sem guarda de concorrência, requisições paralelas no boot a frio podiam disparar `bootstrapar()` em paralelo. Fix: `synchronized` em volta de "checar vazia + bootstrapar" em `ConsultarFilaPriorizada#consultar()` (single-flight por instância JVM). Coberto por `ConsultarFilaPriorizadaTest#requisicoesConcorrentesComReplicaVaziaDisparamBootstrapApenasUmaVez`.
  - **Patch 3** -- `catch (RuntimeException)` em `bootstrapar()` misturava falha de HTTP com falha de upsert (ambas viravam `503`). Fix: só `RestClientException` da chamada `triagemScoreClient.buscarScores()` vira `ScoreBootstrapIndisponivelException`/`503`; falha de upsert propaga crua para o fallback `500`.
  - **Patch 4** -- `FilaExceptionHandler` não logava as exceções antes de traduzir para RFC 7807. Fix: `log.warn` no handler de `503`, `log.error` no handler de `500`.
  - **Patch 5** -- `Comparator` de `ConsultarFilaPriorizada#consultar()` só comparava por Prioridade Efetiva, sem chave secundária -- empates tinham ordem não-determinística entre requisições. Fix: `.thenComparing(ItemFila::occurredAt)` (desempate estável, não é o desempate de negócio da Story 3.2). Coberto por `ConsultarFilaPriorizadaTest#empateDePrioridadeEfetivaDesempataDeFormaEstavelPorOccurredAtAscendente`.

## Design Notes

Bootstrap reaproveita `upsertSeMaisRecente` da 3.1b sem nova lógica de convergência, mesmo com múltiplas linhas por paciente (decisão já documentada na 3.1a). WireMock é o primeiro precedente no projeto para estubar uma chamada HTTP síncrona entre serviços (papel análogo ao LocalStack para AWS na 3.0).

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes + JaCoCo ≥90% domínio/aplicação
- Executado (verificação independente, antes dos 5 patches do code review): `mvn -pl matching-alocacao-service -am verify` -- BUILD SUCCESS, 47 testes (0 falhas), JaCoCo "All coverage checks have been met" (bundle `application.query.*` novo, 100% linha)
- Executado (após os 5 patches, ver Spec Change Log): `mvn -pl matching-alocacao-service -am verify` -- BUILD SUCCESS, 50 testes (0 falhas), JaCoCo "All coverage checks have been met"

## Suggested Review Order

**O bug real (achado do code review)**

- `bootstrapar()` agora é `@Transactional` no método inteiro -- falha em qualquer linha do lote reverte tudo, a réplica continua vazia e o próximo `GET /v1/fila` reexecuta o bootstrap completo.
  [`ScoreBootstrapService.java:65`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/bootstrap/ScoreBootstrapService.java#L65)

- `catch (RestClientException e)` restrito à chamada HTTP -- falha de upsert já não é mascarada como "serviço indisponível" (achado do code review).
  [`ScoreBootstrapService.java:69`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/bootstrap/ScoreBootstrapService.java#L69)

**Consulta da fila (entrada)**

- `GET /v1/fila`: delega ao caso de uso, sem lógica própria.
  [`FilaController.java:28`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/FilaController.java#L28)

- `consultar()`: `synchronized` evita bootstrap duplicado sob concorrência (achado do code review); lê, calcula Prioridade Efetiva, ordena decrescente com desempate estável por `occurredAt`.
  [`ConsultarFilaPriorizada.java:62`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarFilaPriorizada.java#L62)

**Cálculo (decisão de design)**

- `calcular()`: Aging com teto, `horas_espera` nunca negativa mesmo sob defasagem de relógio.
  [`PrioridadeEfetiva.java:45`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/PrioridadeEfetiva.java#L45)

**Testes**

- Prova o bug real corrigido: lote parcial reverte inteiro, réplica continua vazia.
  [`FilaBootstrapIntegrationTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/FilaBootstrapIntegrationTest.java#L1)

- Concorrência: N requisições paralelas disparam bootstrap uma única vez.
  [`ConsultarFilaPriorizadaTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/query/ConsultarFilaPriorizadaTest.java#L1)

- Domínio: teto de aging, `horas_espera` negativa.
  [`PrioridadeEfetivaTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/domain/PrioridadeEfetivaTest.java#L1)
- Reexecutado após os 5 patches do code review (ver Spec Change Log): `mvn -pl matching-alocacao-service -am verify` -- BUILD SUCCESS, 50 testes (0 falhas, +3 novos: `FilaBootstrapIntegrationTest` Patch 1, `ConsultarFilaPriorizadaTest` Patch 2 e Patch 5), JaCoCo "All coverage checks have been met"
