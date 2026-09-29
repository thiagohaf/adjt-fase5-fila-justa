# ⚠️ Notas de Setup para Testes E2E

## Pré-requisitos de Dados

Os testes E2E precisam de dados reais no banco de dados para funcionar:

### 1. **Agendamentos para Teste**
- Testes do fluxo completo (dashboard → confirmação → auditoria) precisam de agendamentos reais
- **Ação necessária**: Criar endpoint `GET /v1/agendamentos` (lista paginada) no backend
  - Endpoint atual: apenas `GET /v1/agendamentos/{id}` (consultar um)
  - Recomendação: implementar com paginação e filtros opcionais

### 2. **Usuário de Teste**
- ✅ Credenciais já disponíveis:
  ```
  Username: regulador
  Password: regulador#2026
  ```

### 3. **DashboardPage - Listagem de Agendamentos**
- ⚠️ DashboardPage atual mostra apenas estatísticas (hardcoded)
- **Ação necessária**: Implementar listagem real de agendamentos
  - Consumir `GET /v1/agendamentos` (quando implementado)
  - Adicionar dados aos agendamentos: data, horário, paciente, status
  - Adicionar `data-testid` conforme documentado em `cypress/README.md`

### 4. **Eventos de Auditoria**
- ✅ Endpoint `GET /v1/auditoria/agendamento/{id}` já existe
- ✅ Data-testid já foram adicionados ao componente AuditoriaPage
- Testes funcionarão assim que houver agendamentos com histórico

## Como Proceder

### Opção A: Setup Rápido (Recomendado para E2E)
1. **Criar endpoint de listagem** no backend (15 min)
   ```
   GET /v1/agendamentos?page=0&size=10
   Response: Page<AgendamentoDTO>
   ```

2. **Atualizar DashboardPage** (30 min)
   ```typescript
   const { data: agendamentos } = useQuery({
     queryKey: ['agendamentos'],
     queryFn: () => api.get('/v1/agendamentos')
   })
   ```

3. **Rodar testes**:
   ```bash
   npm run e2e
   ```

### Opção B: E2E com Dados Mock (Não Recomendado)
- Adicionar `cy.intercept()` nos testes para mockerar respostas
- Menos realista, não testa integração real

### Opção C: Testes Parciais (Agora)
- Testes que funcionar hoje:
  - ✅ UJ-1: Login
  - ✅ UJ-6: Auditoria (para agendamentos específicos)
  - ✅ ConfirmacaoPage e RepassePage (com ID direto na URL)
- Testes que ficarem pending:
  - ⏳ UJ-2/UJ-3: Dashboard + Agendamentos
  - ⏳ Fluxo completo integrado

## Arquivo E2E Modular

Testes foram estruturados assim:

```
cypress/e2e/
├── fluxo-completo.cy.ts        # Integrado (depende de listagem)
└── auditoria-detalhes.cy.ts    # Isolated (funciona agora)
```

## Executar Agora

```bash
# Apenas testes que funcionam atualmente
npm run e2e:run-auditoria

# Todos (alguns falharão sem listagem)
npm run e2e
```

## Próximas Ações (Sugeridas)

- [ ] Implementar `GET /v1/agendamentos` no backend
- [ ] Atualizar `DashboardPage.tsx` para listar agendamentos reais
- [ ] Reexecutar `npm run e2e` (fluxo completo deve passar)
- [ ] Integrar com CI/CD (GitHub Actions)

## Troubleshooting

| Erro | Causa | Solução |
|------|-------|---------|
| `Cannot find agendamento-item` | Sem agendamentos no DB | Criar endpoint listagem |
| `Timeout waiting for elemento` | Sem dados | Verificar `/v1/agendamentos` status |
| `Login fails` | Credenciais | Usar `regulador` / `regulador#2026` |

## Referências

- Spec de agendamentos: `/agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoController.java`
- Componentes React: `frontend/src/pages/*.tsx`
- Cypress config: `frontend/cypress.config.ts`
