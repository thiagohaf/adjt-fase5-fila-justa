---
title: 'Confirmação da Sugestão de Matching (criação de Alocação)'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'e83f50ebf19a0fea6f838ab601cf1206e58dd207'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `GET /v1/recursos/{id}/sugestao` (Story 3.2b3) só indica qual Paciente seria elegível para um Recurso — nunca aloca. Sem confirmação, o Recurso nunca sai do pool e o mesmo Paciente pode ser sugerido para vários Recursos ao mesmo tempo indefinidamente.

**Approach:** `POST /v1/recursos/{id}/alocacoes` cria uma `Alocacao` (domínio novo), protegida por 2 índices únicos parciais no banco (um Recurso só tem 1 Alocação ativa; um Paciente só tem 1 Alocação ativa), marca o Recurso indisponível e publica `AlocacaoConfirmada` via outbox — tudo na mesma transação local, mesmo padrão de `RegistrarTriagem` (`triagem-score-service`).

## Boundaries & Constraints

**Always:**
- `Alocacao` imutável: `alocacaoId` (UUID v4), `recursoId`, `pacienteId` (long), `status` ("ATIVA", único valor por ora), `confirmadoEm` (Instant).
- Persistência via 2 índices únicos parciais `WHERE status = 'ATIVA'` — um em `recurso_id`, outro em `paciente_id` (Technical Decision do epic-3-context.md). O índice é a única fonte de verdade sob concorrência: nunca decidir 409 por checagem em memória antes do INSERT.
- `ConfirmarAlocacao` (novo, `application/command`): 1) INSERT `Alocacao` (índice decide 409 via `DataIntegrityViolationException`, inspecionando o nome da constraint violada), 2) `RecursoRepositorio.marcarIndisponivel(recursoId)` (novo método na porta existente), 3) grava `EventoOutbox` `"AlocacaoConfirmada"` (payload: `alocacaoId, recursoId, pacienteId, confirmadoEm`) — um único `@Transactional`, mesmo padrão de `RegistrarTriagem.registrar`.
- `correlationId`: lido de `X-Correlation-Id` (header opcional) pelo controller, mesma validação de `RegistrarTriagem.correlationIdEfetivo` (gerado UUID se ausente/em branco; `> 128 chars` vira `400` via `CorrelationIdInvalidoException`, novo — mesmo padrão de `CorrelationIdInvalidoException` do `triagem-score-service`).
- `recursoId` sem registro -> `404` via `RecursoNaoEncontradoException` (já existe, reusar).
- `matching_alocacao.alocacao` (migration `V5`) segue o schema `matching_alocacao` já existente (mesmo padrão de `V3__create_recurso.sql`).

**Ask First:** nenhuma decisão de infraestrutura nova é esperada (outbox/SNS já provisionados na Story 3-3a) — se qualquer necessidade de infraestrutura nova surgir durante a implementação, HALT e pergunte antes de prosseguir.

