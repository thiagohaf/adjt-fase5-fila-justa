# FilaJusta — adjt-fase5-fila-justa

MVP backend do FilaJusta, desenvolvido para o Hackathon FIAP Pós-Tech (Fase 5) com a metodologia BMAD (Brief → PRD → Architecture → Epics/Stories → Build).

Sistema de fila justa de priorização/matching de recursos, com autenticação dedicada via JWT (HS256) emitido por um `auth-service` e validado no `gateway-service` — único ponto de entrada do sistema.

## Arquitetura (visão rápida)

- **`gateway-service`** (Spring Cloud Gateway) — único ponto de entrada público; valida JWT em toda rota fora da allowlist pública (`/actuator/health`, `/v1/auth/login`).
- **`auth-service`** — emite JWT HS256 via `POST /v1/auth/login`, contra usuários sintéticos pré-cadastrados (schema `auth` próprio no Postgres).
- **`infra-cdk`** (AWS CDK Java) — provisiona VPC, cluster ECS Fargate, Postgres 18 containerizado e os serviços acima.
- Demais serviços de domínio (`triagem-score-service`, `matching-alocacao-service`, `auditoria-service`) entram nos próximos epics — ver `_bmad-output/implementation-artifacts/deferred-work.md`.

Detalhes de arquitetura completos em `_bmad-output/planning-artifacts/architecture/`.

## Pré-requisitos

| Ferramenta | Versão | Uso |
|---|---|---|
| [Docker](https://docs.docker.com/get-docker/) | daemon ativo | build das imagens (`gateway-service`, `auth-service`) via `cdk deploy` |
| [Java (JDK)](https://adoptium.net/) | 25 | build Maven de todos os módulos |
| [Maven](https://maven.apache.org/) | 3.9+ | build/test do reactor |
| [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) | configurado (`aws configure`) com credenciais válidas | deploy/pause/destroy, smoke test |
| [Node.js](https://nodejs.org/) + [AWS CDK CLI](https://docs.aws.amazon.com/cdk/v2/guide/getting_started.html) | CDK CLI compatível com `aws-cdk-lib` 2.268.0 | `cdk deploy`/`cdk destroy`/`cdk synth` |
| [`jq`](https://jqlang.org/) | qualquer recente | parse do `cdk-outputs.json` nos scripts |

Todos os scripts (`deploy.sh`, `pause.sh`, `destroy.sh`, `scripts/smoke-test.sh`) rodam a partir da raiz do repositório e assumem que as ferramentas acima já estão no `PATH`.

## Como rodar

Toda ação abaixo age de fato na conta AWS configurada no seu ambiente — confirme a conta/região (`aws sts get-caller-identity`) antes de rodar.

```bash
# 1. Build local (compila o reactor: gateway-service, auth-service, infra-cdk)
mvn -q -pl gateway-service,auth-service,infra-cdk -am test

# 2. Sobe o ambiente completo (VPC, ECS Fargate, Postgres, gateway-service, auth-service)
#    Um único comando, sem passo manual adicional.
./deploy.sh

# 3. Smoke test contra o ambiente recém-subido (health-check público, bypass
#    negado ao auth-service, login válido/inválido)
./scripts/smoke-test.sh

# 4. Pausa o ambiente sem destruir dados (escala as tasks ECS a 0 — evita
#    cobrança fora da janela de demo; retomar com ./deploy.sh de novo)
./pause.sh

# 5. Destroy completo (remove todos os recursos, sem deixar órfão cobrando)
./destroy.sh
```

Login de exemplo (usuário sintético pré-cadastrado via migration Flyway):

```bash
curl -sS -X POST "http://<IP-PUBLICO-GATEWAY>:8080/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"regulador","password":"regulador#2026"}'
```

O `deploy.sh` imprime o IP público do `gateway-service` ao final. Se a task ainda não tiver IP atribuído, o próprio script indica o comando AWS CLI para consultar.

## Build e testes (sem tocar AWS)

```bash
mvn -q -pl gateway-service,auth-service,infra-cdk -am test   # testes (Testcontainers para auth-service)
cd infra-cdk && cdk synth                                     # valida a stack CDK sem deployar
```

CI (GitHub Actions, `.github/workflows/ci.yml`) roda os dois comandos acima em todo push/PR.

## Documentação do projeto (BMAD)

- `_bmad-output/planning-artifacts/` — brief, PRD, arquitetura, epics.
- `_bmad-output/implementation-artifacts/` — specs de cada story (contrato de execução), `sprint-status.yaml` (tracking), `deferred-work.md` (itens conscientemente adiados), retrospectivas de epic.
