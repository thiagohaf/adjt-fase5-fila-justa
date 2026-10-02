---
title: 'Story 6.3 — LocalStack/docker-compose: fila FIFO de VagaLiberada + consumidor ligado'
type: 'chore'
created: '2026-09-29'
status: 'in-progress'
baseline_commit: '0844e40'
context:
  - _bmad-output/implementation-artifacts/spec-6-1-vaga-liberada-sugestao-repasse.md
  - _bmad-output/implementation-artifacts/spec-6-2-cdk-fila-vaga-liberada.md
---

## Intent

**Problem:** o ciclo vaga → sugestão não roda localmente: a fila de VagaLiberada só existe no CDK e o consumidor está desligado.

**Approach:** script `localstack/init/01-vaga-liberada.sh` (ready.d, idempotente) cria o tópico SNS FIFO `agendamento-confirmacao-eventos.fifo`, a fila FIFO + DLQ e a subscription (sem raw delivery, filtro de corpo `eventType=VagaLiberada`), espelhando o CDK. `docker-compose.yml` monta o script, liga o outbox relay do agendamento-confirmacao-service (topic ARN + endpoint) e o `VagaLiberadaSqsConsumerJob` do liberacao-repasse-service (queue URL + endpoint).

## Fora de escopo

Deploy ECS; renomeação Alocacao→RepasseConfirmado.

## Verificação

LocalStack sobe, fila/tópico/subscription existem; ciclo E2E: janela expira → VagaLiberada → SugestaoRepasse.

## Resultado da verificação (2026-09-30)

E2E via gateway (8080) OK: recurso + lista-espera + agendamento → janela aberta/expirada (SQL) → LIBERADO → VagaLiberada (SNS FIFO → SQS FIFO) → `SugestaoRepasse` PENDENTE (paciente da lista) → `POST /v1/sugestoes-repasse/{id}/confirmacao` 201 (Alocação ATIVA; sugestão deixa de aparecer).

Achados corrigidos nesta story:
- `VagaLiberadaSqsConsumerJob` importava `com.fasterxml.jackson` (Jackson 2); o bean do Boot 4 é `tools.jackson` → o serviço não subia com o consumidor ligado (testes unitários não exercitam o wiring). Migrado para `tools.jackson.databind`.
- `localstack/localstack:latest` exige licença → fixado em `4.12.0` (igual aos testes); volume movido para `/var/lib/localstack` (`/tmp/localstack` dava "Device or resource busy").
- Healthcheck do LocalStack usava `kinesis` (não habilitado) → `sqs list-queues`; serviços dependem de `service_healthy`.
