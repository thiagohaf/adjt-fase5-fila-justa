---
name: gitflow-gitkraken
description: >-
  Padroniza GitFlow (branches, commits, PRs) e usa o MCP GitKraken/GitLens
  para status, branch, push, issues e PRs (incl. create/review); fallback gh.
  Use when the user asks to create a branch, open a PR, start work from an
  issue, review a PR, push, or mentions GitFlow, GitKraken, or GitLens MCP.
---

# GitFlow + GitKraken MCP

## When triggered

User asks to create/switch branch, open/update PR, start from an issue, review PRs, push, or mentions GitFlow / GitKraken / GitLens MCP.

## Prerequisites

If GitKraken MCP tools are missing or `needsAuth`:

1. Install: GitLens 17.5+ → Command Palette → `GitLens: Install GitKraken MCP Server`
   - Or CLI: ensure real GitKraken binary (`/usr/local/bin/gk`), then configure MCP with `gk mcp`
2. Auth: `\gk auth login` (use `\gk` if shell `gk` is aliased to `gitk`)
3. Tell the user once, then continue with `git` + `gh` fallbacks

Full tool list: https://help.gitkraken.com/mcp/mcp-tools-reference/

## Tool policy

1. Call `GetMcpTools` for the GitKraken server (often named `GitKraken`) before the first `CallMcpTool`.
2. Prefer MCP for git + issues + PRs (create, detail, review).
3. Create PR after push: prefer MCP `pull_request_create`; if missing/fails, use `gh pr create`.
4. If MCP is unavailable: say so once, continue with git + gh.

| Action | Prefer | Fallback |
|--------|--------|----------|
| status / diff / log / blame | `git_status`, `git_log_or_diff`, `git_blame` | `git` shell |
| fetch / pull | `git_fetch`, `git_pull` | `git fetch` / `git pull` |
| create/list branch | `git_branch` | `git branch` / `git checkout -b` |
| checkout / stash / worktree | `git_checkout`, `git_stash`, `git_worktree` | git shell |
| stage/commit | `git_add_or_commit` or `git_commit_composer` | git commit (user commit rules) |
| push | `git_push` | `git push -u` |
| issues | `issues_assigned_to_me`, `issues_get_detail` | `gh issue` |
| start from issue | `gitlens_start_work` | Workflow C below |
| list/inspect/review PRs | `pull_request_*`, `gitlens_launchpad`, `gitlens_start_review` | `gh pr` |
| **create PR** | **`pull_request_create`** | **`gh pr create`** |

Branch/base matrix: [gitflow.md](gitflow.md). PR body: [pr-template.md](pr-template.md).

## GitFlow rules

- Default base for `feature` / `chore` / `bugfix` / `docs` / `refactor`: **`develop`**
- `hotfix`: from `master` (or `main` if that is the production default); then sync to `develop`
- Never commit directly on `develop` / `master` / `main` unless the user explicitly asks
- Branch name: `<type>/<kebab-slug>` — optional ticket: `feature/ABC-123-descricao`
- Allowed types: `feature`, `bugfix`, `hotfix`, `chore`, `docs`, `refactor`
- Before creating a branch: fetch → checkout base → pull → create branch
- Derive name from the user request or issue title

## Workflows

### A — New branch

1. Infer `type` + `slug` + base from [gitflow.md](gitflow.md).
2. Validate name: `<type>/<kebab-slug>`.
3. Ensure clean status (or stash); checkout/update base; create branch (MCP `git_branch` preferred).
4. Confirm active branch to the user.

### B — Open PR

1. Status + log vs base; ensure the branch has commits ahead of base.
2. Push with upstream (MCP `git_push` preferred).
3. Create PR (base `develop`, or `master`/`main` for hotfix/release):
   - Prefer MCP `pull_request_create` with:
     - `provider`: `GITHUB` (default)
     - `repository_organization` / `repository_name` from `git remote`
     - `source_branch`: current branch; `target_branch`: base
     - Conventional `title` + `body` from [pr-template.md](pr-template.md)
   - Fallback: `gh pr create` with the same title/body (HEREDOC)
4. Return the PR URL.
5. Optional: `pull_request_get_detail` to confirm.

### C — Start from issue

1. Prefer `gitlens_start_work` when available.
2. Else: `issues_assigned_to_me` / `issues_get_detail` → name `feature/<key>-<slug>` → Workflow A.

### D — Review

1. Prefer `gitlens_start_review` or `gitlens_launchpad`.
2. Else: `pull_request_get_detail` + `pull_request_get_comments` → structured feedback.
3. Submit via `pull_request_create_review` **only** when the user explicitly asks.

## Safety

- No force-push to `develop` / `master` / `main`
- No `--no-verify`, amend, or push unless the user explicitly asks
- Do not commit secrets (`.env`, credentials, etc.)
- Create commits/PRs **only** when the user asks
- Follow the user's existing git commit and PR user rules when present
