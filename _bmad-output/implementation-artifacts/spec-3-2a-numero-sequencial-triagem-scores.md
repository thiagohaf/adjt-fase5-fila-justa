---
title: 'Story 3.2a: Expor Número de Sequência da Triagem em GET /internal/scores'
type: 'feature'
created: '2026-09-10'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'fcaab29b356f653a22dfc8d9eb1b2f8622b69cc5'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Story 3.2 (`matching-alocacao-service`, sugestão de matching) precisa do "número de sequência da Triagem" para o desempate residual entre Pacientes com Prioridade Efetiva e `occurredAt` idênticos (AD-5) — hoje esse dado não é exposto em lugar nenhum fora de `triagem-score-service`, embora já exista: `RegistrarTriagem` já grava `triagemId` (= `TriagemJpaEntity.id`, `IDENTITY`) no `payload` do evento `ScoreCalculado` (linha 141), mas `GET /internal/scores` (Story 3.1a) descarta esse campo ao desserializar.

**Approach:** `ScoresAtuaisRepositorioAdapter` passa a extrair `triagemId` do `payload` (já presente, nenhuma mudança no writer do outbox/evento) e propagá-lo por `ScoreAtual` até a resposta de `GET /internal/scores`, como `numeroSequencialTriagem`.

## Boundaries & Constraints

**Always:** `numeroSequencialTriagem` = valor de `payload.triagemId` do evento `ScoreCalculado` de origem — nunca recalculado, nunca lido da tabela `triagens` diretamente (mesma fonte única, `eventos_outbox`, já usada por `ScoresAtuaisRepositorioAdapter`). Falha na extração deste campo (ausente/tipo errado) trata a linha como já tratado hoje — isola e loga, não derruba a listagem inteira (mesmo padrão de resiliência por linha já existente).

**Ask First:** Nenhuma pendente.

**Never:** Alterar `RegistrarTriagem`/o writer do outbox — o campo já está no payload, esta story só passa a lê-lo. Alterar o schema/coluna de `eventos_outbox`. Qualquer mudança em `matching-alocacao-service` (propagação para `ScoreReplica` é escopo da Story 3.2b).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Payload com `triagemId` presente | Evento `ScoreCalculado` normal | `GET /internal/scores` inclui `numeroSequencialTriagem` no item | N/A |
| Payload sem `triagemId` (evento legado/malformado) | `payload.triagemId` ausente ou não-numérico | Linha isolada e ignorada, log com `eventId` (mesmo padrão já existente para outros campos ausentes) | Não derruba a listagem |

</frozen-after-approval>

## Code Map

- `triagem-score-service/.../application/query/ScoreAtual.java:26` -- record; adicionar campo `Long numeroSequencialTriagem` (`Objects.requireNonNull` como os demais)
- `triagem-score-service/.../infrastructure/persistence/ScoresAtuaisRepositorioAdapter.java:85-116` (`paraScoreAtual`) -- extrair `payload.get("triagemId")`, validar `instanceof Number` (mesmo padrão de `pacienteIdBruto`/`scoreValorBruto`), passar para `ScoreAtual`
- `triagem-score-service/.../infrastructure/web/ScoreAtualResponse.java:19` -- adicionar campo `numeroSequencialTriagem` ao record e ao `de(ScoreAtual)`
- `triagem-score-service/.../application/command/RegistrarTriagem.java:141` -- **só leitura de referência**, não modificar (`payload.put("triagemId", triagem.getId())` já existe)

## Tasks & Acceptance

**Execution:**
- [x] `ScoreAtual.java` -- adicionar campo `numeroSequencialTriagem` -- transporta o dado da camada de persistência até a web
- [x] `ScoresAtuaisRepositorioAdapter.java` -- extrair e validar `triagemId` do payload -- fecha o gap de dado sem tocar o writer do evento
- [x] `ScoreAtualResponse.java` -- expor `numeroSequencialTriagem` no JSON de resposta -- consumidor (`matching-alocacao-service`, Story 3.2b) passa a poder ler o campo
- [x] Teste unitário de `ScoresAtuaisRepositorioAdapter` -- payload com/sem `triagemId` (cobre a I/O Matrix)
- [x] Teste unitário/de integração existente de `GET /internal/scores` -- atualizar asserção para incluir `numeroSequencialTriagem`

**Acceptance Criteria:**
- Given um evento `ScoreCalculado` com `triagemId` no payload, when `GET /internal/scores`, then o item retornado inclui `numeroSequencialTriagem` igual ao `triagemId` original
- Given um evento com payload sem `triagemId` (malformado), when `GET /internal/scores`, then a linha é isolada/ignorada e as demais linhas válidas continuam presentes na resposta

## Verification

**Commands:**
- `mvn -pl triagem-score-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação mantido ≥90%

## Suggested Review Order

**Extração do dado (fonte da verdade)**

- `payload.get("triagemId")` extraído junto de `pacienteId`, validado como `Number` antes de virar `numeroSequencialTriagem` -- núcleo da mudança.
  [`ScoresAtuaisRepositorioAdapter.java:92-113`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapter.java#L92)

**Propagação do campo (persistência → web)**

- `ScoreAtual` ganha `numeroSequencialTriagem`, com o javadoc explicando a direção do desempate (menor = mais antiga) para a 3.2b.
  [`ScoreAtual.java:24-42`](../../triagem-score-service/src/main/java/com/filajusta/triagem/application/query/ScoreAtual.java#L24)

- `ScoreAtualResponse` expõe o mesmo campo no JSON de `GET /internal/scores`.
  [`ScoreAtualResponse.java:18-32`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/ScoreAtualResponse.java#L18)

**Testes (I/O & Edge-Case Matrix)**

- Payload válido: `numeroSequencialTriagem` reconstruído corretamente.
  [`ScoresAtuaisRepositorioAdapterTest.java:37-55`](../../triagem-score-service/src/test/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapterTest.java#L37)

- `triagemId` ausente: linha isolada/ignorada, resto da listagem sobrevive.
  [`ScoresAtuaisRepositorioAdapterTest.java:59-81`](../../triagem-score-service/src/test/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapterTest.java#L59)

- `triagemId` de tipo errado (string): mesmo tratamento de isolamento (achado do code review, cobre a metade "não-numérico" da matriz).
  [`ScoresAtuaisRepositorioAdapterTest.java:85-107`](../../triagem-score-service/src/test/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapterTest.java#L85)

- Ponta a ponta via `GET /internal/scores`: `numeroSequencialTriagem` bate com o `triagemId` retornado por `POST /v1/triagens` e distingue 2 Triagens do mesmo paciente.
  [`ListarScoresAtuaisIntegrationTest.java:166-179`](../../triagem-score-service/src/test/java/com/filajusta/triagem/ListarScoresAtuaisIntegrationTest.java#L166)
