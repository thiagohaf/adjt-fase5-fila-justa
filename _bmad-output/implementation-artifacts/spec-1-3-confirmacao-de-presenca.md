---
title: 'Confirmação de Presença'
type: 'feature'
created: '2026-09-18'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'c32f5185ba7f06b934adf4d98a35fe099d9b0e18'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Um Agendamento em `AGUARDANDO_CONFIRMACAO` não tem meio de o Paciente confirmar presença via API — a vaga nunca chega ao estado terminal `CONFIRMADO` (FR-4).

**Approach:** Novo comando `ConfirmarPresenca` em `agendamento-confirmacao-service`, exposto via `POST /v1/agendamentos/{id}/confirmacao`, que transiciona `AGUARDANDO_CONFIRMACAO`→`CONFIRMADO` via escrita condicional (reaproveitando `atualizarStatusSeAtual`) e publica `ConfirmacaoRegistrada` via outbox na mesma transação, seguindo o molde de `AbrirJanelaDeConfirmacao` (Story 1.2).

## Boundaries & Constraints

**Always:** Transição via `UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'` (AD-4), nunca leitura-depois-escrita sem guarda. Confirmação duplicada do mesmo `agendamentoId` (já `CONFIRMADO`) retorna sucesso silencioso, sem novo evento. Qualquer outro estado perdedor (`AGUARDANDO_JANELA`, `LIBERADO`) retorna `409` via `ProblemDetail` (RFC 7807), molde de `RecursosExceptionHandler.handleRecursoJaAlocado` em `matching-alocacao-service`. Evento `ConfirmacaoRegistrada` publicado no outbox (`EventoOutboxRepositorio`) na mesma transação do comando, payload incluindo `agendamentoId` (usado como `MessageGroupId` pelo relay). Reaproveitar `AgendamentoRepositorio.atualizarStatusSeAtual` e `EventoOutboxRepositorio.salvar` sem alteração de assinatura.

**Ask First:** Nenhuma decisão de arquitetura pendente — segue precedente direto de AD-4/AD-5 e da Story 1.2.

**Never:** Não reativar `Agendamento.abrirJanela()` como padrão — a transição real continua no adapter via UPDATE condicional. Não criar migration nova (enum `CONFIRMADO` e tabela `eventos_outbox` já existem). Não implementar Recusa (Story 1.4) nem tocar `motivoLiberacao` (não existe ainda). Não adicionar RBAC ou validação de identidade do Paciente além do `agendamentoId` (fora do escopo do PRD).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Confirmação válida | Agendamento `AGUARDANDO_CONFIRMACAO` | `200`, status vira `CONFIRMADO`, evento `ConfirmacaoRegistrada` gravado no outbox na mesma transação | N/A |
| Confirmação duplicada | Agendamento já `CONFIRMADO` pela mesma chamada anterior (mesmo `agendamentoId`) | `200` sucesso silencioso, sem novo registro no outbox | Releitura do estado atual confirma que já é `CONFIRMADO` antes de decidir |
| Janela ainda não aberta | Agendamento `AGUARDANDO_JANELA` | `409` Problem Details explicando "janela ainda não aberta" | Releitura após escrita condicional falhar (0 linhas) |
| Vaga já liberada | Agendamento `LIBERADO` | `409` Problem Details explicando "vaga já liberada" | Releitura após escrita condicional falhar (0 linhas) |
| `agendamentoId` inexistente | id não encontrado | `404` | `AgendamentoRepositorio.buscarPorId` retorna vazio |
| Corrida Confirmação vs. Recusa (preparação p/ Story 1.4) | Duas requisições concorrentes no mesmo `AGUARDANDO_CONFIRMACAO` | Escrita condicional garante só uma vence; perdedora relê e recebe `409` (ou sucesso silencioso se a vencedora foi a mesma confirmação) | Coberto por teste de concorrência Testcontainers, molde de `AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest` |

</frozen-after-approval>

## Code Map

