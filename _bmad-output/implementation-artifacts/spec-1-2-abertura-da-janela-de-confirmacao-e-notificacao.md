---
title: 'Abertura da Janela de Confirmação e Notificação'
type: 'feature'
created: '2026-09-18'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '105eea55e57967d2d7d56a1be165bc0adfae334e'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Agendamentos ficam presos em `AGUARDANDO_JANELA` para sempre — não existe mecanismo que abra a Janela de Confirmação no horário certo nem que notifique o Paciente disso (FR-3).

**Approach:** Adicionar um poller `@Scheduled` em `agendamento-confirmacao-service` que transiciona `AGUARDANDO_JANELA`→`AGUARDANDO_CONFIRMACAO` via escrita condicional e publica `NotificacaoConfirmacaoPublicada` via outbox (AD-3) na mesma transação, reaproveitando o molde de outbox+relay SNS FIFO existente em `matching-alocacao-service` (hoje inexistente neste serviço — foi removido na Story 1.1 junto do domínio Triagem/Score).

## Boundaries & Constraints

**Always:** transição de estado via `UPDATE ... WHERE status = 'AGUARDANDO_JANELA'` (AD-4); outbox gravado na mesma transação do poller (AD-3); `eventId` UUID v4 gerado na application layer; `MessageGroupId = agendamentoId`, `MessageDeduplicationId = eventId`; leitura de pendentes com `FOR UPDATE SKIP LOCKED` (molde `EventoOutboxJpaRepository.buscarPendentesParaAtualizar`); relay com kill-switch `@ConditionalOnProperty` (molde `RelaySnsPublisherJob`); `AgendamentoConfirmacaoServiceApplication` ganha `@EnableScheduling`.

**Ask First:** nenhuma — cadência do poller é `[ASSUMPTION]` 5000ms (mesmo default do molde), AD-5 marca a cadência exata como deferred.

**Never:** poller de expiração (Story 1.5, fora de escopo); qualquer notificação real (e-mail/SMS) — a "notificação" desta story é só o evento de domínio publicado; sincronizar com `liberacao-repasse-service`/`auditoria-service` neste serviço.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Janela pronta para abrir | Agendamento `AGUARDANDO_JANELA`, `janelaAbreEm <= now()` | Status→`AGUARDANDO_CONFIRMACAO`; linha gravada em `eventos_outbox` na mesma transação | N/A |
| Reprocessamento do mesmo Agendamento | Poller roda de novo sobre Agendamento já `AGUARDANDO_CONFIRMACAO` | `UPDATE` afeta 0 linhas; nenhum evento novo gravado | N/A (idempotência por design, não é erro) |
| Ainda não chegou a hora | Agendamento `AGUARDANDO_JANELA`, `janelaAbreEm > now()` | Não selecionado pela query do poller | N/A |
| Múltiplas tasks ECS concorrentes | 2+ instâncias rodando o mesmo poller | `SKIP LOCKED` garante que cada linha é processada por uma única instância | N/A |
| Relay indisponível (SNS fora do ar) | Linha gravada em `eventos_outbox`, publish falha | Transação do relay não commita `publicado_em`; linha permanece pendente para retry no próximo ciclo | Exception logada, sem crash do poller |

</frozen-after-approval>

## Code Map

