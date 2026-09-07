---
title: 'Subida do Ambiente com Health-Check Público'
type: 'feature'
created: '2026-09-07'
status: 'done'
review_loop_iteration: 0
baseline_commit: '9e10f0637766a7857cc130218ba22ebd082d774c'
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A plataforma FilaJusta ainda não tem nenhuma infraestrutura provisionada — não existe VPC, cluster ECS, Postgres nem forma de confirmar que o sistema está no ar antes de construir qualquer funcionalidade de domínio.

**Approach:** Provisionar via AWS CDK (Java) a VPC (subnet pública única, 2 AZs, sem NAT Gateway), um cluster ECS Fargate com Postgres 18 containerizado, e dois serviços Spring Boot em esqueleto — `gateway-service` e `auth-service` — suficientes para expor e testar o health-check público e o bloqueio de bypass; entregar scripts `deploy`/`pause`/`destroy`. Os outros 3 serviços de domínio (`triagem-score-service`, `matching-alocacao-service`, `auditoria-service`) foram deliberadamente adiados (ver `deferred-work.md`) — seguem o mesmo padrão de esqueleto e não são necessários para validar os ACs desta story.

## Boundaries & Constraints

**Always:**
- Toda a infraestrutura como código em AWS CDK **Java** (mesmo runtime/módulo Maven do resto do projeto) — nunca TypeScript, Python ou Terraform.
- VPC com subnet pública única, 2 AZs, sem NAT Gateway (AD-12, NFR-7).
- Tasks ECS Fargate com `assignPublicIp=ENABLED`.
- Postgres 18 roda como **container na própria ECS Fargate** (decisão de custo desta story — não RDS), com storage persistente entre pause/resume.
- Security groups: só o SG do `gateway-service` alcança a porta de aplicação do `auth-service`; um SG de health-check libera só essa porta, por serviço.
- `GET /actuator/health` do `gateway-service` é público, sem token.
- `deploy` sobe tudo com um único comando, sem passo manual adicional (NFR-3). `pause` escala as tasks ECS a 0. `destroy` remove todos os recursos sem deixar órfãos cobrando (NFR-7).
- `gateway-service` e `auth-service` seguem a estrutura `domain/` (sem framework) → `application/` → `infrastructure/` desde já, mesmo com conteúdo mínimo (Additional Requirements do epics.md).
- Segredos (senha do Postgres etc.) nunca no repositório — variável de ambiente ou AWS Secrets Manager (NFR-6).
- `pom.xml` pai criado sem refatoração futura para acomodar os 3 módulos deferidos.

**Ask First:**
- Qualquer comando que crie/destrua recursos reais na conta AWS (`cdk deploy`, `cdk destroy`, `deploy.sh`, `destroy.sh`), mesmo com a conta já confirmada nesta sessão.
- Naming do multi-módulo Maven (módulo/groupId/artifactId) se ambíguo na spine.

**Never:**
- RDS gerenciado.
- NAT Gateway.
- Esqueleto/código de `triagem-score-service`, `matching-alocacao-service` ou `auditoria-service` — deferido, ver `deferred-work.md`.
- Lógica de negócio de domínio (Triagem/Matching/Auditoria) — fora de escopo desta story.
- Validação de JWT/autenticação real no gateway (isso é Story 1.2) — nesta story o gateway só expõe o health-check público; `auth-service` existe apenas como esqueleto para provar o bypass negado, sem o endpoint `POST /v1/auth/login` implementado.
- RBAC ou qualquer controle de acesso por papel.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Deploy completo | Ambiente não provisionado; executa script de deploy | VPC, cluster Postgres containerizado e tasks ECS Fargate (`gateway-service`, `auth-service`) sobem sem intervenção manual adicional | N/A |
| Health-check público | `GET /actuator/health` em `gateway-service`, sem token | `200` | N/A |
| Bypass direto ao `auth-service` | Requisição direta à porta de aplicação do `auth-service`, ignorando o gateway | Conexão recusada pelo security group | N/A |
| Pause | Script `pause` executado com ambiente no ar | Tasks ECS escalam a 0; dados do Postgres preservados | N/A |
| Destroy | Script `destroy` executado | Todos os recursos são removidos; nenhum recurso órfão continua cobrando fora da janela de demo | N/A |

