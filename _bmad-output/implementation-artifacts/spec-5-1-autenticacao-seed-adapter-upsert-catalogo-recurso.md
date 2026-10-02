---
title: 'Story 5.1 — Autenticação do seed-adapter e Upsert do Catálogo de Recurso'
type: 'feature'
created: '2026-09-22'
status: 'done'
review_loop_iteration: 1
baseline_commit: '8f7776719ad40ceb98653cf292022e67ba636f3b'
context:
  - _bmad-output/implementation-artifacts/epic-5-context.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O seed-adapter não possui autenticação centralizada nem carrega o catálogo de Recurso de forma idempotente. Sem Recurso carregado, as stories posteriores (5.2: Agendamentos, 5.3: Lista de Espera) não conseguem referenciar recursos válidos.

**Approach:** Implementar autenticação via JWT (usuário técnico pré-cadastrado) e criar endpoint de upsert idempotente para Recurso via gateway, usando `codigoRecurso` como chave de idempotência.

## Boundaries & Constraints

**Always:**
- Autenticação centralizada: seed-adapter obtém JWT de `auth-service` com credenciais de usuário técnico, usa esse token em *todas* as chamadas ao gateway — nunca faz requisição direta a serviço de domínio
- Upsert idempotente por `codigoRecurso`: reexecutar Story 5.1 não duplica Recurso
- Integração via gateway (`POST /v1/...`), não direto aos serviços de domínio
- Falha explícita sob indisponibilidade: se `auth-service` ou `gateway-service` estiver indisponível, aborta com erro claro
- CPF não circula fora de `agendamento-confirmacao-service` (afeta Story 5.2, não esta story)
- Versionamento de endpoint `/v1/`

**Ask First:** (RESOLVIDO)
- ✅ Seed-adapter acessa `/v1/recursos` via gateway com JWT (decisão: [A])
- ✅ Modelo Recurso será expandido com `especialidade` e `unidade` (decisão: [Y])
- ✅ Seed-data em formato JSON (decisão: [JSON])

**Never:**
- Gerar CPF no seed-adapter (resolvido em Story 1.1)
- Criar Recurso sem validação de `codigoRecurso` (deve rejeitar duplicata, não atualizar silenciosamente)
- Persistir credenciais técnicas no código (usar variáveis de ambiente/Secrets Manager)

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Saída Esperada | Tratamento de Erro |
|---------|------------------|----------------|--------------------|
| JWT obtido com sucesso | Credenciais técnicas em env (username/password) | Token JWT válido obtido, usado em headers Authorization | Falha explícita se auth-service indisponível (erro claro, aborta) |
| Recurso novo (modelo expandido) | `{codigoRecurso, especialidade, unidade, especificidadeRank, disponivel}` | Recurso criado com `recursoId` UUID, retorna 201 | `422` se `codigoRecurso` já existe ou dados inválidos |
| Recurso já existe (idempotência) | Mesmo `codigoRecurso`, dados iguais | Retorna 200 com `recursoId` existente, sem duplicata | Sucesso silencioso (idempotência) |
| Recurso já existe, dados diferentes | Mesmo `codigoRecurso`, dados novos (ex: unidade diferente) | Retorna 200, Recurso atualizado via upsert | Sucesso silencioso (upsert, sem erro de conflito) |
| Gateway indisponível | POST para /v1/recursos com JWT | Erro 503/timeout | Falha explícita, aborta seed-adapter (não retorna) |
| JWT expirado ou inválido | POST com header `Authorization: Bearer {expired_token}` | Erro 401 Unauthorized | Seed-adapter obtém novo JWT e retenta |
| Dados JSON malformados | JSON inválido no payload seed-data | Erro 400 Bad Request | Falha explícita na validação do payload |

</frozen-after-approval>

## Code Map

