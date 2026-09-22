---
title: 'Filtro de Status de Agendamento em Consulta de Auditoria (Story 4.4a)'
type: 'feature'
created: '2026-09-21'
status: 'in-progress'
review_loop_iteration: 0
baseline_commit: '73e3dce544688ee86c1e0bf7a33284d928b62b9a'
context: ['_bmad-output/implementation-artifacts/epic-4-context.md', '_bmad-output/implementation-artifacts/spec-4-3-filtros-auditoria.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.3 oferece filtros para tipoDecisao e range de datas (startDate/endDate), mas um Auditor investigando uma reclamação de paciente precisa também restringir por status do Agendamento subjacente (ex.: "todas as recusas de agendamentos que foram CONFIRMADOS", "todas as decisões de agendamentos que foram LIBERADOS"). Sem esse filtro, a lista retorna muitos registros irrelevantes de agendamentos com status diferente.

**Approach:** Estender os endpoints REST (Story 4.3) com novo query parameter opcional: `statusAgendamento` (enum resolvido via JOIN com agendamento-service). Mantendo paginação SQL, validação centralizada em controller, compatibilidade total com Story 4.3. Filtro composto com AND logic aos filtros existentes.

## Boundaries & Constraints

**Always:**
- Novo filtro é opcional (Story 4.3 comportamento padrão sem mudança).
- Paginação continua (limit/offset) com metadados `total`, `limit`, `offset`.
- Filtro `statusAgendamento` é composto com AND logic aos filtros existentes (tipoDecisao, startDate, endDate).
- JOIN com agendamento-service é feito via SQL (nativa query ou HQL com JOIN explícito), não stream-based.
- Compatibilidade total com Story 4.3: sem novo filtro, retorna exatamente o mesmo que 4.3.
- Response dicotômico de Story 4.3 é preservado (List se sem filtros, wrapper se com filtros).
- Autenticação: qualquer usuário autenticado (sem RBAC).
- JaCoCo ≥90% cobertura em query + repositório.

**Ask First:**
- Quais valores exatos de `StatusAgendamento` enum? (ex.: PENDENTE, CONFIRMADO, RECUSADO, NAO_CONFIRMADO, LIBERADO, etc.?)
- Contrato com agendamento-service: qual é o endpoint/gRPC para resolver Agendamento.id → statusAgendamento?
- Tolerância a JOIN incompleto: se Agendamento foi deletado após registrar decisão, filtrar (retornar vazio) ou incluir com status NULL?

**Never:**
- Não quebrar Story 4.3 (compatibilidade retrógrada).
- Não fazer queries de N+1 (JOIN deve estar em SQL, não em loop).
- Não endpoints novos; estender `GET /v1/auditoria/paciente/{id}` e `GET /v1/auditoria/agendamento/{id}` existentes.
- Não modificar dados.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| FILTRO_STATUS | GET `/v1/auditoria/paciente/{id}?statusAgendamento=CONFIRMADO` | Retorna array com decisões registradas para agendamentos com status CONFIRMADO, ordenadas por timestamp | N/A |
| FILTRO_COMBINADO | GET `/v1/auditoria/paciente/{id}?tipoDecisao=RECUSA&statusAgendamento=CONFIRMADO&startDate=2026-09-01T00:00:00Z` | Retorna SOMENTE recusas de agendamentos confirmados no range (AND logic) | N/A |
| PAGINACAO_COM_FILTRO | GET `/v1/auditoria/paciente/{id}?statusAgendamento=RECUSADO&limit=10&offset=20` | Retorna 10 decisões a partir do offset 20, com metadados total refletindo apenas registros com statusAgendamento=RECUSADO | N/A |
| STATUS_INVALIDO | GET com `statusAgendamento=INVALIDO` | HTTP 400 Bad Request com mensagem "Status de agendamento inválido" | Validação de enum |
| AGENDAMENTO_NAO_EXISTE | GET com `statusAgendamento=X`, mas referência a Agendamento deletado | HTTP 200 com array vazio; log estruturado registra JOIN incompleto | N/A |
| SEM_FILTRO | GET `/v1/auditoria/paciente/{id}` (Story 4.3 compat) | Retorna List direto (sem wrapper), exatamente como 4.3 | N/A |

</frozen-after-approval>

## Code Map

**Existing (Story 4.3):**
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/persistence/DecisaoAuditoriaJpaRepository.java:1-200` — @Query com LIMIT/OFFSET, será estendido com JOIN a agendamento-service
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaController.java:1-150` — controller com @RequestParam, será estendido para statusAgendamento
- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaPaciente.java:1-100` — use case query, será refatorado para aceitar novo filtro
- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaAgendamento.java:1-100` — idem
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaExceptionHandler.java:1-100` — exception handler, será estendido

**New (Story 4.4a):**
- `auditoria-service/src/main/java/com/confirmasus/auditoria/domain/StatusAgendamento.java` — enum com valores confirmados via Ask First (PENDENTE, CONFIRMADO, RECUSADO, NAO_CONFIRMADO, LIBERADO, etc.)
- Integração com agendamento-service (detalhes conforme contrato resolvido no Ask First)

## Tasks & Acceptance

**Execution:**

1. **StatusAgendamento enum**
   - [ ] Criar enum com valores exatos (Ask First resolvido)
   - [ ] Documentar fonte (agendamento-service field name)

2. **DecisaoAuditoriaJpaRepository — estender @Query**
   - [ ] Adicionar JOIN a agendamento-service em SQL
   - [ ] Novas @Query com WHERE clause para statusAgendamento (IS NULL safe)
   - [ ] COUNT separado para metadados (mesmas WHERE clauses)

3. **ConsultarAuditoriaPaciente/ConsultarAuditoriaAgendamento — refatorar**
   - [ ] Aceitar statusAgendamento como parâmetro opcional
   - [ ] Passar para repositório

4. **AuditoriaController — estender @RequestParam**
   - [ ] Adicionar @RequestParam statusAgendamento (nullable)
   - [ ] Validar enum (status inválido → HTTP 400)
   - [ ] Atualizar `hasFilters` para incluir novo parâmetro

5. **AuditoriaExceptionHandler — estender**
   - [ ] Adicionar handler para InvalidStatusAgendamentoException

6. **Testes**
   - [ ] ConsultarAuditoriaPacienteWithStatusAgendamentoFilterTest — cada valor de enum isolado (parametrizado)
   - [ ] Combinações: statusAgendamento + tipoDecisao (AND); statusAgendamento + startDate/endDate (AND)
   - [ ] JOIN incompleto (Agendamento não existe) — retorna array vazio
   - [ ] Story 4.3 compat (sem novo filtro = sem wrapper)

**Acceptance Criteria:**
- Given histórico com 5 decisões de paciente (2 RECUSA, 3 CONFIRMACAO), quando GET `/v1/auditoria/paciente/{id}?statusAgendamento=CONFIRMADO`, then retorna SOMENTE decisões registradas para agendamentos com status CONFIRMADO (quantidade varia conforme data histórica)
- Given 10 decisões agendamento, quando GET `/v1/auditoria/agendamento/{id}?statusAgendamento=LIBERADO&limit=5&offset=0`, then retorna até 5 registros com statusAgendamento=LIBERADO, metadados { total: N, limit: 5, offset: 0 }
- Given filtros: tipoDecisao=RECUSA, statusAgendamento=CONFIRMADO, startDate=2026-09-01T00:00:00Z, quando GET `/v1/auditoria/paciente/{id}`, then retorna SOMENTE registros que satisfazem TODOS (AND logic)
- Given statusAgendamento=STATUS_INEXISTENTE, quando GET, then HTTP 400 "Status de agendamento não reconhecido"
- Given Agendamento deletado (referência órfã), quando GET com statusAgendamento filter, then HTTP 200 com array vazio (não falha)
- Given GET sem novo filtro (Story 4.3 compat), quando `/v1/auditoria/paciente/{id}?tipoDecisao=RECUSA`, then retorna `List<DecisaoAuditoriaResponse>` (NÃO wrapper)
- Cobertura JaCoCo ≥90% (query + repositório estendidos)

## Design Notes

### JOIN vs. Denormalização

Duas abordagens possíveis, conforme contrato com agendamento-service:

1. **JOIN Síncrono (SQL native @Query):** Simples, mas agendamento-service deve estar disponível durante query. Se Agendamento deletado, JOIN retorna NULL (filtro exclui a linha).
2. **Denormalização (snapshot):** Criar tabela `decisao_auditoria_agendamento_snapshot` (statusAgendamento, tipoPaciente) populada ao consumir evento. Resiliente, mas requer lógica no consumer SQS.

**Recomendação:** Começar com JOIN síncrono (mais simples). Evoluir para denormalização se latência/disponibilidade for problema.

## Verification

**Commands:**
- `cd auditoria-service && mvn clean test` -- Rodar testes com novo filtro
- `mvn jacoco:report` -- Gerar report

**Manual HTTP (curl):**

1. **Filtro status:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?statusAgendamento=CONFIRMADO"
   # Expected: HTTP 200 + wrapper com items filtrados
   ```

2. **Combinação AND:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?tipoDecisao=RECUSA&statusAgendamento=CONFIRMADO"
   # Expected: HTTP 200 + wrapper com recusas de agendamentos confirmados
   ```

3. **Validação enum:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?statusAgendamento=INVALIDO"
   # Expected: HTTP 400 + { "error": "Status de agendamento não reconhecido" }
   ```

4. **Compat 4.3 (sem novo filtro):**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123"
   # Expected: HTTP 200 + List<DecisaoAuditoriaResponse> (NÃO wrapper)
   ```