- `agendamento-confirmacao-service/.../domain/Agendamento.java:19-70` -- imutável, sem transição de estado; adicionar `janelaAbreEm`/`janelaExpiraEm` + método `abrirJanela()` (retorna nova instância, padrão `EventoOutbox.comId`).
- `.../domain/StatusAgendamento.java:12-17` -- enum já completo, nenhuma mudança.
- `.../application/command/AgendamentoRepositorio.java:10-13` -- porta só com `salvar`; adicionar `buscarPendentesAberturaJanela(int limite)` + `atualizarStatusSeAtual(id, statusEsperado, novoStatus)`.
- `.../infrastructure/persistence/AgendamentoJpaEntity.java:22-82`, `AgendamentoRepositorioAdapter.java:12-38` -- seguir o mesmo padrão de `EventoOutboxJpaRepository` (`matching-alocacao-service/.../infrastructure/persistence/EventoOutboxJpaRepository.java:24-39`) para a query nativa `FOR UPDATE SKIP LOCKED` e o `UPDATE` condicional.
- Molde completo de outbox a replicar em `agendamento-confirmacao-service` (novo pacote): `domain/EventoOutbox.java`, `application/command/EventoOutboxRepositorio.java`, `infrastructure/persistence/EventoOutbox{JpaEntity,JpaRepository,RepositorioAdapter}.java`, `infrastructure/relay/{RelaySnsPublisherJob,RelaySnsClientConfig}.java` -- copiar de `matching-alocacao-service/src/main/java/com/filajusta/matching/{domain,application/command,infrastructure/persistence,infrastructure/relay}/` (arquivos e linhas conforme investigação já compilada nesta sessão), adaptando pacote e `MessageGroupId` para `agendamentoId`.
- `agendamento-confirmacao-service/src/main/resources/db/migration/V1__create_agendamento_confirmacao_schema.sql:24-31` -- não alterar; criar `V2__add_janela_e_outbox.sql` com `ALTER TABLE agendamentos ADD janela_abre_em TIMESTAMPTZ, ADD janela_expira_em TIMESTAMPTZ` + `CREATE TABLE eventos_outbox` (mesmo DDL de `matching-alocacao-service/.../V4__create_eventos_outbox.sql`, schema `agendamento_confirmacao`).
- `agendamento-confirmacao-service/src/main/resources/application.yml` -- adicionar bloco `filajusta.agendamento.outbox-relay.*` (molde `matching-alocacao-service/.../application.yml:71-91`, env var `FILAJUSTA_AGENDAMENTO_OUTBOX_RELAY_TOPIC_ARN`).
- `AgendamentoConfirmacaoServiceApplication.java:23-27` -- remover comentário "sem `@EnableScheduling`"; adicionar a anotação (molde `MatchingAlocacaoServiceApplication.java:124-125`).
- `infra-cdk/.../FilaJustaStack.java:268-276` -- criar tópico SNS FIFO próprio (molde `buildMatchingAlocacaoEventosTopic():293`) + `TaskRole` com `grantPublish` (molde linha 294) para `agendamento-confirmacao-service`; injetar ARN/região via env var no construto Fargate do serviço.

## Tasks & Acceptance

**Execution:**
- [x] `domain/Agendamento.java` -- adicionar `janelaAbreEm`/`janelaExpiraEm` + método `abrirJanela()` imutável -- AD-4
- [x] `db/migration/V2__add_janela_e_outbox.sql` -- colunas de janela + tabela `eventos_outbox` -- AD-3/AD-10
- [x] Pacote outbox completo (domain/application/persistence/relay) -- copiar molde de `matching-alocacao-service`, adaptar `MessageGroupId=agendamentoId` -- AD-3
- [x] `application/command/AbrirJanelaDeConfirmacao.java` (novo) -- `@Scheduled @Transactional`: busca pendentes (`FOR UPDATE SKIP LOCKED`), `UPDATE` condicional, grava outbox -- AD-5
- [x] `AgendamentoRepositorio`/adapter -- métodos novos de busca e escrita condicional -- suporte ao poller
- [x] `application.yml` + `AgendamentoConfirmacaoServiceApplication` -- config outbox-relay + `@EnableScheduling`
- [x] Testes unitários do poller cobrindo a I/O Matrix (happy path, reprocessamento idempotente, ainda não chegou a hora) -- cobertura ≥90% domínio
- [x] Teste de integração com `SKIP LOCKED` sob concorrência simulada (2 chamadas ao método de busca) -- valida exclusão mútua
- [x] `infra-cdk/.../FilaJustaStack.java` -- tópico SNS FIFO + `TaskRole.grantPublish` + env vars no Fargate -- AD-3/AD-11
- [x] `infra-cdk` teste -- atualizar `FilaJustaStackTest` para o tópico/role novos

**Acceptance Criteria:**
- Given um Agendamento `AGUARDANDO_JANELA` com `janelaAbreEm <= now()`, when o poller roda, then o Agendamento vira `AGUARDANDO_CONFIRMACAO` e `NotificacaoConfirmacaoPublicada` é publicado via outbox na mesma transação.
- Given o mesmo Agendamento já processado, when o poller roda de novo, then nenhuma segunda notificação é gravada (escrita condicional zero linhas).

## Spec Change Log

- **2026-09-18, patch findings (step-04, blind-hunter + verification-gap + edge-case-hunter):** Sem intent_gap/bad_spec — três patches aplicados diretamente ao código sem reabrir o spec: (1) `AbrirJanelaDeConfirmacao.abrirJanelas()` isola falha por item do lote num `try/catch` (mesmo precedente de `RelaySnsPublisherJob`), evitando que uma falha no meio do lote desfizesse (via rollback do `@Transactional` do método inteiro) as transições/eventos já gravados para Agendamentos anteriores na mesma execução; (2) corrigido javadoc quebrado em `RegistrarAgendamento` (`{@code ...}` malformado); (3) `EventoOutbox` agora valida `eventType` não-branco (mesma guarda já existente para `correlationId`). Quatro achados adicionais (validação cruzada `janelaAbreEm`/`dataHoraAgendamento`, backfill da migration V2 assumindo tabela vazia, testes unitários faltando para os adapters/`RelaySnsClientConfig`, `Agendamento.abrirJanela()` como código morto) foram registrados em `deferred-work.md` — nenhum bloqueia os ACs desta story. KEEP: a decisão de escrita condicional via adapter (não pelo método de domínio) permanece; não redesenhar a transição de estado nesta story.

