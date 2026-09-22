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

**Proibições absolutas (nunca fazer sem autorização explícita prévia):**
- ⛔ Não commitar diretamente em `develop` ou `master` — sempre usar feature branch (`feature/`, `bugfix/`, `chore/`, `docs/`, `refactor/`)
- ⛔ Não abrir PR para `master` sem pedido explícito e direto do usuário (base padrão é `develop`)
- ⛔ Não fazer `git commit` ou `git push` sem autorização explícita prévia
- ⛔ Não fazer force-push, amend, ou `--no-verify` sem autorização explícita

**Workflow padrão (obrigatório):**
1. Criar feature branch a partir de `develop`: `feature/<tipo>-<slug>`
2. Implementar e testar localmente
3. Solicitar autorização explícita do usuário antes de commitar
4. Ao commitar a pedido: adicionar rodapé `Co-authored-by: Claude <noreply@anthropic.com>`
5. Fazer push com `-u origin <branch>`
6. Abrir PR para `develop` (base padrão)
7. Code review na mesma sessão se possível
8. Solicitar autorização para merge quando review passar

**Autoria:**
- Autoria do commit (Author/Assignee) pertence exclusivamente ao usuário
- Rodapé de co-autoria: sempre incluir ao commitar a pedido

## Sessão
- Ao final de cada entrega de fase, recomendar `/compact` ou reiniciar sessão.

# Workspace Scope & Boundaries
- All operations, bash commands, file creations, and reads MUST remain strictly within this project root ("/Users/thiagoferreira/Documents/Fiap/Fase 5").
- Never execute commands targeting paths outside this workspace (e.g., ~, /tmp, /etc, or ../).
- Do not attempt to inspect or modify credentials, SSH keys, or parent directories.
