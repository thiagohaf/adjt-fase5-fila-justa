# ConfirmaSUS Frontend

Frontend React + TypeScript do ConfirmaSUS — Confirmação Ativa de Consulta e Exame (Fase 5, Hackathon FIAP).

## Stack

- **React** 18.3 + **TypeScript**
- **Vite** (build tool)
- **React Router** v6 (routing)
- **TanStack Query** (data fetching)
- **Axios** (HTTP client)
- **Tailwind CSS** (styling)

## Pré-requisitos

- Node.js 18+
- npm/yarn

## Setup

```bash
cd frontend
npm install
```

## Desenvolvimento

```bash
npm run dev
```

Acessa em `http://localhost:3000`. O proxy Vite redireciona `/v1/*` para `http://localhost:8080`.

## Build

```bash
npm run build
```

Gera `dist/` pronto para produção.

## Type-checking

```bash
npm run type-check
```

## Lint

```bash
npm run lint
```

## Estrutura de Pastas

Ver [ARCHITECTURE.md](./ARCHITECTURE.md) para detalhes.

## User Journeys

1. **UJ-1:** Paciente confirma presença (`/confirmacao/:agendamentoId`)
2. **UJ-2:** Paciente não responde (polling na dashboard)
3. **UJ-3:** Gestor decide repasse (`/repasse/:recursoId`)
4. **UJ-4:** Auditor investiga (`/auditoria/:agendamentoId`)

## Autenticação

- Login em `/login` com credentials
- JWT salvo em localStorage
- Token automaticamente adicionado a todas as requisições
- 401 redireciona para login

## Endpoints Backend

Ver [ARCHITECTURE.md](./ARCHITECTURE.md#endpoint-mapping) para mapeamento completo.

Base URL: `http://localhost:8080/v1` (em desenvolvimento, via proxy)

## Desenvolvimento Local

1. Backend rodando em `localhost:8080`
2. Frontend em desenvolvimento: `npm run dev` (localhost:3000)
3. Vite proxy redireciona `/v1/*` para backend

## Testes E2E

```bash
npm run e2e            # Cypress headless
npm run e2e:open       # Cypress interativo
npm run e2e:run-fluxo      # spec cypress/e2e/fluxo-completo.cy.ts
npm run e2e:run-auditoria  # spec cypress/e2e/auditoria-detalhes.cy.ts
```

Não há suíte de testes unitários (Jest/Vitest) — a cobertura de comportamento é feita via Cypress.

## Git Workflow

- Branches: `feature/`, `bugfix/`, `chore/` a partir de `develop`
- CR obrigatório antes de merge
- Commit com: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`
