# Frontend Epics & Stories — UJ-1 a UJ-4

## Epic Frontend: Implementação de User Journeys no Frontend React

**Status:** backlog  
**Descrição:** Decompor e implementar as 4 User Journeys (UJ-1 a UJ-4) no frontend React + TypeScript, integrando com endpoints do backend completo (Epics 1-5).

**Stories:** 4 (confirmacao, repasse, auditoria, dashboard-polling)

---

## Story FE-1: UJ-1 Confirmação de Presença — Exibição de Agendamento

**Status:** ready-for-dev  
**Prioridade:** P0  
**Estimativa:** 900-1200 tokens

### Objetivo
Permitir que o Paciente visualize detalhes do agendamento (data, horário, recurso, local) antes de confirmar ou recusar presença, com suporte a autenticação JWT e erro handling.

### Requisitos Funcionais
1. **GET `/v1/agendamentos/{agendamentoId}`** → exibir:
   - ID, data/hora, paciente (nome, CPF mascarado), recurso, local
   - Status atual (Pendente Confirmação, Confirmado, Recusado, Não Confirmado, Liberado)
2. **Exibir estado de carregamento** enquanto fetch roda
3. **Exibir erro amigável** se agendamento não encontrado (404) ou servidor (5xx)
4. **Link de volta** para dashboard

### Requisitos Técnicos
- **Página:** `src/pages/ConfirmacaoPage.tsx`
- **Hook:** `useQuery` (TanStack Query) para fetch com retry automático
- **URL:** `/confirmacao/:agendamentoId`
- **Autenticação:** Bearer token via interceptor Axios (já existente)
- **Tipo:** `Agendamento` (definido em `src/types/index.ts`)

### Critério de Aceitação
- [ ] Componente renderiza detalhes do agendamento com layout legível
- [ ] Loading spinner exibido durante fetch
- [ ] Erro exibido em toast/alert se GET falhar
- [ ] Redireção para login se 401 (Unauthorized)
- [ ] Teste unitário: mock useQuery, validar rendering

### Saída Esperada
- `src/pages/ConfirmacaoPage.tsx` (skeleton com integração)
- Tipos atualizados em `src/types/index.ts` se necessário
- Teste básico em `src/pages/__tests__/ConfirmacaoPage.test.tsx`

---

## Story FE-2: UJ-1 Confirmação de Presença — Janela de Confirmação com Countdown

**Status:** backlog  
**Prioridade:** P0  
**Estimativa:** 900-1600 tokens

### Objetivo
Implementar countdown visual que mostra tempo restante da Janela de Confirmação, expiração automática e visualização clara do prazo ao paciente.

### Requisitos Funcionais
1. **Exibir countdown** em mm:ss (ex: "05:30") → decrementar a cada segundo
2. **Calcular expiração** baseado em `dataHoraLimite` (vindo do GET /agendamentos/{id})
3. **Alertar quando tempo < 1 minuto** → cor vermelha ou animação
4. **Exibir msg se já expirou** → "Janela expirada, não é possível confirmar"
5. **Pausar se usuário não está na página** (visibility API)

### Requisitos Técnicos
- **Custom hook:** `useCountdown(expiryTime: Date)` → retorna `{minutes, seconds, isExpired}`
- **Componente:** `<CountdownDisplay isExpired={} minutes={} seconds={} />`
- **CSS:** Tailwind (transition, color-change, animação pulse se urgente)
- **Lógica:** setInterval com limpeza ao desmontar

### Critério de Aceitação
- [ ] Countdown decrementa corretamente a cada segundo
- [ ] Mudança visual (cor/animação) quando < 1 minuto
- [ ] Teste: mock Date.now(), verificar decremento
- [ ] Teste: verificar expiração detectada corretamente

### Saída Esperada
- `src/hooks/useCountdown.ts` (custom hook)
- `src/components/CountdownDisplay.tsx` (componente visual)
- Integrado em `src/pages/ConfirmacaoPage.tsx`

---

## Story FE-3: UJ-1 Confirmação de Presença — Confirmar/Recusar (Actions)

**Status:** backlog  
**Prioridade:** P0  
**Estimativa:** 1000-1400 tokens

### Objetivo
Permitir que paciente submeta confirmação ou recusa de presença via formulário com feedback visual e redireção pós-sucesso.

### Requisitos Funcionais
1. **Botões:** "Confirmar Presença" + "Recusar Presença"
2. **Desabilitar botões** se countdown expirou ou já está carregando
3. **POST `/v1/agendamentos/{agendamentoId}/confirmacao`** → sucesso:
   - Exibir toast "Confirmação registrada com sucesso"
   - Redirecionar para `/dashboard` após 2s
4. **POST `/v1/agendamentos/{agendamentoId}/recusa`** → sucesso:
   - Exibir toast "Recusa registrada com sucesso"
   - Redirecionar para `/dashboard` após 2s
5. **Erro handling:**
   - 400 Bad Request → mostrar msg customizada
   - 409 Conflict → "Já foi confirmado/recusado"
   - 5xx → toast "Erro ao processar, tente novamente"
6. **Spinner** no botão durante requisição

