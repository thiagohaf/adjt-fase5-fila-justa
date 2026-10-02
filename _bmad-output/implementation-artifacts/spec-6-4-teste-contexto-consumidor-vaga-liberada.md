---
title: 'Story 6.4 — Teste de contexto Spring do consumidor de VagaLiberada'
type: 'chore'
created: '2026-09-30'
status: 'in-progress'
baseline_commit: 'a249cb5'
context:
  - _bmad-output/implementation-artifacts/spec-6-3-localstack-fila-vaga-liberada.md
---

## Intent

**Problem:** o bug de Jackson 2 vs 3 no `VagaLiberadaSqsConsumerJob` passou pelos testes unitários e só apareceu ao subir o serviço com `enabled=true` (Story 6.3).

**Approach:** `VagaLiberadaSqsConsumerJobContextIntegrationTest` (`@SpringBootTest` + Postgres e LocalStack via Testcontainers) sobe o contexto com o consumidor ligado, publica na fila FIFO o envelope embrulhado pelo SNS (sem raw delivery) e verifica que a `SugestaoRepasse` PENDENTE é gerada e a mensagem removida.

## Fora de escopo

Fluxo SNS→SQS (coberto no relay); deploy ECS.

## Verificação

`mvn clean test -Dtest=VagaLiberadaSqsConsumerJobContextIntegrationTest` verde; o teste falha se o bean do consumidor não subir.
