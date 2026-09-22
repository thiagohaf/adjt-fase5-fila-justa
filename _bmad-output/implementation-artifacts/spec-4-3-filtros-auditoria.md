---
title: 'Filtros Avançados de Consulta de Auditoria (Story 4.3)'
type: 'feature'
created: '2026-09-21'
status: 'done'
review_loop_iteration: 1
baseline_commit: 'bdfbbd85a2d9732850dd7e8720837bddcb841ce2'
context: ['_bmad-output/implementation-artifacts/epic-4-context.md']
---

<!-- Target: 900–1300 tokens. -->

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.2 oferece endpoints simples para consultar histórico completo de um Paciente ou Agendamento, mas sem filtros. Um Auditor precisa investigar decisões específicas (ex.: "todas as recusas de um paciente no período X", "todas as liberações de um agendamento entre datas Y e Z") e não consegue restringir a lista — é ineficiente para auditoria de volume.

**Approach:** Estender os endpoints REST com query parameters opcionais para filtrar por data range (startDate/endDate) e tipo de decisão (tipoDecisao). Adicionar paginação com limit/offset. Mantendo read-only e ordenação cronológica (ASC), compatível com Story 4.2. Composição de filtros com AND logic: todos os critérios fornecidos devem ser satisfeitos simultaneamente.

## Boundaries & Constraints

**Always:**
- Filtros são opcionais (comportamento padrão = Story 4.2: sem filtros, retorna tudo ordenado por timestamp ASC).
- Quando nenhum filtro é fornecido, retorna lista completa (compatível com 4.2).
- Paginação: limit (padrão 50, máximo 200), offset (padrão 0).
- Response deve incluir metadados de paginação: `total`, `limit`, `offset`.
- Datas são instantes ISO-8601 (ex.: "2026-09-21T14:30:00Z"), timezone UTC.
- Se startDate > endDate, retorna HTTP 400 (erro de validação).
- `X-Correlation-Id` continua propagado em logs estruturados (NFR-2, Story 4.2).
- Read-only, nunca modificam dados.
- Autenticação: qualquer usuário autenticado (sem RBAC).
- JaCoCo ≥90% cobertura em query + repositório.

**Ask First:**
- Usar wrapper/DTO (AuditoriaFilter, AuditoriaPaginatedResponse) ou query params diretos no método de repositório?
- Suportar filtro de "status de agendamento" (ex.: PENDENTE, LIBERADO)? Nota: `DecisaoAuditoria` tem `tipoDecisao`, não status de agendamento. Se desejado, requer JOIN com agendamento-service (out-of-scope nesta story?).

**Never:**
- Não modificar dados de auditoria.
- Não paginação server-side ilimitada (sempre impor máximo de limit).
- Não fazer filtros dinâmicos sem validação (risco de injection SQL — usar parameterized queries).
- Não quebrar Story 4.2 (compatibilidade retrógrada: sem filtros = comportamento igual a 4.2).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| FILTRO_COMPLETO_PACIENTE | GET `/v1/auditoria/paciente/{id}?startDate=2026-01-01T00:00:00Z&endDate=2026-09-21T23:59:59Z&tipoDecisao=RECUSA&limit=10&offset=0` | Retorna array com até 10 recusas no range de data, + metadados {total: N, limit: 10, offset: 0} | N/A |
| FILTRO_DATA_RANGE_AGENDAMENTO | GET `/v1/auditoria/agendamento/{id}?startDate=2026-09-15T00:00:00Z&endDate=2026-09-21T23:59:59Z` | Retorna todas as decisões no range (sem restrição de tipo), ordenadas por timestamp | N/A |
| FILTRO_TIPO_DECISAO | GET `/v1/auditoria/paciente/{id}?tipoDecisao=LIBERACAO` | Retorna todas as liberações desse paciente (sem restrição de data), ordenadas por timestamp | N/A |
| SEM_FILTROS | GET `/v1/auditoria/paciente/{id}` (como Story 4.2) | Retorna lista completa ordenada por timestamp, com paginação default (limit 50, offset 0) | N/A |
| PAGINACAO_OFFSET | GET `/v1/auditoria/agendamento/{id}?limit=20&offset=40` | Retorna 20 decisões a partir do offset 40 (registros 41-60 da sequência total) | N/A |
| DATAS_INVALIDAS | GET com `startDate` > `endDate` | HTTP 400 Bad Request com mensagem "Data inicial não pode ser maior que data final" | Validação de range |
| TIPO_DECISAO_INVALIDO | GET com `tipoDecisao=INVALIDO` (não existe em enum) | HTTP 400 Bad Request com mensagem "Tipo de decisão inválido" | Validação de enum |
| LIMIT_EXCEDE_MAXIMO | GET com `limit=300` (máximo é 200) | HTTP 400 Bad Request com mensagem "Limit máximo é 200" | Validação de limit |
| RESULTADO_VAZIO | GET com filtro que não retorna nenhum registro | HTTP 200 com array vazio e metadados {total: 0, limit: N, offset: M} | N/A |

