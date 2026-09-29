# ConfirmaSUS — adjt-fase5-confirmasus

MVP do ConfirmaSUS, desenvolvido para o Hackathon FIAP Pós-Tech (Fase 5) com a metodologia BMAD (Brief → PRD → Architecture → Epics/Stories → Build).

Sistema de confirmação ativa de consultas e exames do SUS: notifica o paciente numa janela de confirmação, libera a vaga em caso de recusa/expiração, e sugere o repasse a quem está na Lista de Espera — por ordem de chegada, nunca por gravidade ou qualquer critério clínico (decisão automatizada de priorização é vedada por restrição legal). Autenticação dedicada via JWT (HS256) emitido por um `auth-service` e validado no `gateway-service` — único ponto de entrada do sistema.

## Arquitetura (visão rápida)

- **`gateway-service`** (Spring Cloud Gateway) — único ponto de entrada público; valida JWT em toda rota fora da allowlist pública (`/actuator/health`, `/v1/auth/login`); gera e propaga um `X-Correlation-Id` (UUID) em toda requisição.
- **`auth-service`** — emite JWT HS256 via `POST /v1/auth/login`, contra usuários sintéticos pré-cadastrados (schema `auth` próprio no Postgres).
- **`agendamento-confirmacao-service`** — dono de `Paciente`/`Agendamento`/Janela de Confirmação: confirmação, recusa, expiração e liberação de vaga.
- **`matching-alocacao-service`** — dono de `Recurso`/`Lista de Espera`/`Alocação`: Sugestão de Repasse FIFO pura por ordem de chegada e confirmação/recusa do repasse pelo Gestor. Cumpre hoje o papel de "liberação e repasse" da arquitetura, mas mantém o nome herdado — a renomeação para `liberacao-repasse-service` prevista em `ARCHITECTURE-SPINE.md` não foi executada (débito de nomenclatura conhecido).
- **`auditoria-service`** — log auditável, só leitura + consumidor de eventos dos dois serviços acima.
- **`seed-adapter`** — CLI Java standalone (não é Quarkus/Lambda), carrega dados sintéticos via gateway.
- **`infra-cdk`** (AWS CDK Java) — provisiona VPC, cluster ECS Fargate, Postgres 18 containerizado e os serviços de domínio.
- **`frontend/`** — SPA React/TypeScript com as 4 jornadas de usuário (confirmação, dashboard, repasse, auditoria); não fazia parte do escopo original do PRD (backend-only), mas foi construída como demonstração.

O antigo `triagem-score-service` (produto anterior, calculava Score de Prioridade Clínica) foi decomissionado por restrição legal — sem substituto, removido do repositório.

Detalhes de arquitetura completos em `_bmad-output/planning-artifacts/architecture/`.

## Pré-requisitos

| Ferramenta | Versão | Uso |
|---|---|---|
| [Docker](https://docs.docker.com/get-docker/) | daemon ativo | build das imagens via `cdk deploy` (AWS) ou `docker-compose` (local) |
| [Java (JDK)](https://adoptium.net/) | 25 | build Maven de todos os módulos |
| [Maven](https://maven.apache.org/) | 3.9+ | build/test do reactor |
| [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) | configurado (`aws configure`) com credenciais válidas | deploy/pause/destroy, smoke test (só para o caminho AWS) |
| [Node.js](https://nodejs.org/) + [AWS CDK CLI](https://docs.aws.amazon.com/cdk/v2/guide/getting_started.html) | CDK CLI compatível com `aws-cdk-lib` 2.268.0 | `cdk deploy`/`cdk destroy`/`cdk synth` |
| [`jq`](https://jqlang.org/) | qualquer recente | parse do `cdk-outputs.json` nos scripts |

Todos os scripts (`deploy.sh`, `pause.sh`, `destroy.sh`, `scripts/smoke-test.sh`) rodam a partir da raiz do repositório e assumem que as ferramentas acima já estão no `PATH`.

## Como rodar localmente (docker-compose, sem AWS)

Via para desenvolvimento/demo sem tocar a conta AWS — ver [DOCKER_COMPOSE_README.md](DOCKER_COMPOSE_README.md) para o passo a passo completo (build, seed, troubleshooting).

```bash
docker-compose build
docker-compose up
```

## Como rodar na AWS

Toda ação abaixo age de fato na conta AWS configurada no seu ambiente — confirme a conta/região (`aws sts get-caller-identity`) antes de rodar.

```bash
# 1. Build local (compila o reactor: 6 serviços Spring Boot + infra-cdk)
mvn -q test

# 2. Sobe o ambiente completo (VPC, ECS Fargate, Postgres, todos os serviços)
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

Note: `deploy.sh`/`pause.sh`/`destroy.sh`/`scripts/smoke-test.sh` hoje só provisionam/gerenciam `auth-service`, `gateway-service` e `agendamento-confirmacao-service` no ECS — `matching-alocacao-service` e `auditoria-service` ainda não têm `FargateService`/rota de gateway no CDK (deploy real na AWS continua deferido para esses dois; rodam hoje só via `docker-compose`).

## Build e testes (sem tocar AWS)

```bash
mvn -q test                    # testes do reactor (6 serviços Spring Boot, Testcontainers)
cd seed-adapter && mvn -q test # seed-adapter fica fora do reactor (build/runtime próprio)
cd infra-cdk && cdk synth      # valida a stack CDK sem deployar
```

CI (GitHub Actions, `.github/workflows/ci.yml`) roda os comandos acima em todo push/PR que toque algum dos serviços.

## Frontend

SPA React/TypeScript em `frontend/` — ver [frontend/README.md](frontend/README.md) e [frontend/ARCHITECTURE.md](frontend/ARCHITECTURE.md).

## Documentação do projeto (BMAD)

- `_bmad-output/planning-artifacts/` — brief, PRD, arquitetura, epics.
- `_bmad-output/implementation-artifacts/` — specs de cada story (contrato de execução), `sprint-status.yaml` (tracking), `deferred-work.md` (itens conscientemente adiados), retrospectivas de epic.
