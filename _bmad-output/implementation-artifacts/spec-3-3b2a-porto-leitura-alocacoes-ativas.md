---
title: 'Porto de Leitura de Alocações Ativas'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'ba088aa65edb3702d17ffcae2ef346b952a6cd64'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** não existe hoje nenhuma forma de consultar, do lado de leitura, quais Pacientes têm `Alocacao` ativa — só a porta de escrita (`application.command.AlocacaoRepositorio`, Story 3-3b1) existe, criada para o comando de confirmação, e ela não expõe consulta.

**Approach:** novo porto `AlocacaoConsultaRepositorio` (`application/query`) expõe `pacientesComAlocacaoAtiva(): Set<Long>`, implementado por `AlocacaoConsultaRepositorioAdapter` reaproveitando o `AlocacaoJpaRepository` já existente. Puramente aditivo — nenhum consumidor é ligado a este porto ainda (isso é a Story 3-3b2b, deferida).

## Boundaries & Constraints

**Always:**
- `AlocacaoConsultaRepositorio` expõe só `Set<Long> pacientesComAlocacaoAtiva()`, filtrando por `Alocacao.STATUS_ATIVA` (`Alocacao.java:22` — reusar a constante, nunca repetir o literal `"ATIVA"`). Javadoc segue a fórmula já usada por `RecursoConsultaRepositorio`, citando `application.command.AlocacaoRepositorio` como irmã de escrita (Story 3-3b1).
- Implementado por `AlocacaoConsultaRepositorioAdapter` (`infrastructure/persistence`, `@Component` package-private, `@Transactional(readOnly = true)`) — mesmo split de `RecursoRepositorioAdapter`/`RecursoConsultaRepositorioAdapter` sobre um único `RecursoJpaRepository`.
- `AlocacaoJpaRepository` (hoje só tem `inserir`, `AlocacaoJpaRepository.java:11-29`) ganha um método novo de query nativa (mesmo padrão 100% nativo já usado por `inserir`) retornando os `paciente_id` com `status = :status`.
- Tabela vazia ou sem nenhuma linha `ATIVA` → `Set` vazio, nunca exceção.

**Ask First:** nenhuma decisão de infraestrutura ou schema nova é esperada (tabela/índices de `alocacao` já existem desde 3-3b1) — se qualquer necessidade nova surgir durante a implementação, HALT e pergunte antes de prosseguir.

**Never:** ligar este porto a `ConsultarFilaPriorizada` ou a qualquer outro consumidor (Story 3-3b2b, deferida em `deferred-work.md`); alterar `AlocacaoRepositorio` (porta de escrita) ou `ConfirmarAlocacao`; qualquer mudança em `FilaController`/`FilaItemResponse`/`GET /v1/fila`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Alocação ativa existente | `Alocacao` com `status='ATIVA'` persistida para `pacienteId=X` | `pacientesComAlocacaoAtiva()` retorna `Set` contendo `X` | N/A |
| Nenhuma Alocação no sistema | Tabela `alocacao` vazia | `pacientesComAlocacaoAtiva()` retorna `Set` vazio | N/A |
| Múltiplos Pacientes alocados | 2+ `Alocacao` ATIVA para Pacientes distintos | `Set` contém todos os `pacienteId` correspondentes | N/A |

</frozen-after-approval>

## Code Map

- `matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/AlocacaoConsultaRepositorio.java` (novo) -- porta de leitura, `Set<Long> pacientesComAlocacaoAtiva()`, modelo: `application/query/RecursoConsultaRepositorio.java`
- `matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/AlocacaoJpaRepository.java` (modificado, hoje só tem `inserir`, linhas 11-29) -- novo método `@Query(nativeQuery = true)` retornando `paciente_id` `WHERE status = :status`
- `matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/AlocacaoConsultaRepositorioAdapter.java` (novo) -- `@Component`, implementa a porta, modelo: `infrastructure/persistence/RecursoConsultaRepositorioAdapter.java`
- `matching-alocacao-service/src/main/java/com/filajusta/matching/domain/Alocacao.java:22` -- reusar `STATUS_ATIVA`, sem modificar
- `matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/AlocacaoConsultaRepositorioAdapterIntegrationTest.java` (novo) -- Testcontainers, insere via `AlocacaoRepositorio#confirmar`, confere `pacientesComAlocacaoAtiva()`, modelo: `AlocacaoRepositorioAdapterIntegrationTest.java`/`RecursoConsultaRepositorioAdapterIntegrationTest.java`