### Requisitos Técnicos
- **useMutation** (TanStack Query) para POST
- **Componentes:** `<ConfirmacaoButton>` + `<RecusaButton>`
- **Validação:** Desabilitar se `isExpired || isLoading`
- **Redirect:** `useNavigate()` após sucesso

### Critério de Aceitação
- [ ] POST feito com payload correto
- [ ] Toast exibido em sucesso/erro
- [ ] Redireção funciona após sucesso
- [ ] Botões desabilitados corretamente
- [ ] Teste: mock useMutation, simular sucesso/erro

### Saída Esperada
- `src/components/ConfirmacaoButton.tsx`
- `src/components/RecusaButton.tsx`
- Integrado em `ConfirmacaoPage.tsx`
- Testes em `__tests__/ConfirmacaoPage.test.tsx`

---

## Story FE-4: UJ-3 Repasse de Vaga — Exibição da Sugestão

**Status:** backlog  
**Prioridade:** P1  
**Estimativa:** 900-1200 tokens

### Objetivo
Permitir que Gestor de Agenda visualize a sugestão de repasse automático (paciente recomendado da fila de espera).

### Requisitos Funcionais
1. **GET `/v1/recursos/{recursoId}/sugestao`** → exibir:
   - Paciente sugerido: nome, CPF mascarado, prioridade/score
   - Recurso: tipo, local, data/hora
   - Motivo: "Próximo na fila de espera" + timestamp de sugestão
2. **Validação:** Se nenhuma sugestão disponível → exibir "Nenhuma sugestão pendente"
3. **Exibir loading + erro handling** similar a FE-1
4. **Link de volta** para dashboard

### Requisitos Técnicos
- **Página:** `src/pages/RepassePage.tsx`
- **Hook:** `useQuery` para fetch
- **URL:** `/repasse/:recursoId`
- **Tipo:** `SugestaoRepasse` (atualizar em `src/types/index.ts`)

### Critério de Aceitação
- [ ] Sugestão exibida com layout claro
- [ ] Mensagem de "nenhuma sugestão" tratada
- [ ] Loading + erro funcionam
- [ ] Teste: mock useQuery

### Saída Esperada
- `src/pages/RepassePage.tsx` (skeleton)
- `src/types/index.ts` atualizado com `SugestaoRepasse`
- Teste em `__tests__/RepassePage.test.tsx`

---

## Story FE-5: UJ-3 Repasse de Vaga — Confirmar/Recusar Sugestão

**Status:** backlog  
**Prioridade:** P1  
**Estimativa:** 1000-1400 tokens

### Objetivo
Permitir que Gestor de Agenda confirme ou recuse a sugestão de repasse, registrando a decisão no sistema.

### Requisitos Funcionais
1. **Botões:** "Confirmar Repasse" + "Recusar Sugestão"
2. **POST `/v1/recursos/{recursoId}/alocacoes`** (confirmar):
   - Body: `{pacienteId, sugestaoId}` (se necessário)
   - Sucesso: toast + redireção para `/dashboard`
3. **POST `/v1/recursos/{recursoId}/alocacoes/recusa`** (recusar):
   - Body: motivo opcional
   - Sucesso: toast + redireção
4. **Desabilitar botões** durante loading
5. **Erro handling** para 400/409/5xx

### Requisitos Técnicos
- **useMutation** para POST
- **Componentes:** `<ConfirmarRepasseButton>` + `<RecusarSugestaoButton>`
- **Redirect:** Após sucesso

### Critério de Aceitação
- [ ] POST feito com payload correto
- [ ] Toast + redireção funcionam
- [ ] Botões desabilitados durante loading
- [ ] Erro tratado adequadamente
- [ ] Teste: mock useMutation

### Saída Esperada
- `src/components/ConfirmarRepasseButton.tsx`
- `src/components/RecusarSugestaoButton.tsx`
- Integrado em `RepassePage.tsx`
- Testes

---

## Story FE-6: UJ-4 Auditoria — Timeline Auditável

**Status:** backlog  
**Prioridade:** P1  
**Estimativa:** 1200-1600 tokens

### Objetivo
Permitir que Auditor consulte histórico cronológico (timeline) de um agendamento com todos os eventos registrados (notificação, confirmação, liberação, sugestão, decisão).

### Requisitos Funcionais
1. **GET `/v1/auditoria?agendamentoId={id}` ou `/v1/auditoria/{agendamentoId}`**:
   - Retorna lista de eventos em ordem cronológica
   - Eventos: tipo (notificação, confirmação, liberação, sugestão, decisão, etc), timestamp, usuário, motivo
2. **Exibir timeline vertical** com:
   - Data/hora de cada evento
   - Tipo de evento (com ícone/cor)
   - Descrição/motivo
   - Usuário responsável (se aplicável)
3. **Filtros opcionais:**
   - Por tipo de evento
   - Por intervalo de data
4. **Validação:** Se sem eventos → "Nenhum registro de auditoria"
5. **Loading + erro handling**

