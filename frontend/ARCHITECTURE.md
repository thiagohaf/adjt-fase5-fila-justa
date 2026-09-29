# ConfirmaSUS Frontend — Architecture

## Stack
- **Framework:** React 18.3 + TypeScript
- **Build tool:** Vite (dev server em `:3000`, proxy `/v1/*` → `http://localhost:8080`)
- **Router:** React Router v6
- **State & Data Fetching:** TanStack Query; autenticação via React Context (`AuthProvider`/`useAuth`) + `useState`, sem lib de state management externa
- **HTTP Client:** Axios
- **Styling:** Tailwind CSS
- **Testes E2E:** Cypress

## Estrutura de Pastas

```
src/
├── pages/           # Páginas por rota (UJ-1 a UJ-4)
│   ├── LoginPage.tsx
│   ├── DashboardPage.tsx
│   ├── ConfirmacaoPage.tsx        # UJ-1: Paciente confirma
│   ├── RepassePage.tsx            # UJ-3: Gestor repassa
│   └── AuditoriaPage.tsx          # UJ-4: Auditor investiga
├── components/      # Componentes reutilizáveis
│   ├── ProtectedRoute.tsx
│   ├── ConfirmacaoButtons.tsx     # Confirmar/Recusar presença (UJ-1)
│   ├── CountdownDisplay.tsx       # Countdown da janela de confirmação
│   ├── RecusarSugestaoModal.tsx   # Modal de recusa de repasse (UJ-3)
│   ├── RecursoNome.tsx            # Resolve nome do Recurso a partir do id
│   ├── ErrorBoundary.tsx
│   └── Toast.tsx
├── hooks/           # Custom hooks
│   ├── useAuth.tsx                # AuthProvider + useAuth (Context API)
│   └── useCountdown.ts
├── services/        # Clientes HTTP e APIs
│   └── api.ts
├── types/           # TypeScript types
│   └── index.ts
├── App.tsx          # Configuração de rotas
├── main.tsx         # Entry point
└── index.css        # Estilos globais
```

## Fluxo de Autenticação

1. **Login:** `LoginPage` → `useAuth().login()` → `POST /v1/auth/login` → JWT decodificado (payload `sub`/`role`) e salvo em `localStorage` (`auth_token`)
2. **Protected Routes:** `ProtectedRoute` verifica `isAuthenticated` do contexto antes de renderizar
3. **HTTP Interceptor:** Axios adiciona `Authorization: Bearer {token}` em todas as requisições
4. **Logout automático:** interceptor de resposta do Axios (`services/api.ts`) detecta `401`, limpa `auth_token` do `localStorage` e redireciona para `/login` via `window.location.href`

## User Journeys (Stories)

### UJ-1: Paciente Confirma Presença
**Página:** `/confirmacao/:agendamentoId`
- Exibir detalhes do agendamento
- Mostrar janela de confirmação (countdown até expiração)
- Botões: Confirmar presença / Recusar
- POST `/v1/agendamentos/{id}/confirmacao` → sucesso redireciona

### UJ-2: Paciente Não Responde (Não interativo)
- `DashboardPage` faz `refetchInterval: 15000` (polling a cada 15s) sobre `GET /v1/agendamentos`
- Mudança de status (ex. vaga liberada) aparece na próxima atualização automática, sem ação do usuário

### UJ-3: Gestor Decide Repasse
**Página:** `/repasse/:recursoId`
- GET `/v1/recursos/{id}/sugestao` → exibir paciente sugerido
- Botões: Confirmar / Recusar repasse (`RecusarSugestaoModal` para captura do motivo)
- POST `/v1/recursos/{id}/alocacoes` (confirmar) ou `/v1/recursos/{id}/alocacoes/recusa` (recusar)

### UJ-4: Auditor Investiga
**Página:** `/auditoria/:agendamentoId`
- GET `/v1/auditoria/agendamento/{agendamentoId}` → histórico cronológico
- Exibir timeline com eventos: notificação, confirmação, liberação, sugestão, decisão

## Padrões de Desenvolvimento

### API Calls com TanStack Query
```typescript
const { data, isLoading, error } = useQuery({
  queryKey: ['agendamento', agendamentoId],
  queryFn: () => api.get(`/v1/agendamentos/${agendamentoId}`),
})
```

### Tratamento de Erro
- Interceptor axios (`services/api.ts`) redireciona 401 para login
- `ErrorBoundary` captura falhas de renderização
- `Toast` exibe erro 4xx/5xx ao usuário

## Endpoint Mapping (real)

| UJ | Endpoint | Método | Descrição |
|----|----------|--------|-----------|
| 1 | `/v1/auth/login` | POST | Autenticação |
| 1 | `/v1/agendamentos` | GET | Listar agendamentos (polling 15s, também cobre UJ-2) |
| 1 | `/v1/agendamentos/{id}` | GET | Detalhe do agendamento (`ConfirmacaoPage`) |
| 1 | `/v1/agendamentos/{id}/confirmacao` | POST | Confirmar presença |
| 1 | `/v1/agendamentos/{id}/recusa` | POST | Recusar presença |
| 3 | `/v1/recursos/{id}/sugestao` | GET | Sugestão de repasse pendente |
| 3 | `/v1/recursos/{id}/alocacoes` | POST | Confirmar repasse |
| 3 | `/v1/recursos/{id}/alocacoes/recusa` | POST | Recusar repasse |
| 4 | `/v1/auditoria/agendamento/{id}` | GET | Histórico auditável do agendamento |

Estes endpoints são servidos hoje por `agendamento-confirmacao-service` (agendamentos), `matching-alocacao-service` (recursos/alocações) e `auditoria-service` — ver nota de nomenclatura em `_bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md` (o serviço `matching-alocacao-service` cumpre hoje o papel de repasse, mas a renomeação para `liberacao-repasse-service` prevista na arquitetura não foi executada).

