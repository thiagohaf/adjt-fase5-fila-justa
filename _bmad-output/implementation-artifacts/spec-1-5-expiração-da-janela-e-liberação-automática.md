---
title: 'Expiração da Janela e Liberação Automática'
type: 'feature'
created: '2026-09-19'
status: 'done'
review_loop_iteration: 1
baseline_commit: '2e5e9518aa7106c46e1010a900c8f77ee015925e'
context: []
---

<!-- Target: 900–1300 tokens. Above 1600 = high risk of context rot.
     Never over-specify "how" — use boundaries + examples instead.
     Cohesive cross-layer stories (DB+BE+UI) stay in ONE file.
     IMPORTANT: Remove all HTML comments when filling this template. -->

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Um Agendamento em `AGUARDANDO_CONFIRMACAO` cujo prazo de expiração já passou fica preso indefinidamente sem resposta do Paciente — a vaga nunca é liberada e o repasse para outro candidato (Epic 2) jamais ocorre. O domínio carece de um mecanismo automático de transição para Não Confirmado, complementando a Recusa manual de Story 1.4.

**Approach:** Novo poller `@Scheduled` em `agendamento-confirmacao-service` (classe `ExpirarJanelaDeConfirmacao`), análogo a `AbrirJanelaDeConfirmacao` (Story 1.2), que seleciona Agendamentos com `status = AGUARDANDO_CONFIRMACAO` e `janelaExpiraEm <= now()`, transiciona via escrita condicional `UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'` para `LIBERADO` com `motivoLiberacao = NAO_CONFIRMADO`, e publica dois eventos no outbox na mesma transação: `AgendamentoNaoConfirmado` + `VagaLiberada`.

## Boundaries & Constraints

**Always:** Transição via `UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'` (AD-5). Seleção via `janelaExpiraEm <= now()` sincronizada com Clock injetado (mesma prática de `AbrirJanelaDeConfirmacao`). Poller com `@Scheduled` fixedDelay + timeout e `@Transactional` no método — nunca leitura-depois-escrita sem guarda. Se a escrita condicional afeta 0 linhas (já foi expirado por outra instância ou de outro poller), retorna silenciosamente sem novo evento (idempotência via design, como em Story 1.2). Dois eventos (`AgendamentoNaoConfirmado` + `VagaLiberada`) publicados no outbox na mesma transação (AD-3/AD-4) — **falha ao salvar qualquer um dos dois eventos causa rollback de TODA a transação** (UPDATE + ambos eventos). Cada loop do poller processa um lote de Agendamentos com SKIP LOCKED para garantir paralelismo sob múltiplas tasks ECS sem lock distribuído explícito. Exception handling: falha ao ler pendentes é catchada (não propaga), mas falha ao salvar evento propaga para rollback transacional. Logging estruturado: sucesso (Agendamento expirado com dois eventos gravados), falha (erro com detalhes). Null checks em todos os campos antes de gravar no outbox (recursoId, dataHoraAgendamento, nunca null).

**Ask First:** Cadência exata do poller (fixedDelay em milissegundos) — `[ASSUMPTION]` reutiliza a mesma `filajusta.agendamento.abertura-janela.poll-interval-ms` ou declara nova propriedade `filajusta.agendamento.expiracao-janela.poll-interval-ms`, ambas com default 5000ms. Timeout de execução — `[ASSUMPTION]` 30 segundos (boas práticas de mercado: banco não deve travar por mais de 30s em SELECT/UPDATE simples).

