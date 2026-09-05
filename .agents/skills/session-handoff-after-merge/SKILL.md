---
name: session-handoff-after-merge
description: >-
  After a PR merge confirmation, switches to develop, equalizes with
  origin/develop, and generates a paste-ready handoff prompt for the next
  Cursor session. If the user has not confirmed the PR was merged, asks once
  and waits before doing anything. Use when the user says the PR was merged,
  asks for handoff, finalizar sessão, equalizar develop, or when PR work
  appears done without merge confirmation.
---

# Session handoff after PR merge

## When triggered

- User says the PR was merged (`já fiz o merge`, `PR merged`, `mergeado`, etc.)
- User asks for handoff / finalizar sessão / preparar próxima sessão / equalizar develop
- PR work appears finished but merge was not confirmed

## Gate do merge (hard stop)

**Do not checkout, pull, or write a handoff until merge is explicitly confirmed.**

1. If the user already confirmed merge in this conversation → proceed.
2. If not confirmed → ask once: `O PR já foi mergeado?` and **stop**. Wait for the reply.
3. If the user says it was **not** merged yet → say the pós-merge handoff stays pending and **stop**.

Acceptable confirmations include clear phrases like: `já fiz o merge`, `PR merged`, `mergeado`, `sim, já mergeei`.

## Equalizar develop

Prefer GitKraken MCP (`git_fetch`, `git_checkout`, `git_pull`) when available; otherwise use `git` in the shell.

1. Note the current feature branch name (for the handoff).
2. `git fetch origin`
3. `git checkout develop`
4. `git pull origin develop` (prefer fast-forward)
5. Confirm to the user: current branch is `develop` and it matches `origin/develop`

### Dirty working tree

If checkout fails because of local changes: report the blocker and ask whether to stash or commit. Do **not** force checkout or discard changes.

### Do not

- Delete the feature branch (local or remote)
- Force-push
- Create commits unless the user explicitly asks

## Handoff prompt

After equalize succeeds, output **one** markdown block ready to copy-paste into the next Cursor session. Fill from conversation context; keep it concrete and short.

```markdown
# Handoff — próxima sessão

## Resumo do PR
- O que foi entregue: …
- PR: <url ou número, se conhecido>
- Branch da feature: <nome>

## Estado atual
- Branch: `develop` (atualizada com `origin/develop`)
- Working tree: limpa / pendências: …

## Próximos passos sugeridos
1. …
2. …

## Contexto técnico
- Arquivos-chave: …
- Decisões tomadas: …
- Pendências / riscos: …

## Prompt para a próxima sessão
Continue a partir de `develop` (já equalizada). Objetivo: …
Contexto: …
Comece por: …
```

End the reply with that block so the user can copy it in one action.
