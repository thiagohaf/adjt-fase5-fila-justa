---
title: 'Story 6.7 — Auditoria, Repasse, dados de demonstração e UI do frontend'
type: 'bugfix'
created: '2026-09-30'
status: 'in-review'
baseline_commit: '9dcdd01'
context:
  - _bmad-output/implementation-artifacts/spec-6-6-frontend-eslint-flat-config.md
---

## Intent

**Problem:** Auditoria vazia no docker-compose (consumidor SQS desligado, sem fila assinada nos tópicos SNS, `DecisaoSqsConsumerJob` sem `@Autowired` no construtor principal, cliente SQS sem endpoint override, `payload_bruto` jsonb sem `@JdbcTypeCode`); Repasse sem sugestões (sem fila de espera); dados de demonstração poluídos (UUIDs como nome, recursos de teste); botões pouco claros.

**Approach:** Fila `auditoria-decisoes.fifo` no LocalStack assinando os dois tópicos (raw delivery) e relays ligados no compose; correções no auditoria-service; `scripts/seed-demo.py` recria dados variados e consistentes via API; frontend com nome de recurso (nunca UUID), botões `.btn-*`, cabeçalho de recurso na Auditoria, ações de confirmação só em AGUARDANDO_CONFIRMACAO e mensagens de erro RFC 7807.

## Fora de escopo

Nome de paciente na API (exibido como "Paciente #N"); Cypress (débito técnico); testes unitários do auditoria-service para os fixes.

## Verificação

`npx tsc --noEmit` e `npm run lint` sem erros; seed executado; eventos persistidos em `auditoria.decisao_auditoria`; confirmação/recusa de sugestão via gateway respondem 201/200.