</frozen-after-approval>

## Code Map

- `_bmad-output/implementation-artifacts/epic-1-context.md` -- contexto compilado do Epic 1 (decisões técnicas, requisitos, convenções)
- `_bmad-output/implementation-artifacts/deferred-work.md` -- registra o adiamento do esqueleto dos outros 3 serviços de domínio
- `_bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-06/ARCHITECTURE-SPINE.md` -- AD-8 (gateway único ponto de validação/health), AD-9 (Postgres, schema por serviço), AD-12 (rede/security groups), AD-13 (stack Java/Spring Boot)
- `.gitignore` -- já cobre `.env`/segredos/IDE; adicionar `cdk.out/` e `target/` se ainda não cobertos
- Repositório está vazio de código-fonte — esta story cria o projeto Maven multi-módulo raiz, os módulos `gateway-service`/`auth-service` e o módulo de infraestrutura CDK do zero; não há código existente para reaproveitar

## Tasks & Acceptance

**Execution:**
- [x] `pom.xml` (raiz) -- criar projeto Maven multi-módulo pai (Java 25, Spring Boot 4.1.1/Spring Cloud 2025.1.2+ como BOM), preparado para receber os 3 módulos deferidos depois -- base de build
- [x] `gateway-service/` -- esqueleto Spring Cloud Gateway com `domain/application/infrastructure` e `/actuator/health` público -- AC de health-check
- [x] `auth-service/` -- esqueleto Spring Boot com `domain/application/infrastructure` (mínimo) e `/actuator/health`, sem `POST /v1/auth/login` ainda -- estrutura seed + alvo do teste de bypass
- [x] `infra-cdk/` (módulo CDK Java) -- stack de VPC (subnet pública única, 2 AZs, sem NAT Gateway) -- AD-12
- [x] `infra-cdk/` -- cluster ECS Fargate + task/service definitions para `gateway-service` e `auth-service`, `assignPublicIp=ENABLED` -- AD-12/AD-13
- [x] `infra-cdk/` -- Postgres 18 como container na ECS Fargate (não RDS) com volume persistente -- decisão desta story
- [x] `infra-cdk/` -- security groups: só gateway alcança a porta de app do `auth-service`; SG de health-check estreito por serviço -- AD-8/AD-12
- [x] `Dockerfile` (`gateway-service/`, `auth-service/`) -- build de imagem para as tasks ECS Fargate
- [x] `deploy.sh` / `pause.sh` / `destroy.sh` -- comando único de deploy; pause escala tasks a 0; destroy remove tudo sem órfãos -- NFR-3/NFR-7
- [x] Teste de integração/smoke (script ou Cucumber) -- valida `GET /actuator/health` público via gateway e bypass ao `auth-service` recusado -- cobre a I/O Matrix

**Acceptance Criteria:**
- Given o ambiente não está provisionado, when executo o script de deploy, then a VPC (subnet pública única, 2 AZs, sem NAT Gateway), o cluster Postgres 18 e as tasks ECS Fargate (`gateway-service`, `auth-service`) sobem sem intervenção manual adicional (NFR-3, AD-12)
- Given o ambiente está no ar, when faço `GET /actuator/health` em `gateway-service`, then recebo `200` sem precisar de token (rota pública, AD-8/AD-12)
- Given os security groups configurados, when uma requisição tenta alcançar a porta de aplicação do `auth-service` diretamente (bypass), then a conexão é recusada — só o security group do gateway e a exceção de health-check têm acesso (AD-12)
- Given o ambiente está no ar, when executo `pause`, then as tasks ECS escalam a 0 sem destruir dados; when executo `destroy`, then o ambiente é destruído sem deixar recursos órfãos cobrando fora da janela de demo (NFR-7)

## Spec Change Log

