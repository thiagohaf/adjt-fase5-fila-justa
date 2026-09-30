---
title: 'Story 6.2 — CDK: fila SQS FIFO de VagaLiberada para liberacao-repasse-service'
type: 'chore'
created: '2026-09-29'
status: 'in-progress'
baseline_commit: 'c6e4d15'
context:
  - _bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md (AD-3, AD-6)
  - _bmad-output/implementation-artifacts/spec-6-1-vaga-liberada-sugestao-repasse.md
---

## Intent

**Problem:** `VagaLiberadaSqsConsumerJob` (Story 6.1) existe, mas a fila que ele consome não existe no CDK; a fila legada `liberacao-agendada` (Story 3-4a2) ficou sem uso desde o PR #117.

**Approach:** CDK cria a fila SQS FIFO `vaga-liberada-liberacao-repasse.fifo` + DLQ FIFO (`maxReceiveCount=5`, `visibilityTimeout=60s`), assinada no tópico SNS FIFO `agendamento-confirmacao-eventos.fifo` (envelope SNS mantido, sem raw delivery — o consumidor desembrulha) com filtro de corpo `eventType=VagaLiberada`. `LiberacaoRepasseServiceTaskRole` ganha `grantConsumeMessages`. Output `VagaLiberadaQueueUrl`. Remove `liberacao-agendada`/`-dlq`, output e permissão.

## Fora de escopo

- Ligar o consumidor (`CONFIRMASUS_MATCHING_VAGA_LIBERADA_CONSUMER_ENABLED`): o deploy ECS do liberacao-repasse-service segue deferido; a queue URL sai como output.
- Bootstrap LocalStack/docker-compose.

## Testes

`ConfirmaSusStackTest`: fila FIFO + DLQ (5 tentativas, 60s), subscription SNS→SQS sem raw delivery, `sqs:ReceiveMessage` na task role, fila legada ausente.