</frozen-after-approval>

## Code Map

**Existing Infrastructure (Story 4.2):**
- `auditoria-service/src/main/java/com/confirmasus/auditoria/domain/DecisaoAuditoria.java:1-160` -- entidade de domínio com campos tipoDecisao, timestamp, pacienteId, agendamentoId
- `auditoria-service/src/main/java/com/confirmasus/auditoria/domain/TipoDecisao.java:1-30` -- enum com 9 tipos (NOTIFICACAO, CONFIRMACAO, RECUSA, NAO_CONFIRMADO, LIBERACAO, SUGESTAO_GERADA, REPASSE_CONFIRMADO, SUGESTAO_RECUSADA, GENERICO)
- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/port/DecisaoAuditoriaRepositorio.java:1-50` -- porta de persistência (Story 4.1), será estendida com novos métodos de query com filtros
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/persistence/DecisaoAuditoriaJpaRepository.java:1-50` -- JPA repository, adicionar @Query com WHERE clauses dinâmicas
- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaPaciente.java:1-85` -- query use case (Story 4.2), será refatorado para aceitar filtros (startDate, endDate, tipoDecisao, limit, offset)
- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaAgendamento.java:1-85` -- query use case (Story 4.2), mesmo padrão de refatoração
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaController.java:1-92` -- REST controller, adicionar @RequestParam para novos filtros (opcionais)
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/DecisaoAuditoriaResponse.java:1-50` -- DTO (Story 4.2), reutilizado como-é
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaExceptionHandler.java:1-50` -- exception handler (Story 4.2), estender para validar startDate > endDate, tipoDecisao inválido, limit > máximo

**New Infrastructure (Story 4.3):**
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaFiltrosRequest.java` -- DTO de entrada com startDate, endDate, tipoDecisao, limit, offset (tudo opcional)
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaPaginatedResponse.java` -- DTO de saída wrapper: { items: List<DecisaoAuditoriaResponse>, total: Long, limit: Int, offset: Int }

## Tasks & Acceptance

**Execution (SQL-based Pagination — Review 1 compliance):**

1. **DecisaoAuditoriaRepositorio (interface porta)**
   - [x] Adicionar: `PaginatedResult<DecisaoAuditoria> findByPacienteIdWithFilters(Long pacienteId, Instant startDate, Instant endDate, TipoDecisao tipoDecisao, int limit, int offset);`
   - [x] Adicionar: `PaginatedResult<DecisaoAuditoria> findByAgendamentoIdWithFilters(Long agendamentoId, Instant startDate, Instant endDate, TipoDecisao tipoDecisao, int limit, int offset);`
   - [x] PaginatedResult<T> struct: `{ List<T> items, long total }`

2. **DecisaoAuditoriaJpaRepository (adapter JPA)**
   - [x] Implementar com @Query explícito (LIMIT/OFFSET, não Pageable):
     ```java
     @Query("SELECT d FROM DecisaoAuditoria d " +
            "WHERE d.pacienteId = :pacienteId " +
            "AND (:startDate IS NULL OR d.timestamp >= :startDate) " +
            "AND (:endDate IS NULL OR d.timestamp <= :endDate) " +
            "AND (:tipoDecisao IS NULL OR d.tipoDecisao = :tipoDecisao) " +
            "ORDER BY d.timestamp ASC LIMIT :limit OFFSET :offset")
     List<DecisaoAuditoria> findByPacienteWithFiltersData(
       @Param("pacienteId") Long pacienteId,
       @Param("startDate") Instant startDate,
       @Param("endDate") Instant endDate,
       @Param("tipoDecisao") TipoDecisao tipoDecisao,
       @Param("limit") int limit,
       @Param("offset") int offset);
     ```
   - [x] Implementar COUNT separado para totais:
     ```java
     @Query("SELECT COUNT(d) FROM DecisaoAuditoria d " +
            "WHERE d.pacienteId = :pacienteId " +
            "AND (:startDate IS NULL OR d.timestamp >= :startDate) " +
            "AND (:endDate IS NULL OR d.timestamp <= :endDate) " +
            "AND (:tipoDecisao IS NULL OR d.tipoDecisao = :tipoDecisao)")
     long countByPacienteWithFilters(
       @Param("pacienteId") Long pacienteId,
       @Param("startDate") Instant startDate,
       @Param("endDate") Instant endDate,
       @Param("tipoDecisao") TipoDecisao tipoDecisao);
     ```
   - [x] Same pattern for agendamento (findByAgendamentoWithFiltersData + countByAgendamentoWithFilters)

3. **ConsultarAuditoriaPaciente (use case query)**
   - [x] Refatorar: aceitar filtros opcionais (startDate, endDate, tipoDecisao, limit, offset)
   - [x] Retornar: `PaginatedResult<DecisaoAuditoriaResponse>` (com metadados total)
   - [x] Validações em controller, não aqui (delegar)

4. **ConsultarAuditoriaAgendamento (use case query)**
   - [x] Mesmo padrão que paciente

5. **AuditoriaController (@RestController)**
   - [x] Validar presença de filtros ANTES de chamar query:
     ```java
     @GetMapping("/v1/auditoria/paciente/{pacienteId}")
     public ResponseEntity<?> consultarPaciente(
       @PathVariable Long pacienteId,
       @RequestParam(required = false) Instant startDate,
       @RequestParam(required = false) Instant endDate,
       @RequestParam(required = false) TipoDecisao tipoDecisao,
       @RequestParam(defaultValue = "50") int limit,
       @RequestParam(defaultValue = "0") int offset) {
       
       boolean hasFilters = startDate != null || endDate != null || tipoDecisao != null;
       if (hasFilters) {
         // Retorna AuditoriaPaginatedResponse wrapper
         PaginatedResult<DecisaoAuditoriaResponse> result = 
           consultarAuditoriaPaciente.comFiltros(...);
         return ResponseEntity.ok(new AuditoriaPaginatedResponse(
           result.getItems(), result.getTotal(), limit, offset));
       } else {
         // Retorna List simples (Story 4.2 compat)
         List<DecisaoAuditoriaResponse> items = 
           consultarAuditoriaPaciente.semFiltros(pacienteId);
         return ResponseEntity.ok(items);
       }
     }
     ```
   - [x] Mesmo padrão para `/v1/auditoria/agendamento/{agendamentoId}`

6. **AuditoriaFiltrosRequest (DTO request — NÃO USAR, inline @RequestParam)**
   - [x] Remover ou marcar como deprecated; filtros são @RequestParam inline no controller

7. **AuditoriaPaginatedResponse (DTO response wrapper)**
   - [x] Campos: `List<DecisaoAuditoriaResponse> items`, `long total`, `int limit`, `int offset`

8. **AuditoriaExceptionHandler (@ControllerAdvice)**
   - [x] Expandir para validações centralizadas:
     ```java
     @ExceptionHandler
     public ResponseEntity<ErrorResponse> handleInvalidDateRange(InvalidDateRangeException e) {
       return ResponseEntity.badRequest().body(
         new ErrorResponse("Data inicial não pode ser maior que data final"));
     }
     
     @ExceptionHandler
     public ResponseEntity<ErrorResponse> handleInvalidLimit(InvalidLimitException e) {
       return ResponseEntity.badRequest().body(
         new ErrorResponse("Limit deve estar entre 1 e 200"));
     }
     ```

9. **Testes — 4 test classes com cobertura completa:**

   a) **ConsultarAuditoriaPacienteWithFiltersTest** (unitário, query use case)
      - [x] TODOS 9 TipoDecisao isolados (1 test per type)
      - [x] startDate = endDate (boundary instant, mesmo milissegundo)
      - [x] offset ≥ total (HTTP 200, array vazio, total correto)
      - [x] Ordenação ASC: Assert `for (i < n-1) timestamps[i] <= timestamps[i+1]`
      - [x] MDC propagation: Inject MDC supplier, verify X-Correlation-Id em log

   b) **ConsultarAuditoriaAgendamentoWithFiltersTest** (unitário, query use case)
      - [x] Mesma cobertura acima (9 tipos, boundary, paging, MDC)

   c) **AuditoriaControllerFilterIntegrationTest** (@WebMvcTest integration)
      - [x] GET `/v1/auditoria/paciente/{id}?tipoDecisao=RECUSA&startDate=...&endDate=...` → AuditoriaPaginatedResponse (wrapper)
      - [x] GET `/v1/auditoria/paciente/{id}` (sem filtros) → List<DecisaoAuditoriaResponse> (compatibilidade 4.2)
      - [x] Response type dicotômico: MockMvc.perform() → assertThat(response).contains("items") when com filtros, else sem "items" key
      - [x] HTTP 400 tests: startDate > endDate, limit > 200, offset < 0, tipoDecisao inválido

   d) **AuditoriaControllerIntegrationTest (existing, expande)**
      - [x] Adicionar: limit = 200 (máximo válido), limit = 201 (HTTP 400)
      - [x] Adicionar: 9 TipoDecisao combinations (startDate + tipo, endDate + tipo, ambos)
      - [x] Adicionar: offset behavior (offset = 0, offset = 1, offset = total, offset > total)

**Acceptance Criteria (Review 1 compliance):**
- [x] AC1: Given histórico com 5 decisões (2 RECUSA ago-set, 3 CONFIRMACAO set-out), when GET `/v1/auditoria/paciente/{id}?tipoDecisao=RECUSA&startDate=2026-08-01T00:00:00Z&endDate=2026-09-30T23:59:59Z`, then retorna `AuditoriaPaginatedResponse` com `items: [2 RECUSA], total: 2, limit: 50, offset: 0` (NÃO retorna CONFIRMACAO)
- [x] AC2: Given 10 decisões agendamento, when GET `/v1/auditoria/agendamento/{id}?limit=5&offset=0`, then retorna wrapper com `items: [5], total: 10, limit: 5, offset: 0`
- [x] AC3: Given total=10, when GET com `offset=10`, then retorna `items: [], total: 10, limit: 50, offset: 10` (HTTP 200, não 404)
- [x] AC4: Given startDate=2026-09-25T00:00:00Z, endDate=2026-09-21T23:59:59Z, when GET, then HTTP 400 "Data inicial não pode ser maior que data final"
- [x] AC5: Given tipoDecisao=TIPO_INEXISTENTE, when GET, then HTTP 400 "Tipo de decisão não reconhecido"
- [x] AC6: Given GET sem filtros (Story 4.2 compat), when `/v1/auditoria/paciente/{id}`, then retorna `List<DecisaoAuditoriaResponse>` (NÃO wrapper), com defaultValue limit=50, offset=0 (internamente)
- [x] AC7: Given 100+ registros, when GET com limit=200 (máximo), then retorna array de até 200 items (SQL LIMIT 200)
- [x] AC8: Given 9 TipoDecisao, when test com cada tipo isolado, then retorna SOMENTE aquele tipo (AND logic, não OR)
- [x] AC9: Given registros com timestamp=2026-09-21T14:00:00Z e 2026-09-21T14:00:00Z (mesmo instant), when GET `?startDate=2026-09-21T14:00:00Z&endDate=2026-09-21T14:00:00Z`, then retorna ambos (boundary inclusive)
- [x] AC10: Cobertura JaCoCo ≥90% em `application/query/ConsultarAuditoria*.java` + `infrastructure/persistence/DecisaoAuditoria*.java`

**Acceptance Criteria:**
- Given histórico com 5 decisões de um paciente (2 RECUSA entre ago-26 e set-26, 3 CONFIRMACAO entre set-26 e out-26), when GET `/v1/auditoria/paciente/{id}?tipoDecisao=RECUSA&startDate=2026-08-01T00:00:00Z&endDate=2026-09-30T23:59:59Z`, then retorna array com 2 RECUSA apenas (sem CONFIRMACAO), com metadados { total: 2, limit: 50, offset: 0 }
- Given agendamento com 10 decisões, when GET `/v1/auditoria/agendamento/{id}?limit=5&offset=0`, then retorna 5 decisões (primeira página)
- Given 10 decisões total, when GET com `offset=10`, then retorna array vazio com metadados { total: 10, limit: 50, offset: 10 }
- Given startDate=2026-09-25T00:00:00Z e endDate=2026-09-21T23:59:59Z, when GET `/v1/auditoria/paciente/{id}`, then HTTP 400 com mensagem validando range de datas
- Given tipoDecisao=TIPO_INEXISTENTE, when GET `/v1/auditoria/paciente/{id}`, then HTTP 400 com mensagem de enum inválido
- Given GET sem filtros (compatibilidade Story 4.2), when `/v1/auditoria/paciente/{id}`, then retorna lista completa (como 4.2), com limit padrão 50, offset 0

## Design Notes

### Paginação em SQL (LIMIT/OFFSET), não em memória
JPA repository implementa paginação via `@Query` com `LIMIT :limit OFFSET :offset` explícito em SQL, não via:
- ~~`stream.skip().limit()`~~ (carrega TODO dataset em RAM)
- ~~Spring Data `Pageable`~~ (requer refactor mais amplo)

Isso garante scalabilidade: datasets com 1M+ registros executam O(1) em RAM (database cursor), não O(n).

**SQL pattern:**
```sql
SELECT * FROM decisao_auditoria 
WHERE paciente_id = ? 
  AND (? IS NULL OR timestamp >= ?) 
  AND (? IS NULL OR timestamp <= ?) 
  AND (? IS NULL OR tipo_decisao = ?) 
