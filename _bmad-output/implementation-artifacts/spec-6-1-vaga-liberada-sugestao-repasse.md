---
title: 'Story 6.1 — VagaLiberada → SugestaoRepasse (consumidor SQS FIFO, confirmar/recusar repasse)'
type: 'feature'
created: '2026-09-29'
status: 'in-progress'
baseline_commit: '2dc4ca2'
context:
  - _bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md (AD-3, AD-4, AD-6, AD-7)
---

## Intent

**Problem:** o PR #117 removeu o legado `LiberacaoAgendada*`; hoje, após `ConfirmarAlocacao`, o Recurso fica indisponível e a Alocação ATIVA para sempre. O ciclo "paciente não confirma → vaga libera → próximo da fila é sugerido → Gestor confirma/recusa" (AD-6) não existe: `VagaLiberada` já é publicada no SNS FIFO por `agendamento-confirmacao-service`, mas ninguém a consome.

**Approach:** `liberacao-repasse-service` consome `VagaLiberada` via SQS FIFO, devolve o Recurso ao pool, gera uma `SugestaoRepasse` persistida (idempotente por `agendamentoId`) e expõe confirmar/recusar como escritas condicionais.

## Contrato

**Persistência (V13):** `sugestao_repasse(sugestao_id UUID PK, agendamento_id BIGINT UNIQUE, recurso_id UUID, paciente_id BIGINT NULL, status PENDENTE|CONFIRMADA|ESGOTADA, criado_em, decidido_em)`. Uma linha por Vaga (agendamento); recusa reatribui `paciente_id` ao próximo candidato mantendo `PENDENTE`. `ESGOTADA` = Lista de Espera sem candidato elegível (sem sugestão pendente; a linha só existe para tornar a reentrega idempotente). V13 também adiciona `agendamento_id` a `sugestao_recusada` (PK `(recurso_id, paciente_id)` trocada por UNIQUE `(agendamento_id, paciente_id)`; linhas legadas com `agendamento_id` nulo deixam de excluir alguém) e dropa `ultima_sugestao_registrada` (substituída).

**Consumidor `VagaLiberadaSqsConsumerJob`:** molde `DecisaoSqsConsumerJob`. Kill switch `confirmasus.matching.vaga-liberada-consumer.*` (`enabled` default `false` até a fila existir no CDK — branch C). Desembrulha envelope SNS (`Message`) quando não há raw delivery. Só apaga a mensagem após sucesso ou dedup; malformada/falha → não apaga (DLQ via `maxReceiveCount`).

**`GerarSugestaoRepasse` (idempotente por `agendamentoId`):** `INSERT ... ON CONFLICT (agendamento_id) DO NOTHING`; 0 linhas = duplicata, descarta. Em nova Vaga: Alocação ATIVA do recurso → `LIBERADA`; Recurso → disponível; primeiro candidato FIFO por `criadoEm` (exclui pacientes com Alocação ATIVA e quem já recusou esta Vaga). Sem candidato → `ESGOTADA`, sem evento. Com candidato → `PENDENTE` + outbox `SugestaoRepasseGerada`. `recursoId` inexistente → exceção (mensagem vai à DLQ).

**`ConfirmarRepasse(sugestaoId)`:** `UPDATE ... SET status='CONFIRMADA' WHERE sugestao_id=? AND status='PENDENTE'`; 0 linhas → `409`. Depois cria `Alocacao` ATIVA (= `RepasseConfirmado`), Recurso indisponível, outbox `RepasseConfirmado`. Tudo numa transação.

**`RecusarSugestaoRepasse(sugestaoId, motivo)`:** atualização condicional `WHERE status='PENDENTE' AND paciente_id=<atual>`; 0 linhas → `409`. Grava `sugestao_recusada` (chave `agendamento_id`+`paciente_id`: a recusa vale só para a Vaga atual), reatribui ao próximo candidato (ou `ESGOTADA`), outbox `SugestaoRepasseRecusada` e, se houver próximo, `SugestaoRepasseGerada`.

**REST:** `GET /v1/recursos/{id}/sugestao` passa a ler a sugestão `PENDENTE` do recurso (sem recalcular nem emitir evento); `POST /v1/sugestoes-repasse/{sugestaoId}/confirmacao` e `.../recusa`. Substituem `ConfirmarAlocacao`/`RecusarSugestao`/`ConsultarSugestaoRecurso` calculado.

**Nunca:** confirmar repasse sem chamada humana explícita; critério de ordenação além de `criadoEm`; re-sugerir, na mesma Vaga, paciente que a recusou (numa nova Vaga do mesmo recurso ele volta a ser elegível).

## Fora de escopo (deferido)

- Renomear pacote/schema/classe `Alocacao` → `RepasseConfirmado` (mantida como agregado persistido; o evento já usa o nome `RepasseConfirmado`).
- CDK das filas FIFO + DLQ e remoção da fila `liberacao-agendada` (branch C `chore/cdk-filas-liberacao-repasse`).
- Bootstrap de tópico/fila no LocalStack.

## Testes

Unitários (comandos), integração Postgres real (mensagem duplicada = 1 sugestão; corrida de confirmação = 1 vencedora), consumer (envelope SNS/raw, malformada não apaga, dedup apaga).
