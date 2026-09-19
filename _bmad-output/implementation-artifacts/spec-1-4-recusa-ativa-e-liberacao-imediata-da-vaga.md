---
title: 'Recusa Ativa e Liberação Imediata da Vaga'
type: 'feature'
created: '2026-09-18'
status: 'done'

baseline_commit: 'b96a71c8964caf0d5c55b17c6be91711c4519d91'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Um Agendamento em `AGUARDANDO_CONFIRMACAO` não tem meio de o Paciente recusar presença via API — a vaga não é liberada imediatamente, bloqueando o repasse para outro candidato (FR-5). Além disso, o domínio carece do campo `motivoLiberacao` para distinguir entre Recusa e Não Confirmado na auditoria.

**Approach:** Novo comando `RecusarPresenca` em `agendamento-confirmacao-service`, exposto via `POST /v1/agendamentos/{id}/recusa`, que transiciona `AGUARDANDO_CONFIRMACAO` → `LIBERADO` com `motivoLiberacao = RECUSA` via escrita condicional. Migration V3 adiciona a coluna `motivo_liberacao` na tabela `agendamentos`. Dois eventos são publicados no outbox na mesma transação: `RecusaRegistrada` + `VagaLiberada`. O padrão de idempotência e releitura espelha a Story 1.3.

## Boundaries & Constraints

**Always:** Transição via `UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'` (AD-4). Recusa duplicada (já `LIBERADO/RECUSA`) retorna sucesso silencioso. Outro estado perdedor (`AGUARDANDO_JANELA`, `CONFIRMADO`, `LIBERADO/NAO_CONFIRMADO`) retorna `409` RFC 7807. Dois eventos (`RecusaRegistrada` + `VagaLiberada`) publicados no outbox na mesma transação. Enum `MotivoLiberacao ∈ {RECUSA, NAO_CONFIRMADO}` mapeado no JPA e domínio.

**Ask First:** Nenhuma — segue precedente Story 1.3.

**Never:** Não criar migration retroativa. Não publicar apenas um evento. Não reativar métodos de domínio não-utilizados. Não adicionar RBAC.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Recusa válida | `AGUARDANDO_CONFIRMACAO` | `200`, status vira `LIBERADO/RECUSA`, `RecusaRegistrada` + `VagaLiberada` no outbox | N/A |
| Recusa duplicada | `LIBERADO/RECUSA` (mesma recusa) | `200` sucesso silencioso, sem novo evento | Releitura confirma equivalência |
| Janela não aberta | `AGUARDANDO_JANELA` | `409` "janela ainda não aberta" | Releitura após 0 linhas |
| Confirmado | `CONFIRMADO` | `409` "vaga confirmada" | Releitura |
| Liberado (outro motivo) | `LIBERADO/NAO_CONFIRMADO` | `409` "vaga já liberada" | Releitura |
| Id inexistente | não encontrado | `404` | `buscarPorId` vazio |
| Corrida Recusa vs. Confirmação | concorrente `AGUARDANDO_CONFIRMACAO` | Escrita condicional: uma vence, outra relê e recebe `409` | Testcontainers concorrência |
| Corrida Recusa vs. Recusa | concorrente idêntica | Uma recebe 0 linhas, relê `LIBERADO/RECUSA`, retorna `200` silencioso | Testcontainers concorrência |

</frozen-after-approval>

## Code Map

