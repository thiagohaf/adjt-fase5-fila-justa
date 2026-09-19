---
title: 'Story 3.2b1: Propagação de numeroSequencialTriagem até ScoreReplica (matching-alocacao-service)'
type: 'feature'
created: '2026-09-11'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'f01861d0bb5f4ac174238137dc68401bb1ecfcbd'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `GET /internal/scores` já expõe `numeroSequencialTriagem` desde a Story 3.2a, mas `ScoreReplica` em `matching-alocacao-service` ainda não o carrega — nem via bootstrap síncrono (Story 3.1c) nem via consumidor SQS FIFO (Story 3.1b) — deixando a fila priorizada (`ConsultarFilaPriorizada`, Story 3.1c) sem o dado para o desempate residual de AD-5 (empate de Prioridade Efetiva e `occurredAt` → vence a Triagem mais antiga, menor `numeroSequencialTriagem`).

**Approach:** Propagar `numeroSequencialTriagem` ponta a ponta em `ScoreReplica` (domínio, persistência, bootstrap, consumidor SQS) e aplicar o desempate residual na ordenação já existente de `ConsultarFilaPriorizada`.

## Boundaries & Constraints

**Always:**
- Fila ordenada por Prioridade Efetiva desc; desempate `occurredAt` asc, depois `numeroSequencialTriagem` asc, nulls-last (evento sem o dado nunca quebra a ordenação nem é excluído).
- `upsertSeMaisRecente` de `ScoreReplica` continua comparando só `(occurred_at, event_id)` para last-write-wins; `numeroSequencialTriagem` é campo de carga, nunca entra na tupla de comparação.
- Consumidor SQS: ausência ou formato inválido de `triagemId` no payload nunca rejeita o evento inteiro — grava `numeroSequencialTriagem = null` e segue processando normalmente.
- Bootstrap síncrono: ausência do campo na resposta de `GET /internal/scores` (compatibilidade defensiva) grava `null`, não falha o bootstrap.

**Ask First:** nenhuma decisão pendente.

**Never:** domínio/persistência/upsert de `Recurso` (Story 3.2b2, ver `deferred-work.md`); endpoint `GET /v1/recursos/{id}/sugestao` ou qualquer algoritmo de tiers (Story 3.2b3, ver `deferred-work.md`); confirmação/recusa/alocação (Story 3.3).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Bootstrap com o campo | `GET /internal/scores` retorna item com `numeroSequencialTriagem` | `ScoreReplica` gravada com o valor | N/A |
| Bootstrap sem o campo | Resposta sem `numeroSequencialTriagem` (defensivo) | `ScoreReplica` gravada com `null`, bootstrap não falha | N/A |
| Evento SQS com `triagemId` | Payload `ScoreCalculado` com `triagemId` válido | `ScoreReplica` gravada/atualizada com `numeroSequencialTriagem = triagemId` | N/A |
| Evento SQS sem `triagemId` | Payload malformado/ausente | `ScoreReplica` gravada com `numeroSequencialTriagem = null`; evento não rejeitado | N/A |
| Empate residual na fila | Dois itens com mesma Prioridade Efetiva e mesmo `occurredAt` | Vence o de menor `numeroSequencialTriagem` na ordenação de `ConsultarFilaPriorizada` | N/A |
| Empate residual com `null` | Um dos itens empatados tem `numeroSequencialTriagem = null` | Item com valor não-nulo vence (nulls-last) | N/A |

</frozen-after-approval>

## Code Map

- `matching-alocacao-service/src/main/resources/db/migration/V2__add_numero_sequencial_triagem_score_replica.sql` (novo) -- `ALTER TABLE score_replica ADD COLUMN numero_sequencial_triagem BIGINT NULL`
- `.../domain/ScoreReplica.java` -- adicionar campo `Long numeroSequencialTriagem` (nullable) ao construtor; `maisRecenteQue` inalterado
- `.../infrastructure/persistence/ScoreReplicaJpaEntity.java` -- coluna `numero_sequencial_triagem`
- `.../infrastructure/persistence/ScoreReplicaJpaRepository.java` -- `upsertSeMaisRecente`: incluir coluna na lista de colunas/VALUES/SET, fora da tupla `WHERE (occurred_at,event_id) > (...)`
- `.../infrastructure/persistence/ScoreReplicaRepositorioAdapter.java` -- repassar novo campo à chamada nativa
- `.../application/command/AtualizarScoreReplica.java` -- `atualizar(pacienteId, score, occurredAt, eventId, Long numeroSequencialTriagem)`
- `.../infrastructure/bootstrap/ScoreInternalDto.java` -- adicionar `Long numeroSequencialTriagem` ao record (mapeia por nome via Jackson, mesmo nome do campo em `ScoreAtualResponse`)
- `.../infrastructure/bootstrap/ScoreBootstrapService.java` -- passar `dto.numeroSequencialTriagem()` na chamada a `atualizar(...)`
- `.../infrastructure/relay/ScoreCalculadoConsumerJob.java` -- extrair `payload.get("triagemId")` (Number, opcional, fora de `validarEnvelope`), passar ao `atualizar(...)`; atualizar javadoc que hoje documenta `triagemId` como não persistido
- `.../application/query/ConsultarFilaPriorizada.java` -- `ItemFila` ganha `numeroSequencialTriagem`; comparator: `.thenComparing(ItemFila::occurredAt).thenComparing(ItemFila::numeroSequencialTriagem, Comparator.nullsLast(Comparator.naturalOrder()))`