## Design Notes

O poller não deve reimplementar o relay — só a leitura/transição de `Agendamento` e a escrita na tabela `eventos_outbox`; a publicação em si é responsabilidade exclusiva do `RelaySnsPublisherJob` copiado (já genérico, não conhece o domínio de Agendamento). `janelaAbreEm` é calculado e persistido no momento do `RegistrarAgendamento` (Story 1.1) — como essa story já fechou, popular a coluna nova via lógica adicionada a `RegistrarAgendamento` faz parte desta spec (duração da janela: `[ASSUMPTION]` valor fixo configurável, já que o PRD não define o número exato).

## Verification

**Commands:**
- `mvn -pl agendamento-confirmacao-service -am test` -- expected: todos os testes passam, incluindo os novos do poller
- `mvn -pl infra-cdk -am test` (da raiz do repo) -- expected: `FilaJustaStackTest` passa com o tópico/role novos
- `grep -rn "janelaAbreEm\|eventos_outbox" agendamento-confirmacao-service/src/main` -- expected: presente em domain, migration e persistence

**Manual checks (if no CLI):**
- Revisar `application.yml` para confirmar que o namespace `filajusta.agendamento.outbox-relay` não colide com nenhum existente.

## Suggested Review Order

**Poller de abertura de janela (AD-5)**

- Ponto de entrada: transição condicional + isolamento de falha por item (patch do code review).
  [`AbrirJanelaDeConfirmacao.java:71`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/AbrirJanelaDeConfirmacao.java#L71)

- `abrirJanela()` no domínio nunca é chamado — a transição real vive no adapter (decisão documentada, ver `deferred-work.md`).
  [`Agendamento.java:511`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/Agendamento.java#L511)

- Query nativa `FOR UPDATE SKIP LOCKED` + `UPDATE` condicional — garante exclusão mútua entre tasks ECS concorrentes.
  [`AgendamentoJpaRepository.java:694`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/infrastructure/persistence/AgendamentoJpaRepository.java#L694)

**Outbox + relay SNS FIFO (AD-3, molde copiado de matching-alocacao-service)**

- Linha do outbox, agora com `eventType` validado como não-branco (patch do code review).
  [`EventoOutbox.java:35`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/domain/EventoOutbox.java#L35)

- Relay periódico: lê pendentes, publica no SNS FIFO, marca publicado só após ack do broker.
  [`RelaySnsPublisherJob.java`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/infrastructure/relay/RelaySnsPublisherJob.java)

**Cálculo da janela (RegistrarAgendamento, Story 1.1 revisitada)**

- `janelaAbreEm = agora + janelaDuracao` — sem validação cruzada contra `dataHoraAgendamento` (item deferido).
  [`RegistrarAgendamento.java:67`](../../agendamento-confirmacao-service/src/main/java/com/filajusta/agendamento/application/command/RegistrarAgendamento.java#L67)

**Infraestrutura (CDK + migration)**

- Tópico SNS FIFO novo + `grantPublish` na TaskRole real do serviço (não uma role standalone).
  [`FilaJustaStack.java:268`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L268)

- Colunas de janela + tabela `eventos_outbox`; backfill assume tabela vazia (item deferido).
  [`V2__add_janela_e_outbox.sql`](../../agendamento-confirmacao-service/src/main/resources/db/migration/V2__add_janela_e_outbox.sql)

**Testes (peso maior: concorrência e I/O Matrix)**

- Concorrência real via `SKIP LOCKED` sob 8 threads simultâneas.
  [`AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest.java`](../../agendamento-confirmacao-service/src/test/java/com/filajusta/agendamento/application/command/AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest.java)

- Cobertura unitária da I/O & Edge-Case Matrix da spec (happy path, reprocessamento, ainda não chegou a hora).
  [`AbrirJanelaDeConfirmacaoTest.java`](../../agendamento-confirmacao-service/src/test/java/com/filajusta/agendamento/application/command/AbrirJanelaDeConfirmacaoTest.java)
</content>
