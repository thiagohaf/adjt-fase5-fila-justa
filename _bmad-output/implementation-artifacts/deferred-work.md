# Deferred Work

<!-- Append-only. Cada entrada nasce de um split de escopo feito durante bmad-build (step-02, token-count check). Não editar entradas existentes; apenas acrescentar novas. -->

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Esqueleto Clean Architecture (domain/application/infrastructure) + Dockerfile + registro de task/service ECS para `triagem-score-service`, `matching-alocacao-service` e `auditoria-service`.
  evidence: A spec da Story 1.1 excedeu o alvo de 900–1600 tokens ao cobrir os 5 serviços; o escopo foi reduzido a `gateway-service` + `auth-service` (suficientes para testar os ACs de deploy, health-check público e bypass negado). Os outros 3 serviços seguem o mesmo padrão de esqueleto e podem ser criados como preparação da primeira story de cada epic que os toca (Epic 2 → `triagem-score-service`, Epic 3 → `matching-alocacao-service`, Epic 4 → `auditoria-service`), ou como uma story de infra rápida antes disso se o time preferir antecipar.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Workflow de GitHub Actions (`workflow_dispatch`, credenciais via OIDC) para automatizar `deploy`/`pause`/`destroy` do ambiente, com opção de destroy agendado como rede de segurança contra recurso órfão esquecido.
  evidence: Levantado pelo usuário durante a verificação end-to-end da Story 1.1 (rodada local e manual). Fora do escopo da spec 1.1 (que só previu scripts locais); vale como story/chore de tooling própria mais adiante, se o time decidir que a rotina manual local não é suficiente.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: README na raiz do repositório documentando pré-requisitos (Docker, AWS CLI, `jq`, CDK CLI/Node, Java 25) e como rodar `deploy.sh`/`pause.sh`/`destroy.sh`/`scripts/smoke-test.sh`.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 1.1. Real e útil para quem pegar o projeto depois, mas não bloqueia nenhum AC desta story; o `README.md` atual só tem o nome do projeto.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Preflight checks em `deploy.sh`/`pause.sh`/`destroy.sh`/`scripts/smoke-test.sh` validando `aws`, `cdk`, `jq` e o daemon do Docker antes de rodar, com mensagem clara em vez do erro genérico do comando interno.
  evidence: Achado pelo review adversarial (blind-hunter). Real (confirmado nesta sessão: o primeiro deploy real precisou que o Docker fosse iniciado manualmente), mas é robustez incremental, não um defeito que quebra algum AC desta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Implementar `correlationId` (UUID gerado no gateway por requisição, propagado em header) conforme convenção declarada em `epic-1-context.md`.
  evidence: Achado pelo review adversarial (blind-hunter): a convenção é citada como válida "desde este epic" mas não implementada nem registrada como adiada. Legítimo, porém não há nenhuma rota de domínio passando pelo gateway ainda nesta story (`routes: []` — só health-check) — só faz sentido a partir da Story 1.2, quando o roteamento de fato existir.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Implementar respostas de erro REST no formato RFC 7807 (`ProblemDetail`/exception handler) em `gateway-service`/`auth-service`, conforme convenção declarada em `epic-1-context.md`.
  evidence: Achado pelo review adversarial (blind-hunter). Legítimo, mas nenhum endpoint desta story produz erro de aplicação ainda (só `/actuator/health`); primeiro caso real de erro é o `401` de `POST /v1/auth/login` na Story 1.2.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Trocar o pattern manual de log "JSON" do Logback (`application.yml` de `gateway-service`/`auth-service`) por um encoder JSON de verdade (ex.: `logstash-logback-encoder`).
  evidence: Achado pelo review adversarial (blind-hunter): o pattern atual monta a string manualmente (`%m` sem escaping), então uma mensagem de log com aspas/barra invertida/quebra de linha produz JSON inválido. Baixo risco nesta story (serviços-esqueleto, log mínimo), mas vale corrigir antes de haver volume de log real.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Workflow de CI (GitHub Actions ou similar) rodando `mvn test` e `cdk synth` em push/PR.
  evidence: Achado pelo review adversarial (blind-hunter). Distinto do item de CI de deploy/pause/destroy já registrado acima (esse é sobre build/test, não sobre provisionar AWS); hoje toda verificação da Story 1.1 é manual/local, sem pipeline automatizado.