## Tasks & Acceptance

**Execution:**
- [x] `V2__add_numero_sequencial_triagem_score_replica.sql` -- adicionar coluna -- base para o resto da propagação
- [x] `ScoreReplica`/`ScoreReplicaJpaEntity`/`ScoreReplicaJpaRepository`/`ScoreReplicaRepositorioAdapter`/`AtualizarScoreReplica` -- propagar o campo ponta a ponta na escrita
- [x] `ScoreInternalDto`/`ScoreBootstrapService` -- ler o campo no bootstrap REST síncrono
- [x] `ScoreCalculadoConsumerJob` -- ler `triagemId` do payload do evento, tolerando ausência
- [x] `ConsultarFilaPriorizada`/`ItemFila` -- expor e aplicar o desempate residual por `numeroSequencialTriagem`
- [x] Testes unitários cobrindo a I/O Matrix (bootstrap com/sem campo, SQS com/sem `triagemId`, empate residual com e sem `null`)
- [x] Atualizar testes de integração existentes de bootstrap e do consumidor SQS para incluir asserção do novo campo

**Acceptance Criteria:**
- Given um evento `ScoreCalculado` com `triagemId` no payload, when o consumidor SQS processa, then `ScoreReplica` é gravada com `numeroSequencialTriagem` igual ao `triagemId`
- Given uma resposta de bootstrap com `numeroSequencialTriagem`, when `ScoreBootstrapService` popula a réplica a frio, then o valor é persistido igual ao recebido
- Given dois Pacientes com Prioridade Efetiva e `occurredAt` idênticos, when `GET /v1/fila` ordena a fila, then vence o de menor `numeroSequencialTriagem`

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%

## Suggested Review Order

**Desempate residual na fila (entrada)**

- Comparator encadeado `prioridadeEfetiva` desc → `occurredAt` asc → `numeroSequencialTriagem` asc, nulls-last -- núcleo do AC de AD-5.
  [`ConsultarFilaPriorizada.java:81`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarFilaPriorizada.java#L81)

- `ItemFila` ganha o campo e o propaga a partir de `ScoreReplica` na fábrica `de(...)`.
  [`ConsultarFilaPriorizada.java:86`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/query/ConsultarFilaPriorizada.java#L86)

**Escrita em `ScoreReplica` (domínio + persistência)**

- Novo campo nullable no construtor -- campo de carga, nunca entra em `maisRecenteQue`.
  [`ScoreReplica.java:48`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/ScoreReplica.java#L48)

- Patch 1 do code review: `COALESCE(excluded.*, score_replica.*)` preserva valor conhecido quando o vencedor da tupla chega com `null`.
  [`ScoreReplicaJpaRepository.java:42`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/ScoreReplicaJpaRepository.java#L42)

- Query de leitura (`GET /v1/fila`) passa a repassar o campo da entidade JPA de volta ao domínio -- necessário para o desempate funcionar, adição fora do Code Map original.
  [`FilaRepositorioAdapter.java:47`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/FilaRepositorioAdapter.java#L47)

**Duas origens de escrita (bootstrap + consumidor SQS)**

- Patch 2 do code review: `isIntegralNumber()`/`canConvertToLong()` evita truncar silenciosamente um `triagemId` fracionário ou fora da faixa de `long`.
  [`ScoreCalculadoConsumerJob.java:203`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoConsumerJob.java#L203)

- Extração lida fora de `validarEnvelope` -- ausência/formato inválido nunca rejeita o evento inteiro.
  [`ScoreCalculadoConsumerJob.java:156`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/relay/ScoreCalculadoConsumerJob.java#L156)

- Bootstrap síncrono repassa o campo do DTO ao caso de uso, mesmo padrão do consumidor.
  [`ScoreBootstrapService.java:80`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/bootstrap/ScoreBootstrapService.java#L80)

- DTO de desserialização ganha o campo nullable, mapeado por nome via Jackson.
  [`ScoreInternalDto.java:28`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/bootstrap/ScoreInternalDto.java#L28)

**Peripherals**

- Nova migration adiciona a coluna nullable -- base de todo o resto.
  [`V2__add_numero_sequencial_triagem_score_replica.sql:8`](../../matching-alocacao-service/src/main/resources/db/migration/V2__add_numero_sequencial_triagem_score_replica.sql#L8)

- Testes cobrindo a I/O Matrix completa (bootstrap com/sem campo, SQS com/sem/formato inválido de `triagemId`, empate residual com e sem `null`, preservação de valor no upsert) espalhados em `ScoreReplicaTest`, `AtualizarScoreReplicaTest`, `ConsultarFilaPriorizadaTest`, `ScoreReplicaRepositorioAdapterIntegrationTest`, `ScoreCalculadoConsumerJobTest`, `ScoreCalculadoConsumerJobIntegrationTest`, `FilaBootstrapIntegrationTest`.