- `agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/domain/Agendamento.java:75` -- `abrirJanela()` é o molde a copiar; adicionar `confirmar()` retornando cópia com `status = CONFIRMADO`.
- `agendamento-confirmacao-service/.../domain/StatusAgendamento.java:12-17` -- enum já contém `CONFIRMADO`; nenhuma alteração.
- `agendamento-confirmacao-service/.../domain/EventoOutbox.java:25-92` -- reaproveitar `comId(...)` para montar o evento `ConfirmacaoRegistrada`, sem alteração.
- `agendamento-confirmacao-service/.../application/command/AbrirJanelaDeConfirmacao.java:46-121` -- precedente estrutural direto: `@Transactional`, escrita condicional + outbox na mesma transação, `EVENT_TYPE` como constante, `correlationId = "agendamento-" + id`.
- `agendamento-confirmacao-service/.../application/command/AgendamentoRepositorio.java:24-45` -- porta; `atualizarStatusSeAtual(id, statusEsperado, novoStatus)` já genérica, reaproveitar sem alteração; **adicionar** `Optional<Agendamento> buscarPorId(Long id)` para a releitura de estado.
- `agendamento-confirmacao-service/.../infrastructure/persistence/AgendamentoRepositorioAdapter.java:23-77` -- implementar o novo `buscarPorId` delegando a `AgendamentoJpaRepository.findById`.
- `agendamento-confirmacao-service/.../infrastructure/persistence/AgendamentoJpaRepository.java:32-37` -- query JPQL condicional já genérica em `statusEsperado`/`novoStatus`; nenhuma alteração.
- `agendamento-confirmacao-service/.../infrastructure/persistence/EventoOutboxRepositorioAdapter.java:32-78` -- `salvar` reaproveitável sem alteração.
- `agendamento-confirmacao-service/.../infrastructure/relay/RelaySnsPublisherJob.java:63-220` -- reaproveitável sem alteração, desde que o payload inclua `agendamentoId`.
- `agendamento-confirmacao-service/.../infrastructure/web/AgendamentoController.java:18-34` -- adicionar endpoint `POST /v1/agendamentos/{id}/confirmacao` (único endpoint hoje é `POST /v1/agendamentos`).
- `agendamento-confirmacao-service/.../infrastructure/web/AgendamentoExceptionHandler.java:38-83` -- adicionar handler de `409` (gap: hoje só trata `422`); molde em `matching-alocacao-service/.../infrastructure/web/RecursosExceptionHandler.java:126-143` (`ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ...)`).
- `matching-alocacao-service/.../application/command/ConfirmarAlocacao.java:59-121` -- referência de exceção de domínio para conflito (`RecursoJaAlocadoException`).
- `agendamento-confirmacao-service/.../db/migration/V2__add_janela_e_outbox.sql` -- confirma: coluna `status VARCHAR(32)` sem CHECK, tabela `eventos_outbox` pronta; **nenhuma migration nova necessária**.
- `agendamento-confirmacao-service/src/test/java/.../application/command/AbrirJanelaDeConfirmacaoTest.java:51-139` -- molde de teste unitário (Mockito, `Clock.fixed`, `ArgumentCaptor<EventoOutbox>`).
- `agendamento-confirmacao-service/src/test/java/.../AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest.java:49-131` -- molde de teste de concorrência Testcontainers (N threads, `CountDownLatch`, assert via `JdbcTemplate`).
- `agendamento-confirmacao-service/src/test/java/.../infrastructure/web/AgendamentoControllerIntegrationTest.java:41-80` -- molde de teste HTTP end-to-end (`@SpringBootTest RANDOM_PORT`, Testcontainers, `outbox-relay.enabled=false`, `java.net.http.HttpClient`).

## Tasks & Acceptance

**Execution:**
- [x] `domain/Agendamento.java` -- adicionar `confirmar()` (cópia imutável com `status = CONFIRMADO`) -- espelha `abrirJanela()`
- [x] `application/command/AgendamentoRepositorio.java` -- adicionar `Optional<Agendamento> buscarPorId(Long id)` -- necessário para releitura de estado na regra de concorrência (AD-4)
- [x] `infrastructure/persistence/AgendamentoRepositorioAdapter.java` -- implementar `buscarPorId` via `AgendamentoJpaRepository.findById` -- adapter da nova porta
- [x] `application/command/ConfirmarPresenca.java` (novo) -- comando `@Transactional`: `atualizarStatusSeAtual(id, AGUARDANDO_CONFIRMACAO, CONFIRMADO)`; se `true`, grava `EventoOutbox` (`ConfirmacaoRegistrada`, payload `agendamentoId`+`pacienteId`); se `false`, releitura via `buscarPorId` decide entre sucesso silencioso (já `CONFIRMADO`) e `409` (qualquer outro estado) -- lógica central da story, molde `AbrirJanelaDeConfirmacao`
- [x] `application/command/AgendamentoForaDaJanelaException.java` (novo) -- exceção de domínio para o caso `409`, mensagem distinguindo "janela não aberta" vs. "vaga liberada" -- molde `RecursoJaAlocadoException`
- [x] `infrastructure/web/AgendamentoController.java` -- adicionar `POST /v1/agendamentos/{id}/confirmacao` chamando `ConfirmarPresenca` -- expõe o comando via API
- [x] `infrastructure/web/AgendamentoExceptionHandler.java` -- adicionar handler `AgendamentoForaDaJanelaException` → `409` `ProblemDetail` -- molde `RecursosExceptionHandler.handleRecursoJaAlocado`
- [x] `application/command/ConfirmarPresencaTest.java` (novo) -- cobrir toda a I/O Matrix (confirmação válida, duplicada, janela não aberta, liberado, id inexistente) -- molde `AbrirJanelaDeConfirmacaoTest`
- [x] `ConfirmarPresencaConcurrencyIntegrationTest.java` (novo) -- N threads confirmando o mesmo `agendamentoId` concorrentemente, assert só uma transição efetiva -- molde `AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest`
- [x] `AgendamentoControllerIntegrationTest.java` -- estender com cenário HTTP de confirmação (200 e 409) -- molde já existente no mesmo arquivo

