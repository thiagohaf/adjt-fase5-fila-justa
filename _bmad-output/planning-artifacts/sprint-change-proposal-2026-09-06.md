---
date: '2026-09-06'
status: proposed
trigger: 'Reabertura da decisão de autenticação (AD-8) durante bmad-create-epics-and-stories'
scope: moderate
---

# Sprint Change Proposal — FilaJusta: `auth-service` dedicado

## 1. Issue Summary

Durante `bmad-create-epics-and-stories` (Epic List ainda não aprovada — `epics.md` criado, Epic 1 cobria FR-10/FR-11 como "gateway valida bearer estático"), o usuário reconsiderou a decisão AD-8 da arquitetura (`status: final`, mesclada via PR #7/#8): em vez de um bearer mockado fixo validado só no `gateway-service`, propôs um microsserviço dedicado de autenticação/gestão de usuário, com os demais componentes validando o token emitido por ele.

Não há nenhuma story ou código construído ainda — a mudança é puramente de planejamento (PRD + Arquitetura + Epics), sem rollback de trabalho a considerar.

**Escopo confirmado com o usuário** (3 opções apresentadas, meio-termo escolhido): `auth-service` com schema Postgres próprio (`auth`), tabela de usuários sintéticos **pré-cadastrados via migration** (não via `seed-adapter`/FR-10 — que segue restrito a unidades de saúde/leitos/especialistas), endpoint `POST /v1/auth/login` (usuário+senha mockados) que emite um **JWT assinado** (HS256, segredo compartilhado com `gateway-service`, mesmo padrão de segredo do AD-7). Sem CRUD de usuário via API, sem RBAC aplicado (claim de papel só informativo) — os Non-Goals de OAuth/SSO/RBAC do PRD permanecem intactos; a única mudança de fundo é "bearer estático fixo em config" → "token emitido dinamicamente por um serviço dedicado".

## 2. Impact Analysis

### Epic Impact

- **Epic 1 (Fundação da Plataforma — Autenticação e Dados Sintéticos)**: escopo muda de "gateway valida bearer estático" para "auth-service emite JWT + gateway valida assinatura". Ainda cobre FR-10/FR-11, nenhuma FR nova. Nenhum outro epic (2, 3, 4) é afetado na sua lista de FRs — eles continuam consumindo autenticação como uma capability já resolvida pelo Epic 1, só que agora via JWT em vez de bearer fixo (nenhuma mudança de contrato visível para eles: ainda é um header `Authorization` validado no gateway).
- Nenhum epic fica obsoleto, nenhum novo epic é necessário, ordem/prioridade não muda.

### Artifact Conflicts

- **PRD** (`prd.md`, `status: final`): FR-11 precisa ser reescrito (mecanismo de emissão de token) e §5 Non-Goals precisa de uma frase ajustada (mantendo a exclusão de OAuth/SSO/RBAC/CRUD, deixando claro que login mockado dinâmico não é OAuth real).
- **Arquitetura** (`ARCHITECTURE-SPINE.md`, `status: final`): AD-1 (menção ao token do `seed-adapter`), AD-8 (mecanismo de validação), nova **AD-14** (emissão via `auth-service`), AD-9 (lista de schemas), AD-13 (lista de serviços Spring Boot), Capability→Architecture Map (linha FR-11), Stack (biblioteca JWT), Consistency Conventions (State & cross-cutting), Structural Seed (diagrama de contêineres + árvore de pastas), Deferred (nota de RBAC).
- **solution-design.md**: diagrama C4 Nível 2 (novo contêiner), nova subseção AD-14, linha FR-11 da tabela de rastreabilidade, diagrama de sequência (passo de login).
- **deck.html**: os dois diagramas SVG (visão de contêineres e topologia de rede) ganham a caixa `auth-service`; legendas atualizadas.
- **epics.md**: goal do Epic 1 ajustado; requisitos adicionais (lista de ADs) atualizados com AD-14.
- Nenhum impacto em UI/UX (não existe, backend-only) nem em scripts de deploy além de: `auth-service` precisa entrar no `deploy/pause/destroy` como um 5º serviço ECS (nota para a story de infraestrutura do Epic 1, não uma mudança de documento agora).

## 3. Recommended Approach

**Opção 1 — Ajuste direto (recomendada).** Como nada foi construído ainda, não há rollback a avaliar (Opção 2 não se aplica) e o MVP/goals do PRD não mudam de tamanho ou ambição (Opção 3 não se aplica — os Non-Goals de auth real continuam de pé). Atualizar PRD, Arquitetura, solution-design.md, deck.html e epics.md diretamente, hoje, antes de aprovar a Epic List.

- Esforço: **Médio** (7 arquivos, mas mudanças localizadas e sem código a refatorar).
- Risco: **Baixo** (fase de planejamento, nenhuma regressão possível).

## 4. Detailed Change Proposals

### 4.1 PRD (`prd.md`)

**FR-11 — de:**
> A API exige um token de autenticação (mockado, não um provedor de identidade real) para acessar qualquer endpoint que não seja público/de health-check.
> Consequences: sem token válido → 401; sem distinção de papéis.
> Out of Scope: OAuth/SSO real, RBAC por papel, expiração/rotação de token.

**para:**
> Um usuário sintético pré-cadastrado (representando Regulador, Profissional de Triagem ou Auditor) autentica-se via `POST /login` com usuário e senha mockados contra um serviço de autenticação dedicado, recebendo um token assinado; a API exige esse token para acessar qualquer endpoint que não seja público/de health-check/de login.
> **Consequences:** login com credenciais inválidas → `401`; requisição sem token válido (ausente, expirado ou assinatura inválida) para endpoint protegido → `401`; nenhuma distinção de papéis é exigida nesta fase — qualquer token válido acessa qualquer endpoint protegido, independentemente do papel do usuário autenticado.
> **Out of Scope:** auto-registro de usuário, CRUD de usuário via API, OAuth/SSO real, RBAC por papel aplicado, expiração/rotação de token de nível produção — autenticação e autorização completas de nível produção ficam para depois do MVP.

**§5 Non-Goals — de:**
> Autenticação e autorização completas de nível produção (RBAC, SSO, OAuth) — token mockado é suficiente nesta fase.

**para:**
> Autenticação e autorização completas de nível produção (RBAC aplicado, SSO, OAuth real, CRUD de usuário via API) — login mockado contra usuários sintéticos pré-cadastrados, com token assinado emitido por um serviço dedicado, é suficiente nesta fase.

**Rationale:** preserva a intenção original (nada de auth real) enquanto reflete que o mecanismo de emissão agora é um serviço dedicado, não um valor fixo em config.

### 4.2 Arquitetura (`ARCHITECTURE-SPINE.md`)

**AD-1 — trecho sobre `seed-adapter`, de:**
> `seed-adapter` (job Lambda, Quarkus) é um cliente comum: entra pelo `gateway-service` com o mesmo token mockado de qualquer outro cliente (FR-11)...

**para:**
> `seed-adapter` (job Lambda, Quarkus) é um cliente comum: autentica-se contra `auth-service` (AD-14) com um usuário técnico pré-cadastrado para obter seu próprio token, e entra pelo `gateway-service` como qualquer outro cliente (FR-11)...

**AD-8 — Rule, de:**
> `gateway-service` (Spring Cloud Gateway) é o único ponto que valida o token mockado (bearer estático) contra endpoints protegidos...

**para:**
> `gateway-service` (Spring Cloud Gateway) é o único ponto que valida o token emitido por `auth-service` (AD-14) — verifica assinatura e expiração do JWT — contra endpoints protegidos...

**Nova AD-14 — Emissão de token via `auth-service` dedicado:**
- **Binds:** FR-11
- **Rule:** `auth-service` (Spring Boot) tem schema próprio (`auth`, AD-9) com uma tabela de usuários sintéticos pré-cadastrados por migration (Flyway) na inicialização — não via `seed-adapter`/FR-10, que segue restrito a unidades de saúde/leitos/especialistas. Expõe `POST /v1/auth/login` (usuário + senha mockados) e retorna um JWT assinado (HS256, segredo compartilhado com `gateway-service` via config/Secrets Manager, mesmo padrão de segredo do AD-7) com um claim de papel (`role`) puramente informativo — nenhum serviço aplica controle de acesso por papel (Non-Goal do PRD, FR-11). `gateway-service` (AD-8) valida a assinatura/expiração do JWT. A rota de login é pública através da mesma exceção estreita e nomeada de security group usada para health-check (AD-8/AD-12). `seed-adapter` usa um usuário técnico pré-cadastrado nessa mesma tabela para se autenticar antes de chamar o gateway (AD-1).
- **Prevents:** bearer estático fixo em config como única credencial do sistema; um serviço de domínio reimplementando validação de token (mantém AD-8 como único ponto de enforcement); rota de login vazando para fora do gateway; usuários mockados perdidos a cada redeploy por viverem só em memória; `seed-adapter` ficando sem meio de se autenticar após a mudança.

**AD-9 — lista de schemas, de:** `triagem_score`, `matching_alocacao`, `auditoria` → **para:** `triagem_score`, `matching_alocacao`, `auditoria`, `auth`.

**AD-13 — lista de serviços Spring Boot, de:** `gateway-service`, `triagem-score-service`, `matching-alocacao-service` e `auditoria-service` → **para:** `gateway-service`, `triagem-score-service`, `matching-alocacao-service`, `auditoria-service` e `auth-service`.

**Capability → Architecture Map — linha FR-11, de:**
`FR-11 Autenticação por token mockado | gateway-service | AD-8, AD-12`
**para:**
`FR-11 Autenticação por token mockado | auth-service (emissão) + gateway-service (validação) | AD-8, AD-12, AD-14`

**Stack — nova linha:** biblioteca JWT (ex.: `io.jsonwebtoken:jjwt` ou suporte nativo Spring Security OAuth2 Resource Server para validação) usada por `auth-service` (emissão) e `gateway-service` (validação).

**Consistency Conventions — linha "State & cross-cutting", de:** `...auth só no gateway (AD-8)...` → **para:** `...emissão de token via auth-service (AD-14), validação só no gateway (AD-8)...`

**Structural Seed — diagrama de contêineres:** adicionar nó `AUTHc[auth-service]` no subgraph ECS, aresta `GWc --> AUTHc` e `AUTHc --> PG`.

**Structural Seed — árvore de pastas:** adicionar bloco `auth-service/` (domain: Usuario; application/query: AutenticarUsuario; infrastructure/web: `POST /v1/auth/login`; infrastructure/persistence: schema `auth` + migration com usuários sintéticos; `application.yml`: segredo JWT compartilhado).

**Deferred — nota de RBAC, de:**
> RBAC por papel (FR-11) — postura consciente do MVP (qualquer token válido acessa qualquer endpoint); revisitável se a banca exigir isolamento por papel.

**para:**
> RBAC por papel (FR-11) — o JWT emitido por `auth-service` (AD-14) já carrega um claim de `role`, mas nenhum serviço aplica controle de acesso por papel nesta fase; ativar RBAC seria estender AD-8 para inspecionar esse claim, não uma mudança estrutural. Revisitável se a banca exigir isolamento por papel.

### 4.3 `solution-design.md`

- **C4 Nível 2 (diagrama + prosa):** adicionar contêiner `AUTH["auth-service (Spring Boot)"]`, arestas `Client -->|"POST /login"| GW`, `GW -->|REST| AUTH`, `AUTH -->|"JDBC (schema auth)"| PG`; atualizar a frase "Quatro contêineres de runtime" → "Cinco contêineres de runtime (gateway + auth + 3 serviços de domínio)...".
- **Nova subseção "AD-14 — Autenticação via serviço dedicado, não bearer fixo"** em §5, no mesmo estilo narrativo das demais (por que um serviço dedicado, por que JWT com segredo compartilhado em vez de PKI completa, por que os usuários vivem em migration e não no `seed-adapter`).
- **§7 Rastreabilidade — linha FR-11**, de `gateway-service | AD-8, AD-12` → `auth-service (emissão) + gateway-service (validação) | AD-8, AD-12, AD-14`.
- **Diagrama de sequência (§6):** inserir, antes do primeiro `PT->>GW`, os passos de login: `PT->>GW: POST /auth/login` → `GW->>AUTH: encaminha (rota pública)` → `AUTH-->>GW: 200 (JWT)` → `GW-->>PT: 200 (JWT)`.

### 4.4 `deck.html`

Adicionar a caixa `auth-service` e as arestas correspondentes nos dois diagramas SVG existentes (visão de contêineres ~L226-265; topologia de rede ~L492-538), com legendas atualizadas para citar 5 serviços em vez de 4.

### 4.5 `epics.md`

- Epic 1 (goal): "...protegido por token mockado..." → "...protegido por token JWT emitido por um serviço de autenticação dedicado (`auth-service`)..."
- Additional Requirements: incluir a regra da nova AD-14 na lista já extraída.

## 5. Implementation Handoff

- **Escopo:** Moderado — reorganização de documentos de planejamento (PRD, Arquitetura, solution-design, deck, epics), sem código a alterar (nada construído ainda).
- **Responsável pela execução:** eu mesmo, nesta sessão, atuando como Architect/PM (papéis BMAD combinados), com sua aprovação explícita abaixo.
- **Critério de sucesso:** os 5 artefatos citados ficam mutuamente consistentes (FR-11, AD-1/AD-8/AD-9/AD-13/nova AD-14, diagramas, epics.md) e sem contradição entre "token mockado fixo" (texto antigo) e "token emitido por auth-service" (texto novo) em nenhum lugar.
- **Depois:** retomar `bmad-create-epics-and-stories` a partir da Epic List (ainda pendente de aprovação), já refletindo o novo Epic 1.
