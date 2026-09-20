# Handoff — Próxima Sessão (2026-09-20)

## Resumo da Sessão

**O que foi entregue:** Retrospectiva completa do Epic 1 (Janelavelação — ConfirmaSUS)
- **Retro document:** `_bmad-output/implementation-artifacts/epic-1-retro-2026-09-20.md`
- **Status:** `accepted-with-open-items` (3 achados críticos, bloqueantes)
- **Sprint-status:** Atualizado com 3 action items `open`

## Estado Atual

- **Branch:** `develop` (sincronizada com `origin/develop`)
- **Working tree:** Limpa
- **Commit mais recente:** 2d07569 (merge #53 Story 1.5)
- **Data:** 2026-09-20 00:24 UTC

## 🚨 Achados Críticos (Bloqueantes)

### 1. @Transactional em Método Privado
- **Arquivo:** `ExpirarJanelaDeConfirmacao.java:99` + `AbrirJanelaDeConfirmacao.java:82`
- **Problema:** Spring AOP proxy não intercepta métodos privados → `@Transactional` é ignorado
- **Impacto:** Falha ao salvar evento NÃO causa rollback; Agendamento fica em LIBERADO sem eventos (estado inválido)
- **Fixo:** Mover `@Transactional` para método público ou usar `TransactionTemplate`

### 2. Escopo de Rollback Incorreto (Batch)
- **Arquivo:** `AbrirJanelaDeConfirmacao.java:82` (toda transação é lote único)
- **Problema:** Falha no item 5 → Spring marca rollback global → itens 1-4 são revertidos mesmo já processados
- **Impacto:** Perde-se trabalho dos itens anteriores; viola idempotência
- **Fixo:** `PROPAGATION_REQUIRES_NEW` em cada `processar()` → cada item é transação independente

### 3. Null Checks Silenciosos
- **Arquivo:** `ExpirarJanelaDeConfirmacao.java:102-121`
- **Problema:** Se `recursoId`/`dataHoraAgendamento` são null, método retorna silenciosamente (sem exceção)
- **Impacto:** Evento nunca é criado; Agendamento fica perdido (liberado mas sem notificação de repasse)
- **Fixo:** Lançar exceção ou registrar em DLQ; nunca retornar silenciosamente de falha de integridade

## Próximos Passos Recomendados

### Fase Atual: BLOQUEANTE

1. **Corrigir 3 achados críticos** (primeira prioridade)
   - Story: Nova (não numerada) ou cherry-pick em Story 1.6 (hotfix)
   - Escopo: 3 correções de transação em `agendamento-confirmacao-service`
   - Testes: Adicionar `ExpirarJanelaDeConfirmacaoTransactionRollbackTest` (verificar rollback ao falhar `salvar()`)
   - Testes: Adicionar `AbrirJanelaDeConfirmacaoBatchPartialFailureTest` (verificar item 5 falha, itens 1-4 ficam)

2. **Code review das correções** (verificação-gap)
   - Usar `bmad-review` ou revisão manual focando em:
     - `@Transactional(propagation = PROPAGATION_REQUIRES_NEW)` está correto
     - Nenhum novo `try-catch` derruba a exceção
     - Null checks agora lançam exceção (não silent return)

3. **Merge para `develop`** com novo PR (não force-push)

### Fase Seguinte: EPIC 2

Somente APÓS achados 1-3 serem corrigidos/testados:
- **Story 2.1:** Registrar Catálogo de Recurso + Entrada na Lista de Espera (FIFO)
- **Padrão:** Mesma arquitetura Clean Architecture + CQRS + outbox + escrita condicional

## Deferred Items (Epic 1)

Não há "15 deferred items" formais — a retro identificou apenas os 3 achados críticos. Outros ajustes (cadência de pollers, observabilidade, integração com auditoria) são adiados para Epic 2/3 e não bloqueiam Epic 1.

## Observações

- **Padrão validado:** Pollers com escrita condicional (`UPDATE ... WHERE status = esperado`) funcionam bem; o problema é apenas a orquestração transacional em Spring.
- **Risco descoberto:** Se o mesmo padrão (batch com try-catch) for replicado para Story 2.1+, os mesmos bugs aparecem. **Fixá-lo agora evita retrabalho em 3+ stories.**
- **Testes:** Suíte existente (7 testes) passa, mas não exercita falha de persistência. Os novos testes a adicionar cobrem esse gap.

## Prompt para Próxima Sessão

```
Continue a partir de `develop`. 

**Objetivo:** Corrigir os 3 achados críticos da retro do Epic 1 (transação privada, rollback de batch, null checks silenciosos).

**Comece por:** Story nova (ou 1.6 hotfix) de fixes transacionais em `agendamento-confirmacao-service`. 
1. Implement fixes (move @Transactional, add PROPAGATION_REQUIRES_NEW, throw exception on null)
2. Add tests (rollback verification, batch partial failure)
3. Code review + merge

**Depois:** Volta para Epic 1 retro finalizando, libera para Epic 2.
```

