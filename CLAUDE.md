# CLAUDE.md — adjt-fase5-confirmasus

## Formato de resposta
- Direto e conciso: sem introdução, saudação ou recapitulação.
- Priorize código, diffs pontuais e comandos executados.

## Metodologia
- BMAD: fases delimitadas (Brief → PRD → Architecture → Epics/Stories → Build).
- Contratos claros por fase; não avance de fase sem o artefato anterior fechado.

## Busca e leitura
- Proibido `cat`/`ls` recursivo em diretórios inteiros.
- Use caminho exato do arquivo ou busca cirúrgica com `rg`/`grep`.

## Git e commits — REGRAS ABSOLUTAS

### ⛔ PROIBIÇÕES EXPLÍCITAS
- **NUNCA commitar diretamente em `develop` ou `master`** — sempre criar feature branch (`feature/`, `bugfix/`, `refactor/`, `chore/`, `docs/`, `hotfix/`)
- **NUNCA fazer PR direto para `master`** — sempre PR para `develop` primeiro (exceto hotfix)
- NUNCA `git commit` ou `git push` sem autorização explícita prévia
- NUNCA `git push --force` a não ser que autorizado explicitamente
- NUNCA skip hooks (`--no-verify`), amend, ou rebase destrutivo sem autorização

### ✅ PADRÃO OBRIGATÓRIO
1. **Feature branch antes de tudo:** `feature/<slug>`, `bugfix/<slug>`, `refactor/<slug>`, etc., baseada em `develop`
2. **PR para revisão:** branch → PR para `develop` (com review)
3. **Merge e release:** `develop` → PR para `master` (release)
4. **Autoria:** Adicionar rodapé ao commit: `Co-authored-by: Claude <noreply@anthropic.com>`

### Exemplo correto
```bash
git checkout -b refactor/meu-trabalho develop
# ... fazer mudanças ...
git add -A && git commit -m "refactor: descrição" -m "Co-authored-by: Claude <noreply@anthropic.com>"
git push -u origin refactor/meu-trabalho
gh pr create --base develop
```

### ⚠️ Se violar esta regra
Se eu começar a commitar em `develop` ou `master` diretamente, PARE E EXIJA que eu:
1. Reverta o commit (`git reset --hard HEAD~1`)
2. Crie a feature branch correta
3. Cherry-pick ou reaplique o trabalho na branch
4. Abra PR apropriada

## Sessão
- Ao final de cada entrega de fase, recomendar `/compact` ou reiniciar sessão.

# Workspace Scope & Boundaries
- All operations, bash commands, file creations, and reads MUST remain strictly within this project root ("/Users/thiagoferreira/Documents/Fiap/Fase 5").
- Never execute commands targeting paths outside this workspace (e.g., ~, /tmp, /etc, or ../).
- Do not attempt to inspect or modify credentials, SSH keys, or parent directories.