ORDER BY timestamp ASC 
LIMIT ? OFFSET ?
```

**COUNT separado:** Para metadados `total`, repositório executa `SELECT COUNT(*)` com mesmos WHERE clauses (ANTES de LIMIT/OFFSET):
```sql
SELECT COUNT(*) FROM decisao_auditoria 
WHERE paciente_id = ? 
  AND (? IS NULL OR timestamp >= ?) 
  AND (? IS NULL OR timestamp <= ?) 
  AND (? IS NULL OR tipo_decisao = ?)
```
Garante que `total` reflete registros que satisfazem filtros, não total da tabela.

### Response Type Dicotômico (Controller-Level Decision)
Este é o achado crítico de Review 1. A resposta muda estruturalmente conforme presença de filtros:

| Caso | Query Params | Endpoint Response | Content-Type | Cliente deve esperar |
|------|--------------|-------------------|--------------|----------------------|
| Sem filtros (4.2 compat) | `GET /v1/auditoria/paciente/123` | `List<DecisaoAuditoriaResponse>` | `application/json` | `[ { id: 1, ... }, { id: 2, ... } ]` |
| Com filtros (4.3) | `GET /v1/auditoria/paciente/123?tipoDecisao=RECUSA&limit=10` | `AuditoriaPaginatedResponse` | `application/json` | `{ items: [ { id: 1, ... } ], total: 5, limit: 10, offset: 0 }` |

**Controller Logic (pseudocódigo):**
```java
boolean hasFilters = startDate != null || endDate != null || tipoDecisao != null;
if (hasFilters) {
  // Story 4.3 path: retorna wrapper paginado
  PaginatedResult<DecisaoAuditoriaResponse> result = 
    consultarAuditoriaPaciente.comFiltros(pacienteId, startDate, endDate, tipoDecisao, limit, offset);
  return ResponseEntity.ok(
    new AuditoriaPaginatedResponse(result.items, result.total, limit, offset));
} else {
  // Story 4.2 path: retorna lista simples
  List<DecisaoAuditoriaResponse> items = 
    consultarAuditoriaPaciente.semFiltros(pacienteId);
  return ResponseEntity.ok(items);
}
```

**Breaking change risk:** Cliente REST DEVE lidar com dois tipos de resposta diferentes. Mitigação:
1. Documentar em OpenAPI/Swagger: response dicotômico conforme query params
2. Adicionar atributo extra `_type: "paginated" | "list"` na resposta JSON para disambiguation programática (opcional)
3. Testes devem validar ambos os caminhos (com e sem filtros)

### Validação Centralizada
Filtros são validados no controller (datas, enum, limits), retornando HTTP 400 antes de chegar às query use cases:
- `startDate > endDate?` → throw InvalidDateRangeException → HTTP 400
- `limit < 1 || limit > 200?` → throw InvalidLimitException → HTTP 400
- `offset < 0?` → throw InvalidOffsetException → HTTP 400
- `tipoDecisao not in enum?` → throw InvalidEnumException → HTTP 400

Exception handler centralizado em `AuditoriaExceptionHandler` mapeia cada exceção para HTTP 400 com mensagem JSON clara.

### Compatibilidade com Story 4.2
Story 4.2 e 4.3 compartilham:
- DTOs: `DecisaoAuditoriaResponse` (reutilizado como-é)
- Exception handlers: estendidos, não refatorados
- Repositório interface: estendido com novos métodos, métodos antigos preservados

Sem filtros, Story 4.3 comporta-se identicamente a 4.2 (via `if (!hasFilters)` path).

## Spec Change Log (Review Loop 1 — 2026-09-21)

**CRITICAL CHANGES from v0:**

### 1. Paginação em SQL (LIMIT/OFFSET explícito)
- **v0 erro:** `stream.skip().limit()` no JpaRepository carrega TODO dataset em memória (O(n) RAM)
- **v1 fix:** JPA @Query com `LIMIT :limit OFFSET :offset` explícito via SQL
  ```sql
  SELECT * FROM decisao_auditoria 
  WHERE (paciente_id = :pacienteId OR agendamento_id = :agendamentoId) 
    AND (:startDate IS NULL OR timestamp >= :startDate) 
    AND (:endDate IS NULL OR timestamp <= :endDate) 
    AND (:tipoDecisao IS NULL OR tipo_decisao = :tipoDecisao) 
  ORDER BY timestamp ASC 
  LIMIT :limit OFFSET :offset
  ```
- **Escalabilidade:** 1M registros = O(1) RAM (database cursor), não O(n)
- **Risco:** Campos `startDate`/`endDate`/`tipoDecisao` nullable em @Query; usar IS NULL check

### 2. Response Type Dicotômico (Controller-level validation)
- **v0 erro:** Sempre retorna wrapper (AuditoriaPaginatedResponse), mesmo sem filtros; quebra Story 4.2
- **v1 fix:** Controller valida presença de filtros ANTES de chamar query use case
  ```java
  boolean hasFilters = startDate != null || endDate != null || tipoDecisao != null;
  if (hasFilters) {
    // Chama query com filtros, retorna AuditoriaPaginatedResponse
    PaginatedResult<DecisaoAuditoriaResponse> result = 
      consultarAuditoriaPaciente.comFiltros(pacienteId, startDate, endDate, tipoDecisao, limit, offset);
    return ResponseEntity.ok(new AuditoriaPaginatedResponse(...));
  } else {
    // Chama query sem filtros (Story 4.2), retorna List simples
    List<DecisaoAuditoriaResponse> lista = 
      consultarAuditoriaPaciente.semFiltros(pacienteId);
    return ResponseEntity.ok(lista);
  }
  ```
- **Compatibilidade:** GET `/v1/auditoria/paciente/{id}` (sem query params) retorna List, idêntico a 4.2
- **Breaking risk:** Nullable tipos de response; client DEVE diferenciar por Content-Type ou documentação clara

### 3. Validação de Filtros em Controller
- **v0 erro:** Validação espalhada (handler, query, repositório); stack traces confusos
- **v1 fix:** Tudo centralizado em controller + @ControllerAdvice exception handler
  - `startDate > endDate?` → HTTP 400 "Data inicial não pode ser maior que data final"
  - `limit < 1 || limit > 200?` → HTTP 400 "Limit deve estar entre 1 e 200"
  - `offset < 0?` → HTTP 400 "Offset não pode ser negativo"
  - `tipoDecisao inválido?` → HTTP 400 "Tipo de decisão não reconhecido"
- **Stack:** Controller → ParseException/ValidationException → AuditoriaExceptionHandler → HTTP 400 + JSON mensagem

### 4. Cobertura de Testes — Requisitos Revistos
- **v0 insuficiente:** 4 tipos de decisão, sem boundary cases
- **v1 obrigatório:**
  - **Todos 9 TipoDecisao:** NOTIFICACAO, CONFIRMACAO, RECUSA, NAO_CONFIRMADO, LIBERACAO, SUGESTAO_GERADA, REPASSE_CONFIRMADO, SUGESTAO_RECUSADA, GENERICO
  - **Boundary instant:** `startDate = endDate` (mesmo milissegundo)
  - **Paging beyond:** `offset ≥ total` (HTTP 200, array vazio, total correto)
  - **Limite máximo:** `limit = 200`, `limit = 201` (rejeita)
  - **Ordenação ASC verificada:** Assert `result.get(i).timestamp ≤ result.get(i+1).timestamp`
  - **MDC propagation:** Testes verificam que `X-Correlation-Id` aparece em logs estruturados (SLF4J) mesmo com filtros
  - **Response type dicotômico:** Teste que sem filtros retorna `List<>`, com filtros retorna `AuditoriaPaginatedResponse`

**KEEP from v0:** 
- Estrutura de DTOs (reutilizados)
- Exception handlers (refatorados para validação centralizada)
- Repositório interface (refatorado para SQL-based, não removido)

**Riscos residuais:**
- `IS NULL` em @Query pode ter overhead em índices; considerar COALESCE se problema
- Timezone: assumir UTC; testes devem usar ZoneId.of("UTC") para Instant parsing
- Cliente REST deve tratar dois tipos de response; documentar com OpenAPI/Swagger

**Review 1 rationale:** v0 tinha design correto (SQL, dicotômico) em Design Notes mas implementação v0 não refletia; Spec Change Log agora FORÇA implementação exata via código exemplar acima.

## Verification

**Automated Tests (via Maven):**
```bash
cd auditoria-service
mvn clean test                      # Rodar TODOS os testes + filtros (4 test classes)
mvn jacoco:report                   # Gerar report JaCoCo (target/site/jacoco/index.html)
mvn -Dgroups=integration test       # (Opcional) Apenas testes de integração
```

**Manual HTTP Checks (curl ou Postman):**

1. **Story 4.2 compat — sem filtros:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123" \
     -H "X-Correlation-Id: abc-123"
   # Expected: HTTP 200 + List<DecisaoAuditoriaResponse> (array [] direto, NOT wrapped)
   # E.g.: [ { "id": 1, "tipoDecisao": "RECUSA", "timestamp": "2026-09-21T..." }, ... ]
   ```