**Acceptance Criteria:**
- Given um Agendamento `AGUARDANDO_CONFIRMACAO`, when `POST /v1/agendamentos/{id}/confirmacao`, then status `200`, `Agendamento` vira `CONFIRMADO` e `ConfirmacaoRegistrada` é gravado no outbox na mesma transação.
- Given um `agendamentoId` inexistente, when confirmar, then `404`.
- Given duas requisições concorrentes de confirmação para o mesmo Agendamento `AGUARDANDO_CONFIRMACAO`, when ambas competem, then só uma transição commita e nenhum evento duplicado é publicado.

## Design Notes

`ConfirmarPresenca` não recebe `correlationId` de header ainda (o serviço não tem essa convenção — `AbrirJanelaDeConfirmacao` gera localmente por ser poller); seguir o mesmo padrão local (`"agendamento-" + id`) nesta story. Adotar `X-Correlation-Id` de header fica para quando o Gateway propagar (AD-9/observabilidade) — não é bloqueio desta story.

## Verification

**Commands:**
- `cd agendamento-confirmacao-service && ./mvnw test` -- expected: todos os testes (unitários + integração Testcontainers) passam, incluindo os novos de `ConfirmarPresenca`.
- `cd agendamento-confirmacao-service && ./mvnw verify` -- expected: build completo (inclui Testcontainers) sem falhas.

## Suggested Review Order

**Caso de uso `ConfirmarPresenca`**

- Ponto de entrada: comando que orquestra a transição de estado inteira dentro de uma única transação.
  [`ConfirmarPresenca.java:60`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/application/command/ConfirmarPresenca.java#L60)

- Após patch de revisão: retentativa única quando a janela abre entre a escrita condicional falhar e a releitura.
  [`ConfirmarPresenca.java:99`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/application/command/ConfirmarPresenca.java#L99)

**Escrita condicional e releitura de estado (AD-4)**

- Nova porta de releitura por id, necessária para decidir sucesso silencioso vs. `409` vs. `404`.
  [`AgendamentoRepositorio.java:57`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/application/command/AgendamentoRepositorio.java#L57)

- Mensagem de conflito distinguindo janela não aberta vs. vaga liberada.
  [`AgendamentoForaDaJanelaException.java:24`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/application/command/AgendamentoForaDaJanelaException.java#L24)

**Camada web — novo endpoint e tratamento de erro**

- Novo endpoint `POST /v1/agendamentos/{id}/confirmacao`, sem corpo de resposta.
  [`AgendamentoController.java:45`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoController.java#L45)

- Handlers de `404`/`409` no molde RFC 7807 de `matching-alocacao-service`.
  [`AgendamentoExceptionHandler.java:84`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoExceptionHandler.java#L84)

- Patch de revisão: `id` malformado no path agora vira `400` em vez de `500` genérico.
  [`AgendamentoExceptionHandler.java:112`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoExceptionHandler.java#L112)

**Testes**

- Cobertura da I/O Matrix inteira (válida, duplicada, janela não aberta, liberada, inexistente) + retentativa da corrida.
  [`ConfirmarPresencaTest.java:73`](../../agendamento-confirmacao-service/src/test/java/com/confirmasus/agendamento/application/command/ConfirmarPresencaTest.java#L73)

- Prova de exclusão mútua contra Postgres real (8 threads concorrentes no mesmo `agendamentoId`).
  [`ConfirmarPresencaConcurrencyIntegrationTest.java:59`](../../agendamento-confirmacao-service/src/test/java/com/confirmasus/agendamento/application/command/ConfirmarPresencaConcurrencyIntegrationTest.java#L59)

- Contrato HTTP ponta a ponta, incluindo o caso de `id` malformado do patch.
  [`AgendamentoControllerIntegrationTest.java:260`](../../agendamento-confirmacao-service/src/test/java/com/confirmasus/agendamento/AgendamentoControllerIntegrationTest.java#L260)
