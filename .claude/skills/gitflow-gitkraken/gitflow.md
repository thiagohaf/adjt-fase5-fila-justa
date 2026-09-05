# GitFlow conventions

Develop-centric flow (default for this skill). `release/*` is optional.

## Branch → base → merge target

| Type | Branch pattern | Create from | PR base | After merge |
|------|----------------|-------------|---------|-------------|
| feature | `feature/<slug>` | `develop` | `develop` | done |
| bugfix | `bugfix/<slug>` | `develop` | `develop` | done |
| chore | `chore/<slug>` | `develop` | `develop` | done |
| docs | `docs/<slug>` | `develop` | `develop` | done |
| refactor | `refactor/<slug>` | `develop` | `develop` | done |
| hotfix | `hotfix/<slug>` | `master` or `main` | `master`/`main` | also sync/cherry-pick or PR into `develop` |
| release (optional) | `release/<version>` | `develop` | `master`/`main` | merge back to `develop` |

## Naming

- Format: `<type>/<kebab-slug>`
- Slug: lowercase, hyphens, no spaces
- Ticket optional: `feature/ABC-123-short-description`
- Examples: `feature/openspec-01-autenticacao-e-papeis-quarkus`, `chore/openspec-cli-setup`

## Production branch

Prefer `master` when it exists; use `main` if that is the repo default (`origin/HEAD` or sole long-lived production branch).

## Create-branch checklist

1. `git fetch` (or MCP equivalent)
2. Checkout base and pull
3. Create `<type>/<slug>`
4. Do not commit on protected bases unless explicitly requested

## Flow diagram

```text
feature/* ──PR──► develop ──release PR──► master
chore/*   ──PR──► develop
bugfix/* ──PR──► develop
hotfix/*  ──PR──► master ──► also sync to develop
```