2. **Story 4.3 — com filtros (retorna wrapper):**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?tipoDecisao=RECUSA&startDate=2026-01-01T00:00:00Z" \
     -H "X-Correlation-Id: xyz-789"
   # Expected: HTTP 200 + AuditoriaPaginatedResponse (wrapped)
   # E.g.: { "items": [ { "id": 1, ... } ], "total": 5, "limit": 50, "offset": 0 }
   ```

3. **Validação: startDate > endDate:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?startDate=2026-09-25T00:00:00Z&endDate=2026-09-21T00:00:00Z"
   # Expected: HTTP 400 + { "error": "Data inicial não pode ser maior que data final" }
   ```

4. **Validação: limit > 200:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?limit=201"
   # Expected: HTTP 400 + { "error": "Limit deve estar entre 1 e 200" }
   ```

5. **Validação: tipoDecisao inválido:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?tipoDecisao=TIPO_INEXISTENTE"
   # Expected: HTTP 400 + { "error": "Tipo de decisão não reconhecido" }
   ```

6. **Paginação (offset):**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?limit=10&offset=10"
   # Expected: HTTP 200 + wrapper com items [10-20] (pula primeiros 10), total=N
   ```

7. **Paginação beyond dataset:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?limit=10&offset=1000" \
     -H "Accept: application/json"
   # Expected: HTTP 200 + { "items": [], "total": N, "limit": 10, "offset": 1000 }
   ```

