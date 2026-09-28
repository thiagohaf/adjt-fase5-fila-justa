# ConfirmaSUS Frontend — Architecture

## Stack
- **Framework:** React 18 + TypeScript
- **Build tool:** Vite
- **Router:** React Router v6
- **State & Data Fetching:** TanStack Query + Zustand (para auth)
- **HTTP Client:** Axios com interceptors
- **Styling:** Tailwind CSS

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
│   └── ProtectedRoute.tsx
├── hooks/           # Custom hooks
│   ├── useAuth.ts
│   └── (outros hooks de negócio)
├── services/        # Clientes HTTP e APIs
│   └── api.ts
├── types/           # TypeScript types
│   └── index.ts
├── App.tsx          # Configuração de rotas
├── main.tsx         # Entry point
└── index.css        # Estilos globais
```

## Fluxo de Autenticação

1. **Login:** `LoginPage` → `useAuth.login()` → JWT salvo em localStorage
2. **Protected Routes:** `ProtectedRoute` verifica token antes de renderizar
3. **HTTP Interceptor:** Axios adiciona `Authorization: Bearer {token}` em todas as requisições
4. **Logout automático:** Se 401, token é limpo e redireciona para `/login`

## User Journeys (Stories)

### UJ-1: Paciente Confirma Presença
**Página:** `/confirmacao/:agendamentoId`
- Exibir detalhes do agendamento
- Mostrar janela de confirmação (countdown até expiração)
- Botões: Confirmar presença / Recusar
- POST `/v1/agendamentos/{id}/confirmacao` → sucesso redireciona

### UJ-2: Paciente Não Responde (Não interativo)
- Polling na dashboard detecta mudanças de status
- Exibir notificação quando vaga é liberada

### UJ-3: Gestor Decide Repasse
**Página:** `/repasse/:recursoId`
- GET `/v1/recursos/{id}/sugestao` → exibir paciente sugerido
- Botões: Confirmar / Recusar repasse
- POST `/v1/recursos/{id}/alocacoes` ou `alocacoes/recusa`

### UJ-4: Auditor Investiga
**Página:** `/auditoria/:agendamentoId`
- GET `/v1/auditoria?agendamentoId=...` → histórico cronológico
- Exibir timeline com eventos: notificação, confirmação, liberação, sugestão, decisão

## Padrões de Desenvolvimento

### API Calls com TanStack Query
```typescript
const { data, isLoading, error } = useQuery({
  queryKey: ['agendamento', agendamentoId],
  queryFn: () => api.get(`/agendamentos/${agendamentoId}`),
})
```

### Tratamento de Erro
- Interceptor axios redireciona 401 para login
- Erro 4xx/5xx exibido ao usuário em toast/modal
- Retry automático em falhas de conexão (configurável)

## Next Steps (BMAD)

1. **Epics/Stories:** Decompor UJ-1 a UJ-4 em stories individuais
2. **Feature branches:** `feature/uj-1-confirmacao`, `feature/uj-3-repasse`, etc.
3. **Code review:** Cada story fechada com test coverage (React Testing Library)
4. **Merge:** Para `develop` após PR review

## Endpoint Mapping

| UJ | Endpoint | Método | Descrição |
|----|----------|--------|-----------|
| 1 | `/v1/auth/login` | POST | Autenticação |
| 1 | `/v1/agendamentos` | GET | Listar agendamentos do paciente |
| 1 | `/v1/agendamentos/{id}` | POST | Confirmar presença |
| 2 | (polling) | GET | Monitorar status de agendamento |
| 3 | `/v1/recursos/{id}/sugestao` | GET | Sugestão de repasse |
| 3 | `/v1/recursos/{id}/alocacoes` | POST | Confirmar repasse |
| 3 | `/v1/recursos/{id}/alocacoes/recusa` | POST | Recusar repasse |
| 4 | `/v1/auditoria` | GET | Histórico auditável |

## Decisões Técnicas Abertas

- [ ] Styling: Tailwind direto ou + componentes (Headless UI)?
- [ ] State management: Context + useReducer ou Zustand?
- [ ] Formulários: React Hook Form?
- [ ] Testes: Jest + React Testing Library? Vitest?
- [ ] E2E: Playwright / Cypress?

