---
title: 'Consulta de Auditoria por Paciente ou Agendamento (Story 4.2)'
type: 'feature'
created: '2026-09-21'
status: 'review'
review_loop_iteration: 1
baseline_commit: 'cf58e87829251a7a55c5fdb9841635ec20a38418'
context: ['_bmad-output/implementation-artifacts/epic-4-context.md']
---

<!-- Target: 900–1300 tokens. -->

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Após Story 4.1 registrar decisões no Log Auditável, não há forma de consultá-las. Um Auditor não consegue recuperar o histórico completo de um Paciente ou Agendamento para investigar e justificar ações — faltam endpoints de leitura.

**Approach:** Criar dois endpoints REST versionados no auditoria-service: `GET /v1/auditoria/paciente/{pacienteId}` e `GET /v1/auditoria/agendamento/{agendamentoId}`, cada um retornando uma lista de decisões ordenada cronologicamente (timestamp ascendente). Quando nenhum registro existe, retornar resposta explícita "sem histórico" (nunca erro 404).

## Boundaries & Constraints

**Always:**
- Endpoints são read-only, nunca modificam dados.
- Lista sempre ordenada por `timestamp` crescente (mais antigo primeiro).
- Quando sem histórico: retornar array vazio (nunca erro 404).
- Autenticação: qualquer usuário autenticado (sem RBAC nesta fase, FR-14).
- `X-Correlation-Id` propagado em logs estruturados (NFR-2).
- Versionamento: `/v1/auditoria/*`.
- Sem paginação (Boundaries Story 4.2, Never).
- JaCoCo ≥90% cobertura na query e repositório de consulta.

**Ask First:**
- Response DTO deve incluir todos os campos de `DecisaoAuditoria` (eventId, tipoDecisao, motivo, timestamp, criadoEm) ou um subset?
- Comportamento de PathVariable UUID inválido: permitir tradução automática do Spring (400) ou custom message?

**Never:**
- Não modificar dados (read-only).
- Não paginação (deferred).
- Não criar portas de escrita novas (Story 4.1 já fornece).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| CONSULTA_PACIENTE_COM_HISTÓRICO | GET `/v1/auditoria/paciente/{id}` com pacienteId que tem 3 decisões | Retorna array com 3 DecisaoResponse, ordenado crescente por timestamp | N/A |
| CONSULTA_AGENDAMENTO_COM_HISTÓRICO | GET `/v1/auditoria/agendamento/{id}` com agendamentoId que tem 2 decisões | Retorna array com 2 DecisaoResponse, ordenado crescente por timestamp | N/A |
| CONSULTA_PACIENTE_SEM_HISTÓRICO | GET com pacienteId sem registros | Retorna array vazio `[]` | N/A |
| CONSULTA_AGENDAMENTO_SEM_HISTÓRICO | GET com agendamentoId sem registros | Retorna array vazio `[]` | N/A |
| PARAMETRO_INVALIDO_UUID | GET com `{id}` que não é UUID válido | HTTP 400 Bad Request | Validação automática Spring PathVariable |

</frozen-after-approval>

## Code Map

- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/port/DecisaoAuditoriaRepositorio.java:1-50` -- porta de persistência (Story 4.1), será estendida com métodos de consulta por paciente/agendamento
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/persistence/DecisaoAuditoriaJpaRepository.java:1-60` -- JPA repository, adicionar custom @Query para ordenação cronológica
- `auditoria-service/src/main/java/com/confirmasus/auditoria/domain/DecisaoAuditoria.java:1-80` -- entidade de domínio (append-only), será lida (nunca modificada)
- `matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/web/RecursoSugestaoController.java:1-42` -- padrão de @GetMapping + PathVariable UUID + DTO.de() para modelar comportamento
- `matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/web/FilaController.java:1-34` -- padrão de @GetMapping que retorna List<Response> com stream().map(...).toList()
- `auth-service/src/main/java/com/confirmasus/auth/infrastructure/web/AuthController.java:1-29` -- padrão de @RestController com DI via construtor

## Tasks & Acceptance

**Execution:**
- [x] `auditoria-service/src/main/java/com/confirmasus/auditoria/application/port/DecisaoAuditoriaRepositorio.java` -- estender porta com `findByPacienteIdOrderByTimestamp()` e `findByAgendamentoIdOrderByTimestamp()`
- [x] `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/persistence/DecisaoAuditoriaJpaRepository.java` -- implementar @Query custom para ordenação cronológica
- [x] `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaPaciente.java` -- query use case que busca histórico por pacienteId
- [x] `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaAgendamento.java` -- query use case que busca histórico por agendamentoId
- [x] `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaController.java` -- controller REST com endpoints `/v1/auditoria/paciente/{id}` e `/v1/auditoria/agendamento/{id}`
- [x] `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/DecisaoAuditoriaResponse.java` -- DTO com static factory `de(DecisaoAuditoria)`
- [x] `auditoria-service/src/test/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaPacienteTest.java` -- teste unitário da query
- [x] `auditoria-service/src/test/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaAgendamentoTest.java` -- teste unitário da query
- [x] `auditoria-service/src/test/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaControllerIntegrationTest.java` -- teste de integração cobrindo ambos endpoints (histórico, sem histórico, 404 UUID inválido)

