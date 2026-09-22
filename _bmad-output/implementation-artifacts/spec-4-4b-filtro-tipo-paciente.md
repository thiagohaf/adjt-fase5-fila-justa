---
title: 'Filtro de Tipo de Paciente em Consulta de Auditoria (Story 4.4b)'
type: 'feature'
created: '2026-09-21'
status: 'done'
review_loop_iteration: 0
baseline_commit: '1ec13a4'
context: ['_bmad-output/implementation-artifacts/epic-4-context.md', '_bmad-output/implementation-artifacts/spec-4-4-filtro-status-agendamento.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.3 oferece filtros para tipoDecisao e range de datas, Story 4.4a adiciona statusAgendamento, mas um Auditor investigando uma reclamação também precisa filtrar por tipo do Paciente (ex.: "todas as recusas de pacientes PRIORITARIOS", "todas as decisões de pacientes REGULARES"). Sem esse filtro, auditores não podem restringir análises por categoria de paciente (que afeta prioridade de atendimento).

**Approach:** Estender os endpoints REST (Story 4.3/4.4a) com novo query parameter opcional: `tipoPaciente` (enum resolvido via JOIN com paciente-service, ou via denormalização conforme contrato). Mantendo paginação SQL, validação centralizada em controller, compatibilidade total com Stories 4.3 e 4.4a. Filtro composto com AND logic aos filtros existentes.

## Boundaries & Constraints

**Always:**
- Novo filtro é opcional (Story 4.3/4.4a comportamento padrão sem mudança)
- Paginação continua (limit/offset) com metadados `total`, `limit`, `offset`
- Filtro `tipoPaciente` é composto com AND logic aos filtros existentes (tipoDecisao, startDate, endDate, statusAgendamento)
- Compatibilidade total com Stories 4.3 e 4.4a: sem novo filtro, retorna exatamente o mesmo resultado que 4.4a
- Response dicotômico mantido (List se sem filtros, wrapper se com filtros)
- Autenticação: qualquer usuário autenticado (sem RBAC)
- JaCoCo ≥90% cobertura em query + repositório

**Ask First:**
- Como é exposto `tipoPaciente` em paciente-service? Via endpoint GET ou event payload?
- Se via denormalização: criaremos tabela `decisao_auditoria_paciente_snapshot` (tipoPaciente) populada no consumer SQS ao registrar decisão, ou usamos JOIN+cache?
- Quais valores exatos de enum `TipoPaciente`? (ex.: PRIORITARIO, REGULAR, VIP, etc.?)

**Never:**
- Não quebrar Stories 4.3 ou 4.4a (compatibilidade retrógrada)
- Não fazer queries de N+1 (JOIN deve estar em SQL, não em loop)
- Não endpoints novos; estender `GET /v1/auditoria/paciente/{id}` e `GET /v1/auditoria/agendamento/{id}` existentes
- Não modificar dados

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| FILTRO_TIPO | GET `/v1/auditoria/paciente/{id}?tipoPaciente=PRIORITARIO` | Retorna array com decisões de pacientes PRIORITARIOS, ordenadas por timestamp | N/A |
| FILTRO_COMBINADO | GET `/v1/auditoria/paciente/{id}?tipoDecisao=RECUSA&tipoPaciente=PRIORITARIO&statusAgendamento=CONFIRMADO&startDate=2026-09-01T00:00:00Z` | Retorna SOMENTE recusas de pacientes PRIORITARIOS com agendamentos confirmados no range (AND logic) | N/A |
| PAGINACAO_COM_FILTRO | GET `/v1/auditoria/paciente/{id}?tipoPaciente=REGULAR&limit=10&offset=20` | Retorna 10 decisões a partir do offset 20, com metadados total refletindo apenas registros com tipoPaciente=REGULAR | N/A |
| TIPO_INVALIDO | GET com `tipoPaciente=INVALIDO` | HTTP 400 Bad Request com mensagem "Tipo de paciente inválido" | Validação de enum |
| PACIENTE_SEM_TIPO | GET com `tipoPaciente=X`, mas paciente deletado ou sem tipo definido | HTTP 200 com array vazio; log estruturado registra JOIN incompleto | N/A |
| SEM_FILTRO | GET `/v1/auditoria/paciente/{id}` (Story 4.3/4.4a compat) | Retorna List direto (sem wrapper), exatamente como 4.3 | N/A |

</frozen-after-approval>

## Code Map

**Arquivos existentes de Story 4.4a (reusar padrão):**
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/persistence/DecisaoAuditoriaJpaRepository.java` — Adicionar 4 novos @Query com JOIN a paciente-service (similar a statusAgendamento em 4.4a)
- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaPaciente.java` — Refatorar para aceitar tipoPaciente
- `auditoria-service/src/main/java/com/confirmasus/auditoria/application/query/ConsultarAuditoriaAgendamento.java` — Idem
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaController.java` — Adicionar @RequestParam tipoPaciente
- `auditoria-service/src/main/java/com/confirmasus/auditoria/infrastructure/web/AuditoriaFiltrosRequest.java` — Adicionar campo tipoPaciente

**Arquivos novos (possível):**
- `auditoria-service/src/main/java/com/confirmasus/auditoria/domain/TipoPaciente.java` — Enum com valores (PRIORITARIO, REGULAR, VIP, etc.)
- Testes: `*WithTipoPacienteFilterTest.java` (parametrizado, similar a 4.4a)

## Tasks & Acceptance

**Execution:**

1. **TipoPaciente enum** (Ask First: valores exatos?)
   - [ ] `domain/TipoPaciente.java` -- Criar enum com valores confirmados
   - [ ] Documentar fonte (paciente-service field name)

2. **DecisaoAuditoriaJpaRepository — estender @Query**
   - [ ] Adicionar JOIN a paciente-service em SQL (via endpoint ou event payload)
   - [ ] Novas @Query com WHERE clause para tipoPaciente (IS NULL safe)
   - [ ] COUNT separado para metadados (mesmas WHERE clauses)

3. **ConsultarAuditoriaPaciente/ConsultarAuditoriaAgendamento — refatorar**
   - [ ] Aceitar tipoPaciente como parâmetro opcional
   - [ ] Passar para repositório

4. **AuditoriaController — estender @RequestParam**
   - [ ] Adicionar @RequestParam tipoPaciente (nullable)
   - [ ] Validar enum (tipo inválido → HTTP 400)
   - [ ] Atualizar `hasFilters` para incluir novo parâmetro

5. **AuditoriaFiltrosRequest — estender**
   - [ ] Adicionar field `tipoPaciente` (nullable)

6. **Testes**
   - [ ] ConsultarAuditoriaPacienteWithTipoPacienteFilterTest — cada valor de enum isolado (parametrizado)
   - [ ] Combinações: tipoPaciente + tipoDecisao (AND); tipoPaciente + statusAgendamento (AND); 3-way (tipoPaciente + tipoDecisao + statusAgendamento)
   - [ ] JOIN incompleto (Paciente não existe ou sem tipo) — retorna array vazio
   - [ ] Story 4.3/4.4a compat (sem novo filtro = sem wrapper)

**Acceptance Criteria:**
- Given histórico com 10 decisões de paciente (5 PRIORITARIOS, 5 REGULARES), quando GET `/v1/auditoria/paciente/{id}?tipoPaciente=PRIORITARIO`, then retorna SOMENTE decisões de pacientes PRIORITARIOS
- Given 20 decisões agendamento, quando GET `/v1/auditoria/agendamento/{id}?tipoPaciente=PRIORITARIO&limit=5&offset=0`, then retorna até 5 registros com tipoPaciente=PRIORITARIO, metadados { total: N, limit: 5, offset: 0 }
- Given filtros: tipoDecisao=RECUSA, tipoPaciente=PRIORITARIO, statusAgendamento=CONFIRMADO, quando GET `/v1/auditoria/paciente/{id}`, then retorna SOMENTE registros que satisfazem TODOS (AND logic)
- Given tipoPaciente=TIPO_INEXISTENTE, quando GET, then HTTP 400 "Tipo de paciente não reconhecido"
- Given Paciente sem tipoPaciente definido (referência órfã), quando GET com tipoPaciente filter, then HTTP 200 com array vazio (não falha)
- Given GET sem novo filtro (Story 4.3/4.4a compat), quando `/v1/auditoria/paciente/{id}?tipoDecisao=RECUSA&statusAgendamento=CONFIRMADO`, then retorna `List<DecisaoAuditoriaResponse>` (NÃO wrapper)
- Cobertura JaCoCo ≥90% (query + repositório estendidos)

## Design Notes

### Abordagens Possíveis

1. **JOIN Síncrono (SQL native @Query):** Similar a Story 4.4a (statusAgendamento). Simples, mas paciente-service deve estar disponível durante query.
   
2. **Denormalização (snapshot):** Criar tabela `decisao_auditoria_paciente_snapshot` (tipoPaciente) populada ao consumir evento no `DecisaoSqsConsumerJob`. Resiliente, mas requer lógica no consumer SQS.

**Recomendação:** Começar com JOIN síncrono (mantém padrão de 4.4a). Evoluir para denormalização se latência/disponibilidade for problema.

### Integração com Paciente-Service

Conforme Ask First: precisamos de clareza em como `tipoPaciente` é exposto. Se for um campo no Agendamento (como statusAgendamento), padrão é igual ao 4.4a. Se for isolado em paciente-service, precisaremos de 2 JOINs (agendamento → paciente → tipoPaciente).

## Verification

**Commands:**
- `cd auditoria-service && mvn clean test` -- Rodar testes com novo filtro
- `mvn jacoco:report` -- Gerar report (esperado: JaCoCo 0.8.13+ para Java 25 compat)

**Manual HTTP (curl):**

1. **Filtro tipo:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?tipoPaciente=PRIORITARIO"
   # Expected: HTTP 200 + wrapper com items filtrados
   ```

2. **Combinação AND 3-way:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?tipoDecisao=RECUSA&tipoPaciente=PRIORITARIO&statusAgendamento=CONFIRMADO"
   # Expected: HTTP 200 + wrapper com recusas de pacientes PRIORITARIOS com agendamentos confirmados
   ```

3. **Validação enum:**
   ```bash
   curl -X GET "http://localhost:8080/v1/auditoria/paciente/123?tipoPaciente=INVALIDO"
   # Expected: HTTP 400 + { "error": "Tipo de paciente não reconhecido" }
   ```
