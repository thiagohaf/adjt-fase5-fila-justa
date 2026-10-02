# Testes E2E - ConfirmaSUS

Suite de testes end-to-end do ConfirmaSUS cobrindo o fluxo completo de agendamentos, confirmação de presença, sugestão de repasse e auditoria.

## Estrutura

```
cypress/
├── e2e/
│   ├── fluxo-completo.cy.ts        # Fluxo integrado (UJ-1 a UJ-6)
│   └── auditoria-detalhes.cy.ts    # Testes específicos FE-6 (auditoria)
├── support/
│   ├── commands.ts                  # Comandos customizados
│   └── e2e.ts                       # Setup de suporte
└── cypress.config.ts                # Configuração do Cypress
```

## Como Executar

### Prerequisitos

1. **Backend rodando** em `localhost:8080`
   ```bash
   docker-compose up -d
   ```

2. **Frontend rodando** em `localhost:3000` (em outra janela)
   ```bash
   cd frontend
   npm run dev
   ```

### Rodar Testes

#### Modo interativo (com UI do Cypress)
```bash
npm run e2e:open
```

#### Modo headless (CI/CD)
```bash
npm run e2e                    # Roda todos os testes
npm run e2e:run-fluxo         # Apenas fluxo completo
npm run e2e:run-auditoria     # Apenas testes de auditoria
```

## Fluxo de Testes

### `fluxo-completo.cy.ts` — UJ-1 a UJ-6

Cobre o fluxo integrado completo:

1. **UJ-1: Login**
   - ✓ Login com credenciais válidas
   - ✓ Erro com credenciais inválidas

2. **UJ-2/UJ-3: Dashboard + Countdown**
   - ✓ Listar agendamentos
   - ✓ Navegar para confirmação
   - ✓ Exibir contador regressivo

3. **UJ-4: Confirmação**
   - ✓ Exibir dados do agendamento
   - ✓ Confirmar presença
   - ✓ Recusar presença

4. **UJ-5: Repasse**
   - ✓ Exibir sugestão de repasse
   - ✓ Navegar para página de repasse
   - ✓ Confirmar repasse
   - ✓ Recusar repasse

5. **UJ-6: Auditoria**
   - ✓ Exibir página de auditoria
   - ✓ Listar eventos com tipos e datas
   - ✓ Filtrar por tipo
   - ✓ Exibir motivo quando presente

6. **Integração Ponta-a-Ponta**
   - ✓ Fluxo completo: login → agendamento → confirmação → auditoria
   - ✓ Validar transições de estado

### `auditoria-detalhes.cy.ts` — FE-6 Detalhado

Testes específicos da interface de auditoria:

- **Exibição de Eventos**
  - ✓ 9 tipos de evento com badges corretos
  - ✓ Datas formatadas em pt-BR
  - ✓ Motivo em box destacado

- **Filtro por Tipo**
  - ✓ Listar tipos com contagem dinâmica
  - ✓ Filtrar eventos
  - ✓ Limpar filtro

- **Estados**
  - ✓ Loading (spinner)
  - ✓ Vazio (sem eventos)
  - ✓ Erro (falha requisição)

- **Responsivo**
  - ✓ Layout desktop (md+)
  - ✓ Layout mobile

- **Acessibilidade**
  - ✓ Labels acessíveis
  - ✓ Semântica HTML

## Credenciais de Teste

```
Username: regulador
Password: regulador#2026
```

## Seletores de Teste (data-testid)

Use `data-testid` para elementos críticos:

```typescript
// Agendamentos
[data-testid="agendamento-item"]
[data-testid="agendamento-status"]

// Eventos de auditoria
[data-testid="evento-item"]
[data-testid="evento-badge"]
[data-testid="evento-data"]
[data-testid="evento-motivo"]

// Filtros
[data-testid="filtro-tipo-opcao"]
[data-testid="filtro-label"]
[data-testid="filtro-count"]

// Estados
[data-testid="estado-loading"]
[data-testid="estado-vazio"]
[data-testid="estado-erro"]
[data-testid="btn-retry"]

// Layout
[data-testid="container-filtros"]
[data-testid="lista-eventos"]
```

## Comandos Customizados

```typescript
// Fazer login
cy.login('regulador', 'regulador#2026')

// Logout
cy.logout()

// Navegar para agendamento
cy.navigateToAgendamento()

// Confirmar presença
cy.confirmPresenca()

// Recusar presença
cy.recusarPresenca()
```

## Boas Práticas

1. **Selectors**: Prefira `data-testid` > `[aria-label]` > class selectors
2. **Waits**: Use `{ timeout: 10000 }` para operações assíncronas
3. **Limpeza**: Logout após cada teste (feito via afterEach)
4. **Dados**: Use credenciais de teste válidas (não hardcode em testes)

## CI/CD

Integrar com GitHub Actions:

```yaml
- name: Run E2E Tests
  run: |
    npm ci
    npm run build
    npm run e2e -- --spec 'cypress/e2e/**/*.cy.ts'
```

## Troubleshooting

| Problema | Solução |
|----------|---------|
| Testes falham com timeout | Verificar se frontend + backend estão rodando |
| `Cannot find module 'cypress'` | Rodar `npm install` no frontend |
| Elemento não encontrado | Verificar `data-testid` nos componentes React |
| Login falha | Validar credenciais e se auth-service está rodando |

## Próximos Passos

- [ ] Adicionar testes de performance (carregamento, resposta)
- [ ] Adicionar testes de idempotência
- [ ] Adicionar testes de resiliência (retry, fallback)
- [ ] Integrar com Cypress Dashboard para relatórios