**Never:** exclusão de Pacientes com Alocação ativa da fila priorizada, `GET /v1/fila` (deferida para Story 3-3b2 -- ver `deferred-work.md`); recusa da sugestão e rastreamento `SugestaoGerada`/AD-10 (Story 3-3c); liberação automática do Recurso (Story 3.4); autenticação/roteamento no gateway para este endpoint (gap pré-existente, mesmo padrão de `FilaController`/`RecursoSugestaoController`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Confirmação feliz | `POST /v1/recursos/{id}/alocacoes` com `pacienteId` válido, Recurso disponível, Paciente sem Alocação ativa | `201` com a Alocação criada; Recurso vira indisponível; evento `AlocacaoConfirmada` gravado no outbox | N/A |
| Recurso já alocado | Segunda confirmação concorrente para o mesmo `recursoId` | A 2ª chamada recebe `409` | `RecursoJaAlocadoException`, mapeada do índice único de `recurso_id` |
| Paciente já alocado | `pacienteId` já tem Alocação ativa para outro Recurso | `409` | `PacienteJaAlocadoException`, mapeada do índice único de `paciente_id` |
| Recurso inexistente | `recursoId` sem registro em `matching_alocacao.recurso` | `404` | `RecursoNaoEncontradoException` (existente) |
| Corpo inválido | `pacienteId` ausente ou não-numérico no JSON | `400` | Handlers já existentes (`MethodArgumentNotValidException`/`HttpMessageNotReadableException`) |
| `correlationId` excede limite | Header `X-Correlation-Id` com mais de 128 caracteres | `400` | `CorrelationIdInvalidoException` (novo) |

</frozen-after-approval>

## Code Map

- `matching-alocacao-service/src/main/java/com/filajusta/matching/domain/Alocacao.java` (novo) -- domínio imutável, mesma forma de `Recurso.java`
- `matching-alocacao-service/.../application/command/AlocacaoRepositorio.java` (novo, porta) -- `confirmar(Alocacao): Alocacao`, lança `RecursoJaAlocadoException`/`PacienteJaAlocadoException`
- `matching-alocacao-service/.../application/command/RecursoJaAlocadoException.java`, `PacienteJaAlocadoException.java` (novos)
- `matching-alocacao-service/.../application/command/CorrelationIdInvalidoException.java` (novo) -- mesma forma de `triagem-score-service/.../domain/CorrelationIdInvalidoException.java`
- `matching-alocacao-service/.../application/command/RecursoRepositorio.java` (modificado) -- novo método `marcarIndisponivel(UUID recursoId)`
- `matching-alocacao-service/.../application/command/ConfirmarAlocacao.java` (novo) -- checa `recursoId` via `RecursoConsultaRepositorio.buscarPorId` (404 se ausente), depois orquestra os 3 passos `@Transactional`, mesmo padrão de `RegistrarTriagem.java` (`triagem-score-service`)
- `matching-alocacao-service/.../infrastructure/persistence/AlocacaoJpaEntity.java`, `AlocacaoJpaRepository.java` (novos) -- INSERT nativo, mesmo padrão de `RecursoJpaRepository.upsert`
- `matching-alocacao-service/.../infrastructure/persistence/AlocacaoRepositorioAdapter.java` (novo) -- implementa `AlocacaoRepositorio`; captura `DataIntegrityViolationException`, inspeciona nome da constraint (`ux_alocacao_recurso_ativa` / `ux_alocacao_paciente_ativa`) para mapear a exceção correta
- `matching-alocacao-service/.../infrastructure/persistence/RecursoJpaRepository.java`/`RecursoRepositorioAdapter.java` (modificados) -- `UPDATE ... SET disponivel = false WHERE recurso_id = :id`
- `matching-alocacao-service/.../infrastructure/web/AlocacaoController.java` (novo) -- `POST /v1/recursos/{id}/alocacoes`, lê `X-Correlation-Id`, mesmo padrão de `TriagemController` (`triagem-score-service`)
- `matching-alocacao-service/.../infrastructure/web/ConfirmarAlocacaoRequest.java`, `AlocacaoResponse.java` (novos)
- `matching-alocacao-service/.../infrastructure/web/RecursosExceptionHandler.java` (modificado) -- novos handlers para `RecursoJaAlocadoException`/`PacienteJaAlocadoException` (`409`) e `CorrelationIdInvalidoException` (`400`); generalizar a mensagem de `handleValidacaoInvalida` (hoje hardcoded para `UpsertRecursoRequest`) para cobrir também `ConfirmarAlocacaoRequest`
- `matching-alocacao-service/src/main/resources/db/migration/V5__create_alocacao.sql` (novo) -- tabela `matching_alocacao.alocacao` + os 2 índices únicos parciais, mesmo padrão de `V3__create_recurso.sql`

## Tasks & Acceptance

**Execution:**
- [x] `V5__create_alocacao.sql` -- tabela `alocacao` + 2 índices únicos parciais (`recurso_id`, `paciente_id`) `WHERE status='ATIVA'`
- [x] `domain/Alocacao.java` -- domínio imutável com validação de invariantes básicas
- [x] `application/command/{AlocacaoRepositorio,RecursoJaAlocadoException,PacienteJaAlocadoException,CorrelationIdInvalidoException}.java`
- [x] `application/command/RecursoRepositorio.java` -- `marcarIndisponivel(UUID)`
- [x] `application/command/ConfirmarAlocacao.java` -- checa `recursoId` via `RecursoConsultaRepositorio.buscarPorId` (404 se ausente), orquestração transacional (insere Alocação, marca Recurso indisponível, grava outbox)
- [x] `infrastructure/persistence/Alocacao*.java` -- persistência + mapeamento de constraint violada
- [x] `infrastructure/persistence/Recurso*.java` -- `marcarIndisponivel`
- [x] `infrastructure/web/AlocacaoController.java` + DTOs + `RecursosExceptionHandler.java`
- [x] Testes unitários: `AlocacaoTest`, `ConfirmarAlocacaoTest` (mock dos repositórios -- cobre os 2 cenários de 409, Recurso inexistente, correlationId inválido e o caminho feliz)
- [x] Teste de integração (Testcontainers): `AlocacaoRepositorioAdapterIntegrationTest` -- prova os 2 índices únicos parciais rejeitando de fato via constraint real de banco
- [x] Teste de integração: `AlocacaoControllerIntegrationTest` cobrindo a I/O Matrix acima

**Acceptance Criteria:**
- Given um Recurso disponível e um Paciente sem Alocação ativa, when `POST /v1/recursos/{id}/alocacoes` é chamado, then retorna `201` e o Recurso some da sugestão (`GET /v1/recursos/{id}/sugestao` -- comportamento já existente via `disponivel=false`)
- Given duas confirmações concorrentes para o mesmo `recursoId`, when ambas chegam ao banco, then exatamente uma recebe `201` e a outra `409`
- Given uma linha `EventoOutbox` gravada por `ConfirmarAlocacao`, when `RelaySnsPublisherJob` (Story 3-3a) roda, then `AlocacaoConfirmada` é publicado com o envelope padrão

## Spec Change Log

- **2026-09-11 (step-02 checkpoint, token count):** Spec original de 3-3b (~2463 tokens estimados, alvo 900-1600) cruzava confirmação/criação de Alocação e a exclusão de Pacientes alocados da fila priorizada. Decisão do usuário: dividir em cascata 3-3b1 (este spec) → 3-3b2 (exclusão da fila, deferida em `deferred-work.md`), mesmo padrão das Stories 3.1/3.2/3.2b/3.3a.

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%

## Suggested Review Order

**Orquestração e invariantes de negócio**

- Entry point: orquestra as 3 escritas transacionais e checa Recurso disponível antes de tudo (patch do code review).
  [`ConfirmarAlocacao.java:69`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/ConfirmarAlocacao.java#L69)

- Recurso indisponível sem Alocação ativa vira 409 -- índice único sozinho não cobre este caso.
  [`ConfirmarAlocacao.java:82`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/ConfirmarAlocacao.java#L82)

- `pacienteId` valida positividade no próprio domínio, não só no DTO da web (AD-2).
  [`Alocacao.java:33`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/Alocacao.java#L33)

**Concorrência via constraint de banco**

- Os 2 índices únicos parciais são a única fonte de verdade sob concorrência -- decidem os 2 cenários de 409.
  [`V5__create_alocacao.sql:21`](../../matching-alocacao-service/src/main/resources/db/migration/V5__create_alocacao.sql#L21)

- Traduz a violação de constraint (nome) para a exceção de domínio correta.
  [`AlocacaoRepositorioAdapter.java:58`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/AlocacaoRepositorioAdapter.java#L58)

- `marcarIndisponivel`: UPDATE idempotente de 1 coluna, chamado dentro da mesma transação da confirmação.
  [`RecursoJpaRepository.java:63`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/RecursoJpaRepository.java#L63)

**Superfície HTTP**

- `POST /v1/recursos/{id}/alocacoes`: lê `X-Correlation-Id`, delega ao caso de uso, sem lógica própria.
  [`AlocacaoController.java:42`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/AlocacaoController.java#L42)

- Mensagem de validação reconstruída por campo (patch do code review) -- evita vazar texto interno do Spring.
  [`RecursosExceptionHandler.java:54`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecursosExceptionHandler.java#L54)

- Novos handlers de `409` (Recurso/Paciente já alocado) e `400` (correlationId inválido).
  [`RecursosExceptionHandler.java:127`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/web/RecursosExceptionHandler.java#L127)

**Testes e rastreamento**

- Prova os 2 índices únicos rejeitando de fato contra Postgres real (Testcontainers), não só o mock.
  [`AlocacaoRepositorioAdapterIntegrationTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/infrastructure/persistence/AlocacaoRepositorioAdapterIntegrationTest.java#L1)

- Cobre a I/O Matrix completa no nível HTTP, incluindo o patch de mensagem de erro e o novo caso de indisponibilidade.
  [`AlocacaoControllerIntegrationTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/AlocacaoControllerIntegrationTest.java#L1)

- Orquestração com mocks: os 2 cenários de 409, Recurso inexistente/indisponível, correlationId inválido.
  [`ConfirmarAlocacaoTest.java:1`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/command/ConfirmarAlocacaoTest.java#L1)

- Split em cascata (3-3b1/3-3b2) e correção pós-merge de PR #30 (`sprint-status.yaml`).
  [`sprint-status.yaml:78`](../../_bmad-output/implementation-artifacts/sprint-status.yaml#L78)
