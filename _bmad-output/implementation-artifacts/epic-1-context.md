# Epic 1 Context: Fundação da Plataforma — Autenticação Dedicada

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Este epic sobe o sistema completo (gateway + `auth-service` + os três serviços de domínio + Postgres + mensageria) com um único comando, e estabelece a autenticação dedicada que protege todos os demais endpoints construídos nos epics seguintes. Sem ele, nenhuma outra funcionalidade é demonstrável: é o pré-requisito de infraestrutura e segurança de todo o sistema. Usuários sintéticos pré-cadastrados autenticam-se via `POST /v1/auth/login` contra um `auth-service` dedicado e recebem um JWT assinado, validado pelo `gateway-service` em cada chamada subsequente — substituindo um bearer estático fixo por um mecanismo mais próximo de produção, sem custar RBAC real (fora de escopo).

## Stories

- Story 1.1: Subida do Ambiente com Health-Check Público
- Story 1.2: Autenticação de Usuário via auth-service

## Requirements & Constraints

- O ambiente inteiro (VPC, cluster ECS Fargate, cluster Postgres, mensageria) deve subir com um único comando de deploy, sem passos manuais adicionais.
- `GET /actuator/health` de cada serviço é público (sem token), inclusive `gateway-service`.
- Nenhum serviço de domínio deve ser alcançável diretamente (bypass do gateway); apenas o gateway e a exceção estreita de health-check têm acesso às portas de aplicação.
- Scripts `deploy`/`pause`/`destroy` devem escalar tasks a 0 ou destruir o ambiente sem deixar recursos órfãos cobrando fora da janela de demo.
- Login (`POST /v1/auth/login`) com credenciais corretas retorna `200` com um JWT assinado; credenciais inválidas retornam `401`.
- Qualquer endpoint protegido, acessado sem token, com token expirado ou com assinatura inválida, retorna `401`.
- O claim `role` do JWT é puramente informativo — nenhum endpoint aplica controle de acesso por papel nesta fase; qualquer token válido acessa qualquer endpoint protegido.
- Segredos e credenciais não ficam versionados no repositório.
- Comunicação entre serviços usa contratos versionados (evita quebras silenciosas), mesmo neste epic de fundação.

## Technical Decisions

- **Decomposição de serviços:** `gateway-service`, `auth-service` (suporte transversal, sem dado de domínio clínico), `triagem-score-service`, `matching-alocacao-service`, `auditoria-service` — todos Spring Boot 4.1.1/Spring Cloud 2025.1.2+; `seed-adapter` (fora deste epic) é o único componente Quarkus/Lambda.
- **`auth-service`:** schema Postgres próprio (`auth`), usuários sintéticos pré-cadastrados via migration Flyway na subida do serviço (não via `seed-adapter`). Expõe só `POST /v1/auth/login`; emite JWT assinado (HS256), segredo compartilhado com `gateway-service` via variável de ambiente/AWS Secrets Manager (mesmo padrão de segredo compartilhado usado para os endpoints gRPC de CPF em outro epic). `auth-service` só emite token, nunca valida requisições de terceiros.
- **`gateway-service`** (Spring Cloud Gateway) é o único ponto que valida assinatura e expiração do JWT, para qualquer cliente (incluindo o `seed-adapter` de um epic posterior). Health-check e a rota de login são públicos via exceções estreitas e nomeadas de security group — isso não reabre nenhuma outra rota.
- Chamadas internas entre serviços (gRPC, consumo de fila) não passam pelo gateway — rede de confiança isolada por security group, sem RBAC.
- **Rede:** VPC com subnet pública única (2 AZs, sem NAT Gateway — decisão de custo). Tasks ECS Fargate com `assignPublicIp=ENABLED`. Isolamento por security group, não por camada de rede: só o security group do gateway alcança as portas HTTP de aplicação dos demais serviços; security group de health-check libera só essa porta, por serviço.
- **Persistência:** um único cluster PostgreSQL 18; cada serviço (incluindo `auth`) tem schema e usuário de banco próprios, com `REVOKE` explícito de grants cross-schema; regra ArchUnit em CI proíbe entidades/repositórios JPA fora do schema próprio; migrations versionadas por serviço; snapshot diário do cluster (retenção 1–3 dias).
- Clean Architecture (`domain/` sem framework → `application/query` → `infrastructure/`) aplica-se também a `auth-service`: caso de uso `AutenticarUsuario` vive em `application/query` (verifica credenciais e emite JWT, não muta estado de domínio).
- Estrutura de pastas do `auth-service`: `domain/` (Usuario sintético), `application/query/` (AutenticarUsuario), `infrastructure/web/` (`POST /v1/auth/login`), `infrastructure/persistence/` (schema `auth`, migration com usuários pré-cadastrados).
- Convenções gerais já valem desde este epic: datas ISO-8601 UTC; erros REST em RFC 7807; contratos versionados (`/v1/`); segredos via variável de ambiente/Secrets Manager, nunca no repositório; logging estruturado em JSON; `correlationId` (UUID) gerado no gateway por requisição, propagado em header HTTP.
- Custo/operação: privilegiar Free Tier e recursos pausáveis/destruíveis; manter o padrão de scripts `deploy/pause/destroy` herdado de um projeto anterior do mesmo autor.

## Cross-Story Dependencies

- Story 1.2 (autenticação) depende do ambiente de Story 1.1 estar no ar (gateway, `auth-service`, Postgres com schema `auth`).
- Epic 1 é pré-requisito de todos os demais epics: nenhum endpoint protegido de Triagem (Epic 2), Matching (Epic 3), Auditoria (Epic 4) ou o job `seed-adapter` (Epic 5) funciona sem o gateway validando o JWT emitido aqui.