8. **Filtro AND (data + tipo):**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?startDate=2026-09-01T00:00:00Z&endDate=2026-09-30T23:59:59Z&tipoDecisao=LIBERACAO"
   # Expected: HTTP 200 + wrapper com SOMENTE LIBERACAO registros no range (não outros tipos)
   ```

9. **MDC Propagation (validar log estruturado):**
   - Fazer request com `X-Correlation-Id: test-123`
   - Verificar que logs contêm `correlation_id=test-123` (SLF4J MDC)
   - Log pattern: `timestamp [correlation_id=test-123] function [...] message`

10. **Ordenação ASC (verificar SQL ORDER BY):**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?limit=100" | jq '.items[].timestamp'
   # Expected: timestamps em ordem crescente (2026-09-01 < 2026-09-02 < ...)
   ```

**Code Review Checks:**
- [x] @Query usam `LIMIT :limit OFFSET :offset` explícito (NÃO `stream.skip().limit()`)
- [x] WHERE clauses usam `IS NULL` para nullable filters (evita SQL injection)
- [x] COUNT query tem exatamente mesmos WHERE clauses que data query
- [x] Controller valida `hasFilters` com `||` (OR) para presença de QUALQUER filtro
- [x] Controller retorna `List<>` direto se sem filtros, wrapper se com filtros
- [x] AuditoriaExceptionHandler trata InvalidDateRangeException, InvalidLimitException, InvalidEnumException
- [x] Testes implementam todos os 10 assertions de AC (AC1-AC10)