**Never:** Não criar nova migration — coluna `janelaExpiraEm` já existe (adicionada em Story 1.2, v1 schema); só será preenchida em runtime pela `RegistrarAgendamento` (Story 1.2). Não publicar apenas um evento (sempre dois: Não Confirmado + Vaga Liberada). Não usar polling direto ao banco sem SKIP LOCKED. Não tratar `AgendamentoNaoEncontradoException` — a seleção já garante existência. Não adicionar RBAC ou autenticação no poller (é agendado, não exposto via HTTP).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Expiração válida | `AGUARDANDO_CONFIRMACAO` + `janelaExpiraEm <= now()` | Transiciona para `LIBERADO/NAO_CONFIRMADO`, `AgendamentoNaoConfirmado` + `VagaLiberada` publicados no outbox | N/A |
| Já expirado por outra instância | Poller encontra 0 linhas afetadas pela UPDATE | Relê estado (já `LIBERADO/NAO_CONFIRMADO`), retorna silenciosamente | Idempotência por escrita condicional |
| Reprocessamento idêntico | Mesma execução do poller dentro da mesma cadência | Se `janelaExpiraEm` ainda atende a condição, tenta UPDATE novamente; se afeta 0 linhas, silenciosamente idempotente | SKIP LOCKED + FOR UPDATE SKIP LOCKED na leitura |
| Janela ainda não expirada | `janelaExpiraEm > now()` | Agendamento não é selecionado para processamento | Query filtra por `janelaExpiraEm <= now()` |
| Concorrência: dois pollers | Duas tasks ECS executando poller simultaneamente | Apenas uma obtém lock de linha via SKIP LOCKED, transiciona; outra não seleciona a mesma linha | FOR UPDATE SKIP LOCKED na leitura |
| Confirmado (venceu contra expiração) | `CONFIRMADO` | Agendamento não é selecionado (status ≠ `AGUARDANDO_CONFIRMACAO`) | Query filtra por status exato |
| Recusado manualmente (Story 1.4) | `LIBERADO/RECUSA` antes da expiração automática | Agendamento não é selecionado (status ≠ `AGUARDANDO_CONFIRMACAO`) | Query filtra por status exato |
| Falha ao gravar 1º evento | Excecao durante `eventoOutboxRepositorio.salvar(novoEventoNaoConfirmado)` | Rollback de UPDATE + evento 1; Agendamento permanece em `AGUARDANDO_CONFIRMACAO`; log de erro; retenta na próxima execução | @Transactional rollback automático, exceção propaga |
| Falha ao gravar 2º evento | Excecao durante `eventoOutboxRepositorio.salvar(novoEventoVagaLiberada)` após 1º suceder | Rollback de UPDATE + evento 1 + evento 2 (atomicidade total); Agendamento permanece em `AGUARDANDO_CONFIRMACAO`; log de erro; retenta na próxima execução | @Transactional rollback automático, exceção propaga |
| Poller falha durante seleção | Excecao em `buscarPendentesExpiracaoJanela()` | Catch no poller, log de erro, continua na próxima execução | Nunca interrompe a app |

</frozen-after-approval>

## Code Map

- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/AbrirJanelaDeConfirmacao.java:1-121` — padrão estrutural: @Scheduled + @Transactional, buscarPendentes() com SKIP LOCKED, loop com try-catch isolado, escrita condicional, publica evento(s) no outbox
- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RecusarPresenca.java:128-154` — padrão de múltiplos eventos (RecusaRegistrada + VagaLiberada) publicados na mesma transação
- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/Agendamento.java:32-76` — campo `janelaExpiraEm` já existe (Instant), getter já implementado; método `novo()` passa `janelaExpiraEm = null` (será preenchido em Story 1.2)
- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/StatusAgendamento.java` — enum com valores `AGUARDANDO_JANELA`, `AGUARDANDO_CONFIRMACAO`, `CONFIRMADO`, `LIBERADO`
- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/MotivoLiberacao.java` — enum com valores `{RECUSA, NAO_CONFIRMADO}`, adicionado em Story 1.4
- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/AgendamentoRepositorio.java` — interface com métodos já existentes: `atualizarStatusComMotivo(id, statusEsperado, novoStatus, motivo)` (Story 1.4), `buscarPorId(id)` (Story 1.1); adicionar `buscarPendentesExpiracaoJanela(loteTamanho): List<Agendamento>`
- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/infrastructure/persistence/AgendamentoRepositorioAdapter.java:23-77` — implementar query `buscarPendentesExpiracaoJanela` com SELECT ... FROM agendamentos WHERE status = 'AGUARDANDO_CONFIRMACAO' AND janela_expira_em <= now() ORDER BY id FOR UPDATE SKIP LOCKED LIMIT ?
- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/EventoOutbox.java` — reaproveitar construtor e factory para eventos `AgendamentoNaoConfirmado` (novo) e `VagaLiberada` (reutilizado de Story 1.4)
- `agendamento-confirmacao-service/src/test/java/.../AbrirJanelaDeConfirmacaoTest.java` — padrão para testes unitários do poller
- `agendamento-confirmacao-service/src/test/java/.../AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest.java` — padrão para testes de concorrência com Testcontainers (múltiplas threads, SKIP LOCKED)

