---
title: 'Story 6.6 — Migrar ESLint do frontend para flat config'
type: 'chore'
created: '2026-09-30'
status: 'in-progress'
baseline_commit: '6bfd7fe'
context:
  - _bmad-output/implementation-artifacts/spec-6-5-handler-415-mensagem-corpo-recursos.md
---

## Intent

**Problem:** `npm run lint` no frontend falha: ESLint 9 não lê `.eslintrc.json` (só `eslint.config.js`).

**Approach:** `eslint.config.js` (flat) com `@eslint/js` recommended + `@typescript-eslint` `flat/recommended` e globals de browser em `src/`; remove `.eslintrc.json`; declara `@eslint/js` e `globals` em devDependencies. Corrige o único erro que o lint passa a apontar (`no-self-assign` em `AuditoriaPage`: `location.href = location.href` → `location.reload()`).

## Fora de escopo

Regras adicionais (react-hooks etc.); lint dos testes Cypress.

## Verificação

`npm run lint` e `npm run type-check` sem erros.