### Requisitos Técnicos
- **Página:** `src/pages/AuditoriaPage.tsx`
- **Componente:** `<TimelineEvent>` + `<AuditoriaTimeline>`
- **Hook:** `useQuery` para fetch
- **URL:** `/auditoria/:agendamentoId`
- **Tipo:** `AuditoriaEvento[]` (definir em `src/types/index.ts`)

### Critério de Aceitação
- [ ] Timeline renderiza eventos em ordem cronológica
- [ ] Estilos (cor/ícone) diferem por tipo de evento
- [ ] Filtros funcionam (se implementados)
- [ ] Mensagem "sem eventos" tratada
- [ ] Loading + erro funcionam
- [ ] Teste: mock useQuery com vários eventos

### Saída Esperada
- `src/pages/AuditoriaPage.tsx`
- `src/components/TimelineEvent.tsx`
- `src/components/AuditoriaTimeline.tsx`
- `src/types/index.ts` atualizado com `AuditoriaEvento`
- Testes

---

## Story FE-7: Dashboard com Polling (UJ-2 — Sistema Automático)

**Status:** backlog  
**Prioridade:** P2  
**Estimativa:** 900-1200 tokens

### Objetivo
Implementar polling no Dashboard para detectar mudanças de status de agendamentos e exibir notificações quando vagas são liberadas.

### Requisitos Funcionais
1. **GET `/v1/agendamentos?status=pendente_confirmacao,liberado`** (polling a cada 5-10s):
   - Comparar com estado anterior
   - Se status mudou para "Liberado" → exibir notificação (toast ou banner)
2. **Exibir badge/indicador** para agendamentos em estado "Liberado"
3. **Notificação opcional** (se browser notification API disponível)
4. **Pausar polling** quando usuário não está na página

### Requisitos Técnicos
- **useQuery com refetchInterval** ou `setInterval` customizado
- **Componente:** notificação toast (library ou custom)
- **Visibility API:** pausar poll quando tab não está ativa

### Critério de Aceitação
- [ ] Polling roda a cada N segundos
- [ ] Mudança de status detectada
- [ ] Notificação exibida corretamente
- [ ] Polling pausa quando tab fica inativa
- [ ] Teste: mock setInterval, simular resposta

### Saída Esperada
- `src/hooks/usePollingAgendamentos.ts`
- Integrado em `DashboardPage.tsx`
- Testes

---

## Story FE-8: Styling & Polish (Tailwind)

**Status:** backlog  
**Prioridade:** P2  
**Estimativa:** 800-1200 tokens

### Objetivo
Aplicar estilos Tailwind CSS consistentes em todas as páginas (UJ-1 a UJ-4) e garantir responsividade mobile.

### Requisitos Funcionais
1. **Palette consistente:**
   - Primary: cores do SUS (verde/azul)
   - Status colors: verde (confirmado), vermelho (recusado), amarelo (pendente), cinza (expirado)
2. **Componentes:**
   - Buttons: primary, secondary, danger (hover, disabled, loading states)
   - Cards: agendamento, sugestão, evento auditável
   - Forms: input, select (com validação visual)
   - Modal/Toast: feedback visual
3. **Responsive:**
   - Mobile-first (< 640px)
   - Tablet (640-1024px)
   - Desktop (> 1024px)
4. **Acessibilidade:**
   - Contraste WCAG AA
   - Focus states visíveis
   - Labels acessíveis

### Requisitos Técnicos
- **Tailwind config:** customizar cores, fonts
- **Componentes estilizados:** Headless UI (opcional) ou classes diretas
- **Dark mode:** suporte opcional (prefers-color-scheme)

### Critério de Aceitação
- [ ] Todas as páginas com estilos consistentes
- [ ] Responsive em móvel/tablet/desktop
- [ ] Contraste e acessibilidade OK
- [ ] Teste visual: screenshot comparison (opcional)

### Saída Esperada
- `tailwind.config.ts` atualizado
- `src/index.css` com custom directives
- Todos os componentes estilizados

---

## Resumo de Stories por UJ

| Story | UJ | Título | Status | Tokens |
|-------|----|----|--------|--------|
| FE-1 | 1 | Exibição de Agendamento | ready-for-dev | 900-1200 |
| FE-2 | 1 | Countdown | backlog | 900-1600 |
| FE-3 | 1 | Confirmar/Recusar | backlog | 1000-1400 |
| FE-4 | 3 | Exibição Sugestão | backlog | 900-1200 |
| FE-5 | 3 | Confirmar/Recusar Sugestão | backlog | 1000-1400 |
| FE-6 | 4 | Timeline Auditável | backlog | 1200-1600 |
| FE-7 | 2 | Dashboard Polling | backlog | 900-1200 |
| FE-8 | - | Styling & Polish | backlog | 800-1200 |

**Total estimado:** 7.700 a 11.200 tokens para 8 stories

---

## Próximas Ações

1. ✅ **Documento criado** → decomposição concluída
2. ⏳ **Story FE-1:** Criar branch `feature/fe-1-confirmacao-exibicao` e começar implementação
3. ⏳ **Code review:** Após cada story
4. ⏳ **Merge:** Para `develop` após review OK
5. ⏳ **Retro:** Após Epic Frontend completo

---

**Criado:** 2026-09-27  
**Versão:** 1.0
