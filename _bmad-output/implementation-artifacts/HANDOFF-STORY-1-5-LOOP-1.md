# Handoff — Story 1.5 Loop 1 (Gap Crítico de Atomicidade)

## Estado Atual
- **Branch:** `develop` (clean)
- **Spec:** `spec-1-5-expiração-da-janela-e-liberação-automática.md` — status `in-progress`, `review_loop_iteration: 1`
- **Implementação:** Concluída pelo subagent (90/90 testes passando)
- **Revisão:** Completa (blind-hunter ✅, edge-case-hunter ✅, verification-gap ❌ GAP CRÍTICO ENCONTRADO)

## Problema Crítico Encontrado

**Atomicidade de dois eventos quebrada AINDA:**

Try-catch em `ExpirarJanelaDeConfirmacao.expirarJanelas()` (linhas 87-97) captura RuntimeException sem relançar:

```java
for (Agendamento agendamento : pendentes) {
    try {
        processar(agendamento);  // ambos salvar() aqui, sem try isolado
    } catch (RuntimeException e) {
        log.error("Falha ao processar expiração...", e);  // captura SEM relançar
    }
}
```

**Consequência:** Spring @Transactional só faz rollback automático se exceção propaga FORA do método. Com catch sem rethrow, transação comita parcialmente (UPDATE + evento 1 persistem, evento 2 fica órfão).

**Violação:** Spec says "Rollback de toda a transação" mas não ocorre.

## 3 Opções de Resolução

### [1] Separar Transações por Item (RECOMENDADO ✓)
Mover `@Transactional` de `expirarJanelas()` para `processar()`:

```java
@Scheduled(...)
public void expirarJanelas() {
    List<Agendamento> pendentes = agendamentoRepositorio.buscarPendentesExpiracaoJanela(loteTamanho);
    for (Agendamento agendamento : pendentes) {
        try {
            processar(agendamento);  // cada item tem sua própria transação
        } catch (RuntimeException e) {
            log.error("Falha...", e);  // continua loop
        }
    }
}

@Transactional  // ← MOVE AQUI
private void processar(Agendamento agendamento) {
    boolean transicionado = atualizarStatusComMotivo(...);
    if (!transicionado) return;
    eventoOutboxRepositorio.salvar(novoEventoNaoConfirmado(...));
    eventoOutboxRepositorio.salvar(novoEventoVagaLiberada(...));  // falha aqui propaga, rollback de UPDATE + evento 1
}
```

**Vantagens:**
- Atomicidade por Agendamento (UPDATE + 2 eventos rollback juntos)
- Falha em um não trava o lote
- Padrão Spring consolidado
- Testes já validam (ExpirarJanelaDeConfirmacaoConcurrencyIntegrationTest)

**Desvantagem:** Se muitos agendamentos falham, poller fica lento (N transações em vez de 1). Aceitável para MVP.

### [2] Marcar Rollback Manualmente
```java
try {
    processar(agendamento);
} catch (RuntimeException e) {
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    log.error(..., e);
}
```
**Desvantagem:** Menos elegante, requer import de Spring Framework interno.

### [3] Remover Try-Catch
Deixar propagar. Falha em 1 agendamento = falha TODO o lote.
**Desvantagem:** Poller não resiliente; bate contra AD-5 ("nunca deixa escapar").

## Ação para Próxima Sessão

1. **Escolha opção [1], [2] ou [3]** — recomendo [1]
2. **Atualize spec** (seção Boundaries + Design Notes) documentando decisão
3. **Redespeche subagent** com correção:
   ```
   Mova @Transactional de expirarJanelas() para processar(). 
   Valide que falha no segundo evento causa rollback de UPDATE + evento 1.
   Rode mvn test -pl agendamento-confirmacao-service.
   ```
4. **Rode revisão novamente** (verification-gap) para validar que atomicidade foi resolvida
5. **Se passar:** Atualize spec status para `in-review` → aprovação final → status `done` → commit + PR

## Arquivos-Chave

- **Spec:** `_bmad-output/implementation-artifacts/spec-1-5-expiração-da-janela-e-liberação-automática.md`
- **Implementação:** `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/ExpirarJanelaDeConfirmacao.java`
- **Testes:** 
  - `ExpirarJanelaDeConfirmacaoTest.java`
  - `ExpirarJanelaDeConfirmacaoConcurrencyIntegrationTest.java`
- **Revisão findings:** Último resultado do verification-gap (acima neste handoff)

## Contexto Anterior

- Story 1.4 (Recusa Ativa) — completa e mergeada (PR #52)
- Story 1.5 padrão espelha Story 1.2 (AbrirJanelaDeConfirmacao)
- Padrão AD-5 (poller com escrita condicional, outbox, eventos)

## Próximas Sessões Após Story 1.5 Done

1. **Epic 1 Retro** — avaliar 15 deferred items
2. **Story 2.1** (Epic 2) — Registrar Catálogo de Recurso + Lista de Espera
3. **Recomendação:** `/compact` antes de Story 2.1 (contexto está grande)