- 2026-09-07 — Escopo reduzido de 5 para 2 serviços (`gateway-service`, `auth-service`); os outros 3 foram para `deferred-work.md` (token-count check do step-02). KEEP: decisões de infra (CDK Java, Postgres containerizado, rede/SGs, scripts) integrais.
- 2026-09-07 — Verificação end-to-end rodada contra a conta AWS real (118308531450/us-east-1), com autorização explícita do usuário. Os 5 cenários da I/O & Edge-Case Matrix passaram ao vivo (deploy completo, `GET /actuator/health` → 200, bypass ao auth-service recusado, pause com `desiredCount=0`, destroy sem recurso órfão). Três bugs de infraestrutura encontrados e corrigidos nesse processo, todos em `infra-cdk`:
  1. Corrida entre `PostgresService` (Service Connect) e o namespace Cloud Map do cluster (`Failed to retrieve namespace`) — corrigido com dependência explícita `postgresService.getNode().addDependency(cloudMapNamespace)`.
  2. Tasks Fargate mortas com `exec format error` — imagem construída nativamente em ARM64 (Apple Silicon) rodando em task definitions sem `runtimePlatform`, que por default é X86_64 — corrigido fixando `RuntimePlatform` ARM64 nas 3 task definitions (`arm64Platform()`), coerente com `postgres:18` (multi-arch) e mais barato que X86_64 na Fargate.
  3. Timeout de mount NFS no EFS (`mount.nfs4: mount system call failed`) — `postgresEfs.getConnections().addSecurityGroup(sgPostgresEfs)` era chamado depois que o `FileSystem` (e seus `AWS::EFS::MountTarget`) já tinham sido construídos, então os mount targets ficavam presos no security group default (sem regra de ingress). Corrigido reordenando a criação dos SGs para antes do `FileSystem` e passando `sgPostgresEfs` direto no builder (`.securityGroup(...)`). Também mantida a dependência explícita em `mountTargetsAvailable()` (padrão oficial do CDK para a corrida de propagação dos mount targets), que por si só não teria resolvido o problema #3 mas é uma proteção válida à parte.
  KEEP: a abordagem de infra (VPC sem NAT, ECS Fargate, Postgres containerizado em EFS, security groups por porta) estava correta desde o início — os 3 bugs eram todos de ordenação/timing entre recursos CDK, não de arquitetura.
