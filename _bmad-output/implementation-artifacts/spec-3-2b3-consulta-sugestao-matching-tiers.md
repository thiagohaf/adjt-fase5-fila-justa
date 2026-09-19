---
title: 'Story 3-2b3 — Consulta de Sugestão de Matching com Algoritmo de Tiers'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 1
context: []
baseline_commit: '05038b049b9b74317d7ad73c56fbfda115c23824'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Um Regulador não tem como saber qual Paciente sugerir para um Recurso específico sem selecionar manualmente da fila, o que quebra a garantia de justiça objetiva do FilaJusta (AD-5).

**Approach:** Novo endpoint `GET /v1/recursos/{id}/sugestao` em `matching-alocacao-service` que aplica o algoritmo de tiers de desempate sobre a fila global já ordenada por Prioridade Efetiva (`ConsultarFilaPriorizada`, reutilizada sem duplicar lógica), consultando o domínio `Recurso` (3-2b2) para determinar quantos tiers mais genéricos e disponíveis existem antes de indexar na fila.

## Boundaries & Constraints

**Always:**
- Reutilizar `ConsultarFilaPriorizada.consultar()` para obter a fila global ordenada (Prioridade Efetiva desc, `occurredAt` asc, `numeroSequencialTriagem` asc) — nunca recalcular Prioridade Efetiva/Aging aqui.
- N = quantidade de valores DISTINTOS de `especificidadeRank` estritamente menores (`< r`) que o do Recurso consultado, com pelo menos 1 Recurso `disponivel=true` naquele tier. Recursos do mesmo tier consomem 1 posição no total, nunca uma por Recurso.
- Sugestão = `filaGlobal[N]` (índice 0); sempre recalculada na consulta (sem cache, sem reserva de Paciente).
- Recurso inexistente → `404` RFC 7807 (mesmo padrão de `TriagemNaoEncontradaException`/`TriagemExceptionHandler`).
- `{id}` malformado (não-UUID) → `400` RFC 7807 (mesmo padrão de `MethodArgumentTypeMismatchException` em `TriagemExceptionHandler`).
- Fila global esgotada antes do índice N → resposta `200` indicando ausência de sugestão, nunca erro (requisito do epic: "nenhum Recurso elegível retorna erro").
- Recurso existente porém `disponivel=false` → resposta `200` indicando ausência de sugestão (mesmo tratamento de "fila esgotada" — Recurso indisponível nunca é elegível, decisão do usuário no loopback de review multi-agente de 2026-09-11), nunca calcula/retorna uma sugestão para ele.

**Ask First:** Nenhuma decisão bloqueante identificada nesta story. Se o dataset de seed introduzir `especificidadeRank` não-contíguo ou zero, parar e perguntar antes de assumir semântica.