**JaCoCo Compliance:**
- Linhas em coverage: `application/query/ConsultarAuditoria*.java` + `infrastructure/persistence/DecisaoAuditoria*.java`
- Target: ≥90% (não modificar arquivo `.jacoco` config)
- Relatório: `mvn jacoco:report` gera HTML em `target/site/jacoco/` (abrir em browser)

## Implementation Risk Register (Review 1)

| Risk | Severity | Probability | Mitigation |
|------|----------|-------------|-----------|
| **Response type dicotomia confunde cliente REST** | HIGH | HIGH | Documentar no OpenAPI/Swagger que response é List[] se sem filtros, wrapper{} se com filtros. Adicionar exemplo de código em README. Teste integração valida ambos os paths. |
| **SQL `IS NULL` overhead em índices** | MEDIUM | MEDIUM | Se problema em perf (> 200ms), considerar refactor para COALESCE ou índices compostos. Benchmark antes de PR. |
| **Timezone misalignment (UTC vs local)** | MEDIUM | MEDIUM | TODOS os testes devem usar `ZoneId.of("UTC")` ao parsear Instant. Docstring em AuditoriaController: "Datas são ISO-8601 UTC". |
| **startDate=endDate boundary instant** | LOW | LOW | Query usa `>=` e `<=` (inclusive on both sides), testes validam ambos os registros retornados. |
| **offset ≥ total não quebra** | LOW | LOW | Query retorna array vazio, metadados corretos (total=N, offset=M > N). HTTP 200 OK, não 404. Teste valida. |
| **@Query @Param nullable não funciona em todos DBs** | MEDIUM | LOW | Validado em H2 (testes unitários) e PostgreSQL (CI). Se usar outro DB, verificar docs de @Query com IS NULL. |
| **MDC propagation em threads de pool** | MEDIUM | MEDIUM | Usar `MDCTaskDecoratorFactory` em async context. Se sync-only, risco nulo. Verificar em logs com X-Correlation-Id. |
| **Exception message usability** | LOW | HIGH | Mensagens de erro em PT-BR, claras (ex.: "Limit deve estar entre 1 e 200", não "Invalid param"). Test validar status + mensagem exata. |

## Summary of Changes (v0 → v1)

| Aspecto | v0 | v1 | Impacto |
|---------|----|----|---------|
| **Paginação** | stream.skip().limit() | SQL LIMIT/OFFSET | Escalabilidade: O(n) → O(1) RAM |
| **Response type** | Sempre wrapper | Dicotômico (List vs wrapper) | Breaking: cliente deve lidar com 2 tipos |
| **Validação** | Espalhada (handler, query, repo) | Centralizada (controller) | Manutenibilidade: stack trace claro |
| **Teste cobertura** | 4 TipoDecisao | 9 TipoDecisao + boundary + MDC | Confiabilidade: 90%+ JaCoCo |
| **Ordering** | Implícito | Explícito ORDER BY ASC | Determinístico: testes verificam |
| **COUNT** | Não mencionado | Separado, mesmo WHERE | Metadados corretos: total reflete filtros |

**Review 1 rationale:** v0 design era correto mas implementação esperada não refletia especificação (stream vs SQL). v1 FORÇA implementação SQL explícita via código exemplar em Tasks section, testes obrigatórios de boundary/9-tipos/MDC, e Breaking change risk documentado com mitigation (dicotomia de response).