- `seed-adapter/` -- Projeto Quarkus/Lambda separado (não existente ainda; será novo serviço)
- `auth-service/AuthController.java` -- `POST /v1/auth/login` (username/password → JWT HS256)
- `auth-service/TokenIssuer.java` (JwtTokenIssuer) -- Geração de JWT com `sub`, `role`, `iat`, `exp`
- `gateway-service/JwtAuthenticationFilter.java` -- Validação de JWT (GlobalFilter), allowlist: `/v1/auth/login`, `/actuator/health`
- `matching-alocacao-service/RecursoJpaRepository.java` -- Upsert idempotente via `ON CONFLICT (codigo_recurso) DO UPDATE` (SQL nativo) — **não sobrescreve `recursoId`**
- `matching-alocacao-service/UpsertRecursoRequest.java` -- DTO entrada (modelo: `codigoRecurso`, `especificidadeRank`, `disponivel`)
- `matching-alocacao-service/UpsertRecursoIntegrationTest.java` -- Testes idempotência (referência para implementação)
- `matching-alocacao-service/TriagemScoreClient.java` -- Padrão RestClient existente (modelo para seed-adapter HTTP clients)
- `.env` / AWS Secrets Manager -- Credenciais técnicas (username/password para JWT de seed-adapter)

## Tasks & Acceptance

**Execution:**
- [x] `matching-alocacao-service/` -- Criar controller público `RecursoPublicController` com endpoint `POST /v1/recursos` que roteia para `RecursoJpaRepository` (upsert idempotente via `ON CONFLICT`) — wrapper sobre `/internal/recursos`
- [x] `matching-alocacao-service/` -- Expandir modelo `Recurso` com campos `especialidade` (String) e `unidade` (String, nullable ou required por negócio); atualizar migration Flyway
- [x] `matching-alocacao-service/` -- Atualizar `UpsertRecursoRequest` e `RecursoResponse` com `especialidade` e `unidade`
- [x] `seed-adapter/` -- Criar novo projeto Quarkus/Lambda com pom.xml (baseado em referência raiz `/pom.xml`), estrutura Maven padrão
- [x] `seed-adapter/` -- Implementar classe `AuthClient`: chama `POST /v1/auth/login` com credenciais de env (username/password), obtém JWT, reutiliza em requisições subsequentes (refresh se expirado)
- [x] `seed-adapter/` -- Implementar classe `RecursoClient`: cliente HTTP/REST (padrão `RestClient` ala `TriagemScoreClient`) para chamar `POST /v1/recursos` do gateway com JWT autenticado
- [x] `seed-adapter/` -- Implementar carregamento de seed-data em **formato JSON** (arquivo local `resources/seed-data.json`) com 3-5 Recursos de teste com campos: `codigoRecurso`, `especialidade`, `unidade`, `especificidadeRank`, `disponivel`
- [x] `seed-adapter/` -- Implementar orquestração `SeedDataLoaderJob`: obtém JWT via `AuthClient`, carrega JSON, chama `RecursoClient.upsert()` para cada entrada usando `codigoRecurso` como idempotência key
- [x] Testes: seed-adapter 14/14 passando; matching-alocacao-service 226/226 passando (inclui correções de construtores Recurso + imports)

**Acceptance Criteria:**
- Given credenciais técnicas (username/password) em variáveis de ambiente, when seed-adapter inicia, then obtém JWT de `POST /v1/auth/login` sem erro
- Given JWT válido, when seed-adapter faz POST para `POST /v1/recursos` ao gateway com header `Authorization: Bearer {JWT}`, then gateway valida JWT e roteia para `/v1/recursos` de `matching-alocacao-service`
- Given seed-data JSON com `{codigoRecurso, especialidade, unidade, especificidadeRank, disponivel}`, when seed-adapter carrega primeira vez, then cada Recurso é criado com `recursoId` UUID único, retorna 201 Created
- Given mesmo dataset executado novamente (idempotência), when seed-adapter carrega, then nenhum Recurso duplicado (verificar COUNT em banco), retorna 200 OK com IDs existentes
- Given `codigoRecurso` já existente com dados diferentes (ex: unidade alterada), when seed-adapter faz upsert, then Recurso é atualizado via `ON CONFLICT DO UPDATE`, retorna 200 OK (sucesso silencioso)
- Given `auth-service` indisponível, when seed-adapter tenta obter JWT, then aborta com erro claro ("auth-service unavailable"), sem prosseguir para carga de Recurso
- Given seed-data JSON malformado (inválido), when seed-adapter tenta carregar, then aborta com erro claro descrevendo o problema JSON (parsing error)

## Spec Change Log