## Tasks & Acceptance

**Execution:**
- [x] `AlocacaoConsultaRepositorio.java` -- nova porta -- `Set<Long> pacientesComAlocacaoAtiva()`
- [x] `AlocacaoJpaRepository.java` -- novo método de query nativa -- fonte dos `pacienteId` com status ATIVA
- [x] `AlocacaoConsultaRepositorioAdapter.java` -- implementa a porta -- reaproveita `AlocacaoJpaRepository`
- [x] `AlocacaoConsultaRepositorioAdapterIntegrationTest.java` -- cobre a I/O Matrix contra Postgres real via Testcontainers

**Acceptance Criteria:**
- Given uma `Alocacao` ATIVA persistida para um `pacienteId`, when `AlocacaoConsultaRepositorio.pacientesComAlocacaoAtiva()` é chamado, then o `Set` retornado contém esse `pacienteId`
- Given nenhuma `Alocacao` ativa no sistema, when `pacientesComAlocacaoAtiva()` é chamado, then retorna `Set` vazio
- Given este porto implementado, when `mvn -pl matching-alocacao-service -am verify` roda, then nenhum consumidor existente (`ConsultarFilaPriorizada`, `GET /v1/fila`) muda de comportamento

## Spec Change Log

- **2026-09-11 (step-02 checkpoint, token count):** Spec original de 3-3b2 (~2067 tokens estimados, alvo 900-1600) cruzava a criação deste porto de leitura com a aplicação do filtro em `ConsultarFilaPriorizada.consultar()`. Decisão do usuário: dividir em cascata 3-3b2a (este spec, porto + adapter + teste de integração) → 3-3b2b (aplicar o filtro na fila, deferida em `deferred-work.md`), mesmo padrão das Stories 3.1/3.2/3.2b/3-3a/3-3b.

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%

## Suggested Review Order

**Contrato do porto de leitura**

- Entry point: único método exposto, `Set<Long>` puro -- sem consumidor ligado ainda (aditivo por design).
  [`AlocacaoConsultaRepositorio.java:31`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/AlocacaoConsultaRepositorio.java#L31)

**Implementação: adapter + query nativa**

- Query nativa 100% SQL (mesmo padrão do `inserir` já existente) filtra por `status = :status`, nunca lança exceção.
  [`AlocacaoJpaRepository.java:36`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/AlocacaoJpaRepository.java#L36)

- Adapter fino: só delega, injeta `Alocacao.STATUS_ATIVA` (constante do domínio, nunca literal repetido).
  [`AlocacaoConsultaRepositorioAdapter.java:30`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/AlocacaoConsultaRepositorioAdapter.java#L30)

**Testes de integração (Postgres real via Testcontainers)**

- Prova que o `WHERE` de fato discrimina -- sem este teste uma query sem filtro nenhum passaria nos demais (achado do code review).
  [`AlocacaoConsultaRepositorioAdapterIntegrationTest.java:118`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/AlocacaoConsultaRepositorioAdapterIntegrationTest.java#L118)

- Isolamento por estado (`deleteAll()` a cada teste), não por ordem de execução -- robusto a novos testes futuros.
  [`AlocacaoConsultaRepositorioAdapterIntegrationTest.java:64`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/AlocacaoConsultaRepositorioAdapterIntegrationTest.java#L64)

- Tabela genuinamente vazia -> `Set` vazio, prova literal da linha da I/O Matrix.
  [`AlocacaoConsultaRepositorioAdapterIntegrationTest.java:84`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/AlocacaoConsultaRepositorioAdapterIntegrationTest.java#L84)

- Caminho feliz simples e múltiplos Pacientes alocados -- cobertura direta da I/O Matrix.
  [`AlocacaoConsultaRepositorioAdapterIntegrationTest.java:96`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/AlocacaoConsultaRepositorioAdapterIntegrationTest.java#L96)