**Never:**
- Não aloca, reserva ou marca o Recurso/Paciente (fora de escopo — Story 3.3).
- Não implementa JWT/segurança neste controller — mesmo padrão hoje de `GET /v1/fila` (roteamento no gateway ainda pendente, gap pré-existente, não desta story).
- Não expõe `numeroSequencialTriagem` na resposta HTTP (fator interno de desempate, mesmo padrão de `FilaItemResponse`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HAPPY_PATH | Recurso rank=2, 1 Recurso disponível rank=1, fila global com 5 Pacientes | N=1 → sugestão = `filaGlobal[1]` | N/A |
| SEM_RECURSO_GENERICO_DISPONIVEL | Recurso rank=1 (mais genérico possível), nenhum Recurso rank<1 existe | N=0 → sugestão = `filaGlobal[0]` | N/A |
| MESMO_TIER_NAO_BLOQUEIA | 2 Recursos disponíveis rank=1, Recurso consultado rank=2 | N=1 (não 2) → sugestão = `filaGlobal[1]` | N/A |
| FILA_ESGOTADA | N ≥ tamanho da fila global | `200`, corpo indica ausência de sugestão | N/A |
| RECURSO_INEXISTENTE | `id` válido (UUID) mas sem registro | — | `404` RFC 7807 |
| ID_MALFORMADO | `id` não é um UUID válido | — | `400` RFC 7807 |
| RECURSO_INDISPONIVEL | Recurso existe, `disponivel=false` | `200`, corpo indica ausência de sugestão (mesmo formato de FILA_ESGOTADA) | N/A |

</frozen-after-approval>

## Code Map

- `application/query/ConsultarSugestaoRecurso.java` (novo) -- caso de uso: resolve `Recurso` por id, calcula N via `RecursoConsultaRepositorio`, obtém fila via `ConsultarFilaPriorizada.consultar()` (injetada), aplica o algoritmo, padrão `ConsultarFilaPriorizada.java`
- `application/query/RecursoConsultaRepositorio.java` (novo) -- porta: `Optional<Recurso> buscarPorId(UUID)`, `int contarTiersMaisGenericosDisponiveis(int especificidadeRank)`; separada de `application/command/RecursoRepositorio.java` (só upsert), mesmo split CQRS de `FilaRepositorio` vs `ScoreReplicaRepositorio`
- `application/query/RecursoNaoEncontradoException.java` (novo) -- `RuntimeException`, padrão `triagem-score-service/.../TriagemNaoEncontradaException.java`
- `infrastructure/persistence/RecursoJpaRepository.java` (modificado) -- adiciona `@Query(nativeQuery=true)` `SELECT COUNT(DISTINCT especificidade_rank) ... WHERE disponivel=true AND especificidade_rank < :rank`; `findById` já herdado de `JpaRepository`
- `infrastructure/persistence/RecursoConsultaRepositorioAdapter.java` (novo) -- `@Component`, delega a `RecursoJpaRepository`; mapeamento entidade→domínio via `RecursoJpaEntity.paraDominio()` (extraído para não duplicar com `RecursoRepositorioAdapter`, patch do code review)
- `infrastructure/persistence/RecursoJpaEntity.java` (modificado) -- adiciona `paraDominio()` package-private, compartilhado pelos dois adapters (comando e consulta)
- `infrastructure/web/RecursoSugestaoController.java` (novo) -- `GET /v1/recursos/{id}/sugestao`, sem segurança (mesmo padrão de `FilaController.java:28-33`)
- `infrastructure/web/SugestaoRecursoResponse.java` (novo) -- `record(UUID recursoId, Long pacienteId)`, `pacienteId=null` quando não há sugestão, factory `de(...)`, padrão `RecursoResponse.java`
- `infrastructure/web/RecursosExceptionHandler.java` (modificado) -- adiciona `@ExceptionHandler(RecursoNaoEncontradoException.class)`→404 e `@ExceptionHandler(MethodArgumentTypeMismatchException.class)`→400, mesmo padrão `TriagemExceptionHandler.java:92-105`
- `MatchingAlocacaoServiceApplication.java` (modificado, linhas ~69-78) -- `@Bean` novo para `ConsultarSugestaoRecurso` e `RecursoConsultaRepositorioAdapter`, mesmo padrão dos beans existentes

## Tasks & Acceptance

**Execution:**
- [x] `RecursoConsultaRepositorio.java`/`RecursoConsultaRepositorioAdapter.java`/query nativa em `RecursoJpaRepository.java` -- porta e adapter de consulta
- [x] `RecursoNaoEncontradoException.java` -- exceção de domínio/aplicação
- [x] `ConsultarSugestaoRecurso.java` -- algoritmo de tiers
- [x] `RecursoSugestaoController.java`/`SugestaoRecursoResponse.java` -- `GET /v1/recursos/{id}/sugestao`
- [x] `RecursosExceptionHandler.java` -- handlers 404/400
- [x] `MatchingAlocacaoServiceApplication.java` -- wiring dos novos beans
- [x] Testes unitários cobrindo a I/O Matrix (`ConsultarSugestaoRecursoTest`, padrão `ConsultarFilaPriorizadaTest.java`)
- [x] Teste de integração de persistência (Testcontainers) para a query de contagem de tiers, padrão `RecursoRepositorioAdapterIntegrationTest.java`
- [x] `RecursoSugestaoControllerIntegrationTest.java` (novo, adicionado na Matrix Test Audit do step-03) -- teste HTTP de ponta a ponta (Testcontainers) para as linhas RECURSO_INEXISTENTE (404) e ID_MALFORMADO (400) da I/O Matrix, que `ConsultarSugestaoRecursoTest` (unitário, mocks) não exercitava no nível de wire format; revelou e corrigiu um bug real: `@PathVariable UUID id` sem nome explícito quebrava com `500` em toda chamada ao endpoint (parameter name não disponível via reflection sem `-parameters`), corrigido para `@PathVariable("id") UUID id`, mesmo padrão de `TriagemController`
- [x] Loop 1 (code review multi-agente): guard clause `!recurso.isDisponivel()` em `ConsultarSugestaoRecurso` (novo cenário `RECURSO_INDISPONIVEL`) + teste unitário + teste HTTP (200 com `pacienteId` null); `Math.toIntExact` no cast de `long`→`int` em `RecursoConsultaRepositorioAdapter`; `RecursoJpaEntity.paraDominio()` extraído para eliminar duplicação entre os dois adapters; notação `Story 3-2b3`→`3.2b3` normalizada nos arquivos desta story; 2 testes HTTP de ponta a ponta adicionados (`HAPPY_PATH` com corpo `200` real via `score_replica` semeada por `JdbcTemplate`, `RECURSO_INDISPONIVEL` com `pacienteId` null serializado)

**Acceptance Criteria:**
- Given um Recurso com Recursos disponíveis em N tiers estritamente mais genéricos, when `GET /v1/recursos/{id}/sugestao` é chamado, then retorna `filaGlobal[N]`
- Given Recursos do mesmo tier do Recurso consultado, when a sugestão é calculada, then eles não incrementam N
- Given a fila global esgotada antes do índice N, when a sugestão é consultada, then resposta `200` sem erro, indicando ausência
- Given um Recurso existente com `disponivel=false`, when a sugestão é consultada, then resposta `200` sem erro, `pacienteId` null (mesmo tratamento de fila esgotada)
- Given um `recursoId` inexistente, when consultado, then `404` RFC 7807
- Given um `id` não-UUID no path, when consultado, then `400` RFC 7807

## Spec Change Log

- **2026-09-11, loop 1 (intent_gap):** Review multi-agente (blind-hunter + edge-case-hunter, achado convergente) apontou que a spec original não definia o comportamento de `GET /v1/recursos/{id}/sugestao` quando o próprio Recurso consultado está `disponivel=false` — o código calculava e retornava uma sugestão normalmente, o que pode induzir o Regulador a pensar que um Recurso ocupado está livre. Como havia mais de uma leitura plausível (sem sugestão vs erro `409` vs calcular mesmo assim como "preview"), foi ao usuário para decisão explícita. Decisão: tratar como `FILA_ESGOTADA` — `200`, `pacienteId=null`, nunca erro (consistente com o invariante do epic "nenhum Recurso elegível retorna erro" — um Recurso indisponível não é elegível). Amendado: Boundaries "Always" (nova regra) e I/O Matrix (nova linha `RECURSO_INDISPONIVEL`). **KEEP:** o algoritmo de tiers em si (cálculo de N, indexação em `filaGlobal[N]`, reuso de `ConsultarFilaPriorizada`) estava correto e não muda — só um guard clause adicional antes de calcular N.

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%

## Suggested Review Order

**Algoritmo de tiers (entrada)**

- Ponto de entrada do algoritmo: resolve o Recurso, aplica o guard de indisponibilidade, calcula N e indexa na fila global.
  [`ConsultarSugestaoRecurso.java:49`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarSugestaoRecurso.java#L49)

- Guard clause adicionado no loop 1 do review: Recurso indisponível nunca é elegível, mesmo tratamento de fila esgotada.
  [`ConsultarSugestaoRecurso.java:53`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarSugestaoRecurso.java#L53)

- Porta de consulta nova (CQRS), separada do upsert de 3.2b2 -- só o essencial para o algoritmo.
  [`RecursoConsultaRepositorio.java:19`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/RecursoConsultaRepositorio.java#L19)

**Contagem de tiers (persistência)**

- Query nativa `COUNT(DISTINCT especificidade_rank)` -- núcleo do "N" do algoritmo, Recursos do mesmo tier não se somam.
  [`RecursoJpaRepository.java:53`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoJpaRepository.java#L53)

- `Math.toIntExact` no cast `long`→`int` (patch do review, evita overflow silencioso).
  [`RecursoConsultaRepositorioAdapter.java:45`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoConsultaRepositorioAdapter.java#L45)

- Mapeamento entidade→domínio extraído para a própria entidade JPA, compartilhado com o adapter de comando (patch do review, elimina duplicação).
  [`RecursoJpaEntity.java:61`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoJpaEntity.java#L61)

**Endpoint HTTP e tratamento de erro**

- `GET /v1/recursos/{id}/sugestao`, sem segurança (mesmo gap pré-existente de `GET /v1/fila`).
  [`RecursoSugestaoController.java:38`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecursoSugestaoController.java#L38)

- `404` para Recurso inexistente e `400` para `{id}` malformado, ambos RFC 7807, mesmo padrão de `TriagemExceptionHandler`.
  [`RecursosExceptionHandler.java:84`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecursosExceptionHandler.java#L84)

- `pacienteId` nulo quando não há sugestão (fila esgotada ou Recurso indisponível) -- nunca omitido do JSON.
  [`SugestaoRecursoResponse.java:16`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/SugestaoRecursoResponse.java#L16)

- Wiring do novo caso de uso no composition root, mesmo padrão dos beans existentes.
  [`MatchingAlocacaoServiceApplication.java:87`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/MatchingAlocacaoServiceApplication.java#L87)

**Testes**

- Cobertura unitária completa da I/O Matrix (mocks), incluindo o caso `RECURSO_INDISPONIVEL` adicionado no loop 1.
  [`ConsultarSugestaoRecursoTest.java:130`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/query/ConsultarSugestaoRecursoTest.java#L130)

- Teste HTTP de ponta a ponta do caminho feliz (200 real, fila semeada via JdbcTemplate) -- fechado na Matrix Test Audit/loop 1, não existia na primeira versão.
  [`RecursoSugestaoControllerIntegrationTest.java:99`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/RecursoSugestaoControllerIntegrationTest.java#L99)

- Teste HTTP de `RECURSO_INDISPONIVEL` (200, `pacienteId` null serializado, não omitido).
  [`RecursoSugestaoControllerIntegrationTest.java:118`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/RecursoSugestaoControllerIntegrationTest.java#L118)

- Testes de `404`/`400` que revelaram o bug do `@PathVariable` sem nome explícito (corrigido no step-03).
  [`RecursoSugestaoControllerIntegrationTest.java:132`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/RecursoSugestaoControllerIntegrationTest.java#L132)