## Tasks & Acceptance

**Execution:**
- [x] `agendamento-confirmacao-service/.../application/command/AgendamentoRepositorio.java` -- adicionar assinatura `buscarPendentesExpiracaoJanela(int loteTamanho): List<Agendamento>` -- contrato para seleção de Agendamentos pendentes de expiração
- [x] `agendamento-confirmacao-service/.../infrastructure/persistence/AgendamentoRepositorioAdapter.java` -- implementar `buscarPendentesExpiracaoJanela` com query SQL `SELECT ... WHERE status = 'AGUARDANDO_CONFIRMACAO' AND janela_expira_em <= now() ORDER BY id FOR UPDATE SKIP LOCKED LIMIT ?` -- garante seleção atômica e sem contenção entre múltiplos pollers
- [x] `agendamento-confirmacao-service/.../application/command/ExpirarJanelaDeConfirmacao.java` (novo) -- implementar poller `@Scheduled` análogo a `AbrirJanelaDeConfirmacao`: método `expirarJanelas()` com @Transactional, loop sobre `buscarPendentesExpiracaoJanela()`, try-catch isolado por item, `atualizarStatusComMotivo()` com motivo `NAO_CONFIRMADO`, publica dois eventos no outbox -- aplica padrão AD-5 (poller + escrita condicional + atomicidade)
- [x] `agendamento-confirmacao-service/.../domain/EventoOutbox.java` ou equivalente -- documentar ou reutilizar payload shapes para `AgendamentoNaoConfirmado` (novo) e `VagaLiberada` (existente) com `{agendamentoId, pacienteId, motivo}` e `{agendamentoId, recursoId, dataHoraAgendamento}` -- coerência com AD-7 (vocabulário compartilhado de `motivo` nos eventos)
- [x] `agendamento-confirmacao-service/src/test/java/.../application/command/ExpirarJanelaDeConfirmacaoTest.java` (novo) -- testes unitários: válida expiração, idempotência via 0 linhas afetadas, exceção durante busca, exception handling nunca propaga
- [x] `agendamento-confirmacao-service/src/test/java/.../application/command/ExpirarJanelaDeConfirmacaoConcurrencyIntegrationTest.java` (novo) -- Testcontainers: duas threads do poller disputam o mesmo Agendamento, apenas uma transiciona, outra retorna silenciosamente; validar SKIP LOCKED behavior
- [x] `agendamento-confirmacao-service/pom.xml` ou `application.yml` -- adicionar propriedade `filajusta.agendamento.expiracao-janela.poll-interval-ms=5000` (ou reutilizar a mesma de abertura); validar que @Scheduled lê a propriedade corretamente
- [x] `agendamento-confirmacao-service/.../AgendamentoConfirmacaoServiceApplication.java` -- garantir que `@EnableScheduling` está presente (já existe em AbrirJanelaDeConfirmacao)

**Acceptance Criteria:**
- Given um Agendamento em `AGUARDANDO_CONFIRMACAO` cuja `janelaExpiraEm` já é menor que a hora atual, when o poller `expirarJanelas()` é executado, then o Agendamento transiciona para `LIBERADO` com `motivoLiberacao = NAO_CONFIRMADO`, e os eventos `AgendamentoNaoConfirmado` + `VagaLiberada` são gravados no outbox dentro da mesma transação
- Given um Agendamento já transicionado para `LIBERADO/NAO_CONFIRMADO` e o poller tenta processar novamente, when a escrita condicional retorna 0 linhas, then o poller retorna silenciosamente sem novo evento (idempotência)
- Given duas instâncias do poller rodando concorrentemente sobre o mesmo Agendamento expirado, when ambas executam `buscarPendentesExpiracaoJanela()`, then apenas uma obtém lock via SKIP LOCKED, transiciona, e a outra nunca seleciona a mesma linha
- Given uma falha ao salvar eventos no outbox, when a transação é revertida, then o Agendamento permanece em `AGUARDANDO_CONFIRMACAO` (nunca fica em estado intermediário) e nenhum evento é publicado

