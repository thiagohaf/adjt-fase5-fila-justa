# CLAUDE.md — adjt-fase5-fila-justa

## Formato de resposta
- Direto e conciso: sem introdução, saudação ou recapitulação.
- Priorize código, diffs pontuais e comandos executados.

## Metodologia
- BMAD: fases delimitadas (Brief → PRD → Architecture → Epics/Stories → Build).
- Contratos claros por fase; não avance de fase sem o artefato anterior fechado.

## Busca e leitura
- Proibido `cat`/`ls` recursivo em diretórios inteiros.
- Use caminho exato do arquivo ou busca cirúrgica com `rg`/`grep`.

## Git e commits
- NUNCA `git commit` ou `git push` sem autorização explícita prévia.
- Autoria do commit (Author/Assignee) pertence exclusivamente ao usuário.
- Ao commitar a pedido, adicionar rodapé: `Co-authored-by: Claude <noreply@anthropic.com>`.

## Sessão
- Ao final de cada entrega de fase, recomendar `/compact` ou reiniciar sessão.