**Loop 1 (2026-09-22 code-review):**
- **Triggering finding**: verification-gap & edge-case-hunter identified undocumented null/whitespace handling for `especialidade` and `unidade` fields, creating inconsistency with `codigoRecurso` normalization strategy
- **Amendment**: Added Design Notes section "Normalização de Campos especialidade/unidade" clarifying that both fields must be trimmed (leading/trailing whitespace removed) before persistence, consistent with `codigoRecurso` behavior
- **Known-bad state avoided**: Malformed data (e.g., "Cardiologia " with trailing space) persisting silently, causing mismatches in downstream filtering/matching (Stories 5.2/5.3)
- **KEEP**: Upsert idempotency pattern (ON CONFLICT), test structure (constructor updates), Quarkus seed-adapter architecture, seed-data.json format — all solid; just add normalization logic to domain constructor or application layer

## Design Notes

### Upsert Idempotente via `ON CONFLICT`

Recurso já implementa upsert via PostgreSQL `ON CONFLICT (codigo_recurso) DO UPDATE`:
- `recursoId` é gerado via `UUID.randomUUID()` ANTES do upsert
- Na colisão, PostgreSQL preserva `recursoId` antigo (não está na cláusula SET)
- App valida via `findByCodigoRecurso()` para detectar insert vs. update (diferenciar 201 vs. 200)
- Sem locks distribuídos — PostgreSQL garante atomicidade

**Implicação**: seed-adapter pode chamar idempotentemente — a enésima execução retorna mesmo `recursoId`, sem duplicata.

### Autenticação Centralizada via JWT

Padrão existente em `auth-service`:
- Issuer: `TokenIssuer` (classe `JwtTokenIssuer`)
- Algoritmo: HS256, secret de 32+ bytes
- Claims: `sub` (username), `role`, `iat`, `exp`

Seed-adapter faz:
1. `POST /v1/auth/login` com credenciais técnicas (env vars)
2. Armazena JWT em memória (sem persist)
3. Usa em todas as requisições ao gateway (header `Authorization: Bearer {JWT}`)
4. Refresh se expirado (reinicializa se nova chamada > exp)

### Segredos em Environment

Credenciais técnicas (username/password) vêm de env vars ou AWS Secrets Manager, **nunca** hardcoded. Seed-adapter lê na inicialização.

### Decisão: `/v1/recursos` via Gateway [DECISÃO: A]

**Escolhido**: Criar controller público `/v1/recursos` que seed-adapter chama via gateway com JWT.

**Rationale**:
- Consistente com AD-2 (Clean Architecture, all clients via gateway)
- Mantém autenticação centralizada (JWT validado no gateway, não duplicado em cada serviço)
- Facilita auditoria (requests passam por gateway, correlationId propagado)
- Suporta future RBAC ou rate-limiting no gateway sem mudança em seed-adapter

**Tradeoff**: Latência extra (gateway → matching-alocacao-service) aceitável para job de deploy-time.

### Modelo Recurso Expandido [DECISÃO: Y]

**Antes**: `recursoId`, `codigoRecurso`, `especificidadeRank`, `disponivel`

**Agora**: Adicionar:
- `especialidade` (String) — tipo de recurso (ex: "Cardiologia", "Cirurgia", "Radiologia")
- `unidade` (String) — unidade de saúde/localização (ex: "Hospital Central", "UBS Zona Leste")

**Implicação**: Requer migration Flyway (`ALTER TABLE recurso ADD ...`), expansão de Request/Response DTOs, e atualização de testes.

### Seed-Data em JSON [DECISÃO: JSON]

**Formato escolhido**: JSON (não YAML).

**Locação**: `seed-adapter/src/main/resources/seed-data.json`

**Estrutura**:
```json
{
  "recursos": [
    {
      "codigoRecurso": "01",
      "especialidade": "Cardiologia",
      "unidade": "Hospital Central",
      "especificidadeRank": 1,
      "disponivel": true
    },
    ...
  ]
}
```

**Rationale**: Interop uniforme com `ObjectMapper`, parsing sem dependência extra (YAML requer SnakeYAML).

### Normalização de Campos especialidade/unidade

**Regra (CLARIFICADO em Loop 1)**: Campos `especialidade` e `unidade` devem seguir o mesmo padrão de normalização que `codigoRecurso`:
- Se não-null, **trim whitespace** (leading/trailing) antes de persistir
- Preservar null se recebido como null (backward compat com dados antigos)
- Rejeitar string vazia (após trim) como inválida via validação (padrão de `codigoRecurso`?)

**Rationale**: Consistência com domínio. Se `codigoRecurso` é normalizado, campos categorização devem ser também, evitando dados silenciosamente malformados (ex: "Cardiologia " != "Cardiologia").