## Spec Change Log

**Review Loop 1 — Verification Gap (falha no segundo evento):**
- **Finding:** Falha ao salvar segundo evento não causava rollback transacional; Agendamento transicionava mesmo com evento 2 órfão
- **Changed:** Clarificado em Boundaries que falha em QUALQUER um dos dois eventos causa rollback de UPDATE + ambos eventos. Exception handling: falha ao salvar propaga (não capturada isoladamente por evento)
- **Known-bad state avoided:** Agendamento nunca fica em estado intermediário (LIBERADO mas sem segundo evento); atomicidade garantida
- **KEEP:** Padrão de try-catch isolado por item MANTIDO apenas para leitura de pendentes (falha não deve derrubar app); removido de `registrarRecusa` (ambos eventos gravados fora de catch isolado)

**Review Loop 1 — Null Checks e Timeout (blind-hunter + edge-case-hunter):**
- **Finding:** Faltavam validações null em recursoId, dataHoraAgendamento; falta timeout em @Scheduled; falta logging de sucesso
- **Changed:** Adicionado null checks antes de gravar no outbox. Timeout @Scheduled = 30s (boas práticas). Logging de sucesso ao processar Agendamento
- **Known-bad state avoided:** NPE em payload JSON; poller travado indefinidamente se SELECT/UPDATE lento; sem observabilidade de throughput
- **KEEP:** Padrão de logging isolado por item (sucesso por Agendamento, erro com detalhe de qual falhou)

## Design Notes

**Padrão de poller com escrita condicional (AD-5):**

O poller `ExpirarJanelaDeConfirmacao` espelha `AbrirJanelaDeConfirmacao` (Story 1.2) em estrutura:

```java
@Scheduled(fixedDelayString = "${filajusta.agendamento.expiracao-janela.poll-interval-ms:5000}")
@Transactional
public void expirarJanelas() {
    List<Agendamento> pendentes;
    try {
        pendentes = agendamentoRepositorio.buscarPendentesExpiracaoJanela(loteTamanho);
    } catch (RuntimeException e) {
        log.error("Falha ao ler Agendamentos pendentes...", e);
        return;
    }
    
    for (Agendamento agendamento : pendentes) {
        try {
            processar(agendamento);
        } catch (RuntimeException e) {
            log.error("Falha ao processar expiração...", e);
        }
    }
}

private void processar(Agendamento agendamento) {
    boolean transicionado = agendamentoRepositorio.atualizarStatusComMotivo(
        agendamento.getId(), 
        StatusAgendamento.AGUARDANDO_CONFIRMACAO, 
        StatusAgendamento.LIBERADO,
        MotivoLiberacao.NAO_CONFIRMADO.name()
    );
    
    if (!transicionado) {
        log.warn("Agendamento {} já não estava mais em AGUARDANDO_CONFIRMACAO...", agendamento.getId());
        return;
    }
    
    eventoOutboxRepositorio.salvar(novoEventoNaoConfirmado(agendamento));
    eventoOutboxRepositorio.salvar(novoEventoVagaLiberada(agendamento));
}
```

**Diferença de Story 1.2:**
- Story 1.2 seleciona por `janelaAbreEm <= now()` + status `AGUARDANDO_JANELA`
- Story 1.5 seleciona por `janelaExpiraEm <= now()` + status `AGUARDANDO_CONFIRMACAO`
- Story 1.5 publica dois eventos (+ motivo `NAO_CONFIRMADO`)

## Verification

**Commands:**
- `mvn clean test -pl agendamento-confirmacao-service` -- todos os testes da service passam, incluindo testes unitários de `ExpirarJanelaDeConfirmacao`, concorrência (Testcontainers), e edge cases da I/O Matrix
- `mvn verify -pl agendamento-confirmacao-service` -- JaCoCo ≥90% de cobertura de linha em `com.filajusta.agendamento.domain.*` (ou conforme policy do projeto)
- `mvn -pl agendamento-confirmacao-service org.pitest:pitest-maven:mutationCoverage` -- PIT mutation score (sob demanda; sem threshold mínimo)
