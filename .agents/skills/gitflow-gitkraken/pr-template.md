# PR body template

Use with MCP `pull_request_create` or `gh pr create` (HEREDOC). Fill bullets from the actual diff and commits.

```markdown
## Summary
- <what changed and why>

## Test plan
- [ ] <how to verify>
```

## Title

Conventional Commits style, matching the branch type:

- `feat(scope): …` for `feature/*`
- `fix(scope): …` for `bugfix/*` or `hotfix/*`
- `chore(scope): …` for `chore/*`
- `docs: …` for `docs/*`
- `refactor(scope): …` for `refactor/*`

## Base branch

- Default: `develop`
- Hotfix / production release: `master` or `main`

## Example MCP `pull_request_create` (GitHub)

```text
provider: GITHUB
repository_organization: <org-or-user>
repository_name: <repo>
source_branch: feature/my-change
target_branch: develop
title: feat(scope): short summary
body: (from template below)
```

## Example `gh` fallback

```bash
gh pr create --base develop --title "feat(auth): implement JWT login" --body "$(cat <<'EOF'
## Summary
- Adds JWT login and role claims for Quarkus API

## Test plan
- [ ] Login returns 200 with valid credentials
- [ ] Protected routes reject missing/invalid token

EOF
)"
```