- 2026-09-07 — Review adversarial (step-04: blind-hunter, edge-case-hunter, verification-gap) rodada sobre o diff completo da story. Nenhum achado de `intent_gap`/`bad_spec` (a abordagem de infra permanece correta). 6 achados classificados como `patch` e corrigidos nesta sessão: (1) `authServiceAppPortIsOnlyReachableFromGatewaySecurityGroup` usava `Match.anyValue()` e não garantia de fato que a origem fosse o SG do gateway — trocado por asserção que verifica o `GetAtt` do SG correto; (2) adicionado teste cobrindo a porta de health-check do auth-service (8090); (3) adicionado teste garantindo `RuntimePlatform` ARM64 nas 3 task definitions (regressão do bug #2 do entry anterior); (4) adicionado teste garantindo `DependsOn` do `PostgresService` no namespace Cloud Map e nos mount targets EFS (regressão do bug #1/#3 do entry anterior); (5) `deploy.sh`/`scripts/smoke-test.sh` agora tratam ENI/IP público ainda não disponíveis com mensagem clara em vez de erro opaco da AWS CLI; (6) `pause.sh` não aborta mais o loop no primeiro `update-service` que falhar — continua escalando os demais services a 0 e reporta quais falharam no final (evita deixar service rodando e cobrando por um erro parcial, NFR-7). `mvn test` (reactor completo, 13 testes em `infra-cdk` + 2 de health) e `cdk synth` revalidados após os patches — todos verdes. 6 achados reais mas fora do escopo desta story (README, preflight checks nos scripts, `correlationId`, RFC 7807, encoder de log JSON de verdade, CI de build/test) registrados em `deferred-work.md`. KEEP: os 4 ACs continuam satisfeitos ao vivo; os patches são só endurecimento de testes/scripts, sem mudança de comportamento no caminho feliz.

## Design Notes

- `pom.xml` pai (packaging `pom`) com módulos `gateway-service`, `auth-service`, `infra-cdk` nesta story; os 3 serviços deferidos e `seed-adapter` (Quarkus/Lambda) entram depois, cada um na sua própria story/epic.
- `infra-cdk`: app CDK Java padrão (`software.amazon.awscdk`), sintetizado/aplicado via `cdk deploy`/`cdk destroy` chamados por `deploy.sh`/`destroy.sh`; `pause.sh` usa `aws ecs update-service --desired-count 0` direto, sem CDK, para não recriar/destruir a stack.
- Postgres: uma task ECS Fargate, imagem `postgres:18`, volume EFS (storage efêmero da Fargate não sobrevive à parada da task); serviços conectam via DNS interno/Service Connect.

## Verification

**Commands:**
- `mvn -q compile` -- todos os módulos (`gateway-service`, `auth-service`, `infra-cdk`) compilam sem erro
- `mvn -q -pl infra-cdk -am test` -- testes do módulo CDK passam
- `cd infra-cdk && cdk synth` -- sintetiza o CloudFormation sem erro
- `./deploy.sh` -- ambiente sobe (ação real na AWS — confirmar com o usuário antes de rodar)
- `curl -sS -o /dev/null -w '%{http_code}' https://<gateway-public-endpoint>/actuator/health` -- espera `200`
- `./pause.sh` seguido de `aws ecs describe-services ...` -- espera `desiredCount=0` em todas as tasks
- `./destroy.sh` seguido de `aws cloudformation describe-stacks` / `aws ecs list-clusters` -- espera nenhum recurso remanescente

**Manual checks (if no CLI):**
- Tentar acessar diretamente a porta de aplicação do `auth-service` (não pelo gateway) usando o IP público da task ECS -- deve falhar por recusa de conexão (security group nega)

## Suggested Review Order

**Infraestrutura (VPC, ECS, Postgres/EFS) — CDK Java**

- Ponto de entrada: monta VPC, cluster, EFS, security groups e as 3 tasks/services na ordem certa.
  [`FilaJustaStack.java:79`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L79)

- VPC de subnet pública única, sem NAT Gateway (AD-12, custo).
  [`FilaJustaStack.java:175`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L175)

- SG do auth-service criado antes do EFS -- ordem importa (ver corrida abaixo).
  [`FilaJustaStack.java:96`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L96)

- SG do EFS passado direto no builder do FileSystem, não depois.
  [`FilaJustaStack.java:121`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L121)

- Postgres 18 containerizado (não RDS) com volume EFS persistente.
  [`FilaJustaStack.java:214`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L214)

- `runtimePlatform(arm64Platform())` aplicado nas 3 task definitions.
  [`FilaJustaStack.java:255`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L255)

**Corridas de infraestrutura encontradas e corrigidas no deploy ao vivo**

- ARM64 evita "exec format error" (imagem local Apple Silicon x task X86_64 default).
  [`FilaJustaStack.java:72`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L72)

- Dependência explícita no namespace Cloud Map evita "Failed to retrieve namespace".
  [`FilaJustaStack.java:149`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L149)

- Dependência em `mountTargetsAvailable()` evita timeout de mount NFS no EFS.
  [`FilaJustaStack.java:155`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L155)

**Esqueleto Clean Architecture dos serviços**

- `gateway-service`: porta única 8080, health público por padrão (sem dependência de segurança ainda).
  [`GatewayServiceApplication.java:13`](../../gateway-service/src/main/java/com/filajusta/gateway/GatewayServiceApplication.java#L13)

- `auth-service`: porta de app (8081) separada da porta de management (8090) -- permite SG restrito só na 8081.
  [`application.yml:2`](../../auth-service/src/main/resources/application.yml#L2)

**Scripts operacionais e endurecimento pós-review**

- `deploy.sh` agora avisa em vez de falhar opaco quando ENI/IP público ainda não existem.
  [`deploy.sh:36`](../../deploy.sh#L36)

- `pause.sh` não aborta mais no primeiro erro -- escala os demais services e reporta falhas (NFR-7).
  [`pause.sh:28`](../../pause.sh#L28)

**Testes (peripherals)**

- Assert do `SourceSecurityGroupId` real (não `Match.anyValue()`) -- fecha o gap de bypass da review.
  [`FilaJustaStackTest.java:56`](../../infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java#L56)

- Novos testes de regressão: health-check do auth-service, ARM64 e `DependsOn` do Postgres.
  [`FilaJustaStackTest.java:73`](../../infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java#L73)