- `agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/Agendamento.java:75` -- adicionar `recusar()` retornando `LIBERADO` (espelho de `confirmar()`).
- `agendamento-confirmacao-service/.../domain/MotivoLiberacao.java` (novo) -- enum `{RECUSA, NAO_CONFIRMADO}`.
- `agendamento-confirmacao-service/.../infrastructure/persistence/AgendamentoJpaEntity.java:18-45` -- field `String motivoLiberacao` mapeado para `motivo_liberacao`.
- `agendamento-confirmacao-service/.../application/command/AgendamentoRepositorio.java:24-45` -- adicionar sobrecarga `boolean atualizarStatusComMotivo(Long id, StatusAgendamento statusEsperado, StatusAgendamento novoStatus, String motivoLiberacao)`.
- `agendamento-confirmacao-service/.../infrastructure/persistence/AgendamentoRepositorioAdapter.java:23-77` -- implementar `atualizarStatusComMotivo` com UPDATE condicional incluindo coluna `motivo_liberacao`.
- `agendamento-confirmacao-service/.../application/command/ConfirmarPresenca.java:46-121` -- precedente estrutural para `RecusarPresenca` (releitura, retentativa, exceções RFC 7807).
- `agendamento-confirmacao-service/.../domain/EventoOutbox.java:25-92` -- reaproveitar para `RecusaRegistrada` e `VagaLiberada`.
- `agendamento-confirmacao-service/.../infrastructure/persistence/EventoOutboxRepositorioAdapter.java:32-78` -- reaproveitar `salvar`; chamar duas vezes.
- `agendamento-confirmacao-service/.../infrastructure/web/AgendamentoController.java:18-34` -- adicionar `POST /v1/agendamentos/{id}/recusa`.
- `agendamento-confirmacao-service/.../infrastructure/web/AgendamentoExceptionHandler.java:38-83` -- reaproveitar handler de `AgendamentoForaDaJanelaException`.
- `agendamento-confirmacao-service/.../db/migration/V3__add_motivo_liberacao.sql` -- nova migration adicionando `motivo_liberacao VARCHAR(32) NULL`.
- `agendamento-confirmacao-service/src/test/java/.../application/command/RecusarPresencaTest.java` -- novo, molde `ConfirmarPresencaTest`, I/O Matrix completa.
- `agendamento-confirmacao-service/src/test/java/.../RecusarPresencaConcurrencyIntegrationTest.java` -- novo, recusa concorrente.
- `agendamento-confirmacao-service/src/test/java/.../infrastructure/web/AgendamentoControllerIntegrationTest.java:41-80` -- estender com recusa (200, 409, 404).

## Tasks & Acceptance

**Execution:**
- [ ] `db/migration/V3__add_motivo_liberacao.sql` -- criar migration `motivo_liberacao VARCHAR(32) NULL` -- rastreamento de causa de liberação.
- [ ] `domain/MotivoLiberacao.java` -- enum `{RECUSA, NAO_CONFIRMADO}` -- mapeia causas conforme AD-4.
- [ ] `infrastructure/persistence/AgendamentoJpaEntity.java` -- field `motivoLiberacao` mapeado -- JPA mapping.
- [ ] `application/command/AgendamentoRepositorio.java` -- sobrecarga `atualizarStatusComMotivo(...)` -- escrita condicional com motivo.
- [ ] `infrastructure/persistence/AgendamentoRepositorioAdapter.java` -- implementar `atualizarStatusComMotivo` -- UPDATE condicional.
- [ ] `application/command/RecusarPresenca.java` (novo) -- `@Transactional` command: `atualizarStatusComMotivo(id, AGUARDANDO_CONFIRMACAO, LIBERADO, RECUSA)`, releitura+retentativa, dois eventos no outbox.
- [ ] `infrastructure/web/AgendamentoController.java` -- endpoint `POST /v1/agendamentos/{id}/recusa`.
- [ ] `application/command/RecusarPresencaTest.java` (novo) -- I/O Matrix, molde `ConfirmarPresencaTest`.
- [ ] `RecusarPresencaConcurrencyIntegrationTest.java` (novo) -- concorrência.
- [ ] `AgendamentoControllerIntegrationTest.java` -- estender com recusa HTTP.

**Acceptance Criteria:**
- Given `AGUARDANDO_CONFIRMACAO`, when `POST /v1/agendamentos/{id}/recusa`, then `200`, status vira `LIBERADO/RECUSA`, `RecusaRegistrada` + `VagaLiberada` no outbox.
- Given id inexistente, when recusar, then `404`.
- Given duas recusas concorrentes, when ambas competem, then uma commita, outra relê, sucesso silencioso, nenhum evento duplicado.
- Given já `LIBERADO/RECUSA`, when retentar, then `200` silencioso sem novo evento.
- Given `CONFIRMADO` ou `LIBERADO/NAO_CONFIRMADO`, when recusar, then `409` Conflict.

## Design Notes

