---
title: 'Story 6.5 — 415 sem Content-Type e mensagem genérica de corpo ilegível'
type: 'bugfix'
created: '2026-09-30'
status: 'in-progress'
baseline_commit: '6bfd7fe'
context:
  - _bmad-output/implementation-artifacts/spec-6-4-teste-contexto-consumidor-vaga-liberada.md
---

## Intent

**Problem:** achados ao reproduzir a recusa de sugestão (E2E de 2026-09-30). `POST /v1/sugestoes-repasse/{id}/recusa` sem `Content-Type` devolve 500 (`HttpMediaTypeNotSupportedException` cai no fallback `Exception`); e o 400 de corpo ilegível cita `codigoRecurso, especificidadeRank e disponivel`, campos de `POST /internal/recursos`, mesmo em endpoints que não os usam (o handler é global).

**Approach:** em `RecursosExceptionHandler`, novo handler `HttpMediaTypeNotSupportedException` → 415 (RFC 7807) e mensagem do 400 de corpo ilegível sem citar campos.

## Fora de escopo

Demais serviços; a recusa já devolve 409 corretamente (não era bug).

## Verificação

`SugestaoRepasseIntegrationTest`: recusa sem `Content-Type` → 415; recusa sem corpo → 400 sem citar `codigoRecurso`. `mvn clean test` do módulo verde.