**Acceptance Criteria:**
- Given um histórico com 3 decisões para pacienteId X, when GET `/v1/auditoria/paciente/X`, then resposta é array com 3 items, ordenados por timestamp crescente, cada um com tipoDecisao, motivo (se aplicável), e timestamp
- Given um agendamentoId Y sem registros, when GET `/v1/auditoria/agendamento/Y`, then resposta é array vazio (não erro)
- Given UUID inválido no path, when GET `/v1/auditoria/paciente/nao-uuid`, then retorna HTTP 400
- Given histórico com evento RECUSA + LIBERACAO (2 eventos), when GET `/v1/auditoria/agendamento/{id}`, then ordem retornada respeita timestamp crescente (evento mais antigo primeiro)

## Design Notes

A camada de aplicação (query) não validará existência de paciente/agendamento — apenas retorna o histórico de decisões registradas. Resposta vazia é sucesso, não erro, alinhado com FR-13 (consulta explícita sem histórico).

A ordenação acontece no banco (SQL `ORDER BY timestamp ASC`) em vez de em memória, garantindo escalabilidade quando o histórico crescer.

Na v2 (após review 1), X-Correlation-Id deve ser lido da request header e propagado ao MDC para structured logging conforme NFR-2. Isso garante tracing distribuído entre serviços.

## Spec Change Log

- **Review 1 (2026-09-21):** Finding "X-Correlation-Id not read from request or propagated to logs" (verification_gap → bad_spec). Amended Design Notes com instrução explícita para ler header `X-Correlation-Id` e propagar ao MDC. KEEP: Read-only e ordenação são corretos.

## Verification

**Commands:**
- `cd auditoria-service && mvn clean test` -- rodar testes unitários + integração
- `mvn jacoco:report` -- gerar cobertura JaCoCo, validar ≥90% em `com.confirmasus.auditoria.application.query.*`
- Inspecionar `http://localhost:8086/v1/auditoria/paciente/{sample-uuid}` manualmente (mock de dados de teste)

**Manual checks:**
- Verificar que ambos endpoints retornam array (nunca erro 404)
- Verificar que lista está sempre ordenada crescente por timestamp
- Verificar que X-Correlation-Id aparece nos logs de cada consulta

## Suggested Review Order

**REST Entry Point — X-Correlation-Id propagation**

- Endpoints definem contrato e propagam correlationId aos use cases
  [`AuditoriaController.java:1-68`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaController.java#L1)

**Query Use Cases — Business logic com transactionality e MDC**

- Lê correlationId e propaga ao MDC para structured logging (NFR-2)
  [`ConsultarAuditoriaPaciente.java:1-50`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaPaciente.java#L1)

- Mesmo padrão para agendamento, delegação ao repositório
  [`ConsultarAuditoriaAgendamento.java:1-50`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaAgendamento.java#L1)

**Persistence Layer — Read-only queries com ordenação e null-safety**

- Extensão de porta com assinaturas de método para busca ordenada
  [`DecisaoAuditoriaRepositorio.java:40-60`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/application/port/DecisaoAuditoriaRepositorio.java#L40)

- JPA @Query com ORDER BY timestamp ASC, garantindo escalabilidade
  [`DecisaoAuditoriaJpaRepository.java:1-80`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/persistence/DecisaoAuditoriaJpaRepository.java#L1)

- Adapter com null-checks defensivos antes de .stream()
  [`DecisaoAuditoriaRepositorioAdapter.java:60-75`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/persistence/DecisaoAuditoriaRepositorioAdapter.java#L60)

**DTOs & Error Handling — Type-safe responses**

- DTO factory com null-guard, lança IllegalArgumentException se decisão for null
  [`DecisaoAuditoriaResponse.java:35-45`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/DecisaoAuditoriaResponse.java#L35)

- Global exception handler mapeia IllegalArgumentException → HTTP 400
  [`AuditoriaExceptionHandler.java:1-50`](../../../auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaExceptionHandler.java#L1)

**Tests — Cobertura de scenarios e MDC propagation**

- Query tests com mock repositório, validam MDC e null-handling
  [`ConsultarAuditoriaPacienteTest.java:1-100`](../../../auditoria-service/src/test/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaPacienteTest.java#L1)

- Controller integration tests validam endpoints e correlationId propagation
  [`AuditoriaControllerIntegrationTest.java:1-120`](../../../auditoria-service/src/test/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaControllerIntegrationTest.java#L1)

- Exception handler tests validam 400 em null, 500 em exceção genérica
  [`AuditoriaExceptionHandlerTest.java:1-60`](../../../auditoria-service/src/test/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaExceptionHandlerTest.java#L1)