`RecusarPresenca` espelha `ConfirmarPresenca`: releitura, exceções RFC 7807, retentativa. Diferença: dois eventos na mesma transação (`RecusaRegistrada` + `VagaLiberada`). `motivoLiberacao` persistido via `atualizarStatusComMotivo` sem migration retroativa.

## Verification

**Commands:**
- `cd agendamento-confirmacao-service && ./mvnw test` -- expected: testes novos + estendidos passam.
- `cd agendamento-confirmacao-service && ./mvnw verify` -- expected: build completo sem falhas.
- `cd agendamento-confirmacao-service && ./mvnw flyway:info` -- expected: V3 aplicada.

## Suggested Review Order

- [`RecusarPresenca.java:60`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RecusarPresenca.java#L60) -- orquestra transição + dois eventos.
- [`RecusarPresenca.java:99`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RecusarPresenca.java#L99) -- retentativa única.
- [`RecusarPresenca.java:115-125`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RecusarPresenca.java#L115) -- dois eventos no outbox.
- [`AgendamentoRepositorioAdapter.java:80`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/infrastructure/persistence/AgendamentoRepositorioAdapter.java#L80) -- UPDATE condicional com motivo.

## Suggested Review Order

**Orquestração: Comando de Recusa**

- Ponto de entrada: orquestra transição de estado + publicação de dois eventos atomicamente.
  [`RecusarPresenca.java:60`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RecusarPresenca.java#L60)

**Escrita Condicional e Releitura (AD-4)**

- Escrita condicional que atualiza status e motivoLiberacao na mesma cláusula WHERE.
  [`AgendamentoRepositorioAdapter.java:90`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/infrastructure/persistence/AgendamentoRepositorioAdapter.java#L90)

- Releitura pós-falha: decide entre sucesso silencioso, 409 ou retentativa.
  [`RecusarPresenca.java:115`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RecusarPresenca.java#L115)

**Publicação de Dois Eventos (AD-3)**

- RecusaRegistrada + VagaLiberada gravados no outbox na mesma transação.
  [`RecusarPresenca.java:145`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RecusarPresenca.java#L145)

**Domínio: Tipo de Liberação**

- Enum MotivoLiberacao com valores RECUSA e NAO_CONFIRMADO.
  [`MotivoLiberacao.java`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/MotivoLiberacao.java)

**Persistência: JPA + Migration**

- Coluna motivoLiberacao VARCHAR(32) NULL adicionada; migration V3.
  [`V3__add_motivo_liberacao.sql`](../../../agendamento-confirmacao-service/src/main/resources/db/migration/V3__add_motivo_liberacao.sql)

- Entity mapping e converter JPA para bidireção enum ↔ String.
  [`AgendamentoJpaEntity.java:99`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/infrastructure/persistence/AgendamentoJpaEntity.java#L99)

- Sobrecarga do repositório: atualizarStatusComMotivo() com UPDATE condicional.
  [`AgendamentoRepositorio.java:52`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/AgendamentoRepositorio.java#L52)

**HTTP Endpoint**

- POST /v1/agendamentos/{id}/recusa expõe o comando.
  [`AgendamentoController.java:65`](../../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/infrastructure/web/AgendamentoController.java#L65)

**Testes: Cobertura Completa**

- Testes unitários da I/O Matrix (válida, duplicada, estados inválidos, id inexistente).
  [`RecusarPresencaTest.java:51`](../../../agendamento-confirmacao-service/src/test/java/com/filajusta/agendamento/application/command/RecusarPresencaTest.java#L51)

- Teste de concorrência (Recusa vs. Recusa, Recusa vs. Confirmação) com Testcontainers.
  [`RecusarPresencaConcurrencyIntegrationTest.java:65`](../../../agendamento-confirmacao-service/src/test/java/com/filajusta/agendamento/application/command/RecusarPresencaConcurrencyIntegrationTest.java#L65)

- Teste HTTP end-to-end com cenários 200/409/404, parametrizado.
  [`AgendamentoControllerIntegrationTest.java:310`](../../../agendamento-confirmacao-service/src/test/java/com/filajusta/agendamento/AgendamentoControllerIntegrationTest.java#L310)