## Verification

**Commands:**
- `mvn -pl seed-adapter test` -- Esperado: testes de autenticação + upsert passam, cobertura ≥90% domínio (se aplicável)
- `cdk synth` -- Esperado: Quarkus + RoleExecution + variáveis de ambiente para credenciais técnicas configuradas sem erro
- `curl -X POST http://localhost:8080/v1/recursos -H "Authorization: Bearer $JWT" -d '{"codigoRecurso": "01", ...}'` -- Esperado: retorna `{id, codigoRecurso, ...}` sem duplicata em reexecução

**Manual checks:**
- Verificar arquivo de seed-data (YAML/JSON) com 3-5 Recursos de teste
- Verificar que nenhuma credencial (username/password) aparece em código ou logs

## Suggested Review Order

**Public API Entry Point**

- Novo endpoint `/v1/recursos` com JWT via gateway, wrapper sobre repositório idempotente
  [`RecursoPublicController.java:33`](../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/web/RecursoPublicController.java#L33)

**Domain Model Expansion**

- Recurso adicionado especialidade/unidade com normalização (trim) consistente com codigoRecurso
  [`Recurso.java:38`](../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/domain/Recurso.java#L38)

**Persistence Layer**

- Mapeamento JPA de schema: nova tabela com colunas especialidade, unidade, migration V9
  [`RecursoJpaEntity.java:35`](../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/persistence/RecursoJpaEntity.java#L35)

- Upsert idempotente SQL nativo: INSERT + ON CONFLICT DO UPDATE com novos campos
  [`RecursoJpaRepository.java:23`](../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/persistence/RecursoJpaRepository.java#L23)

- Migration V9: ALTER TABLE recurso ADD especialidade, unidade (nullable)
  [`V9__add_especialidade_unidade_to_recurso.sql:1`](../../../matching-alocacao-service/src/main/resources/db/migration/V9__add_especialidade_unidade_to_recurso.sql#L1)

**Application Command Layer**

- UpsertRecurso.upsertar() expandida: assinatura com 6 params (adicionado especialidade, unidade)
  [`UpsertRecurso.java:77`](../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/application/command/UpsertRecurso.java#L77)

**Request/Response DTOs**

- UpsertRecursoRequest: campos opcionais especialidade, unidade (validação em Body/Builder)
  [`UpsertRecursoRequest.java:308`](../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/web/UpsertRecursoRequest.java#L308)

- RecursoResponse: payload público com specialidade, unidade
  [`RecursoResponse.java:256`](../../../matching-alocacao-service/src/main/java/com/confirmasus/matching/infrastructure/web/RecursoResponse.java#L256)

**Seed-Adapter Orchestration (Novo Projeto)**

- Arquitetura: Quarkus job de carga, 3 clients (Auth, Recurso, orchestrador SeedDataLoader)
  [`seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataLoader.java:1`](../../../seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataLoader.java#L1)

- AuthClient: obtém JWT de auth-service, cache 55min, refresh automático
  [`seed-adapter/src/main/java/com/confirmasus/seedadapter/AuthClient.java:56`](../../../seed-adapter/src/main/java/com/confirmasus/seedadapter/AuthClient.java#L56)

- RecursoClient: POST via gateway com Bearer JWT, idempotente por codigoRecurso
  [`seed-adapter/src/main/java/com/confirmasus/seedadapter/RecursoClient.java:58`](../../../seed-adapter/src/main/java/com/confirmasus/seedadapter/RecursoClient.java#L58)

- Seed-data.json: 5 recursos com especialidade+unidade preenchidas (Cardiologia, Cirurgia, etc)
  [`seed-adapter/src/main/resources/seed-data.json:1`](../../../seed-adapter/src/main/resources/seed-data.json#L1)

**Testing Coverage**

- Domain: Recurso normalization tests (trim especialidade/unidade como codigoRecurso)
  [`RecursoTest.java:556`](../../../matching-alocacao-service/src/test/java/com/confirmasus/matching/domain/RecursoTest.java#L556)

- Seed-adapter: 21 testes (AuthClient caching, RecursoClient JWT header, SeedDataLoader error handling)
  [`AuthClientTest.java:40`](../../../seed-adapter/src/test/java/com/confirmasus/seedadapter/AuthClientTest.java#L40)
