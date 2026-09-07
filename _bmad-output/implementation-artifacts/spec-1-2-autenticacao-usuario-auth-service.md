---
title: 'Autenticação de Usuário via auth-service'
type: 'feature'
created: '2026-09-07'
status: 'done'
review_loop_iteration: 0
baseline_commit: '9185e382a0dc141254f8e4da2b3076a3dd5cc083'
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `auth-service` só expõe `/actuator/health` — sem persistência nem emissão de token, ninguém consegue logar.

**Approach:** `auth-service` ganha `POST /v1/auth/login` (`AutenticarUsuario` em `application/query`), usuários sintéticos via migration Flyway (schema `auth`, senha BCrypt) e JWT HS256 (jjwt). `gateway-service` ganha só uma rota pública para o login. Validação de JWT/correlationId/RFC7807 no gateway ficam em `deferred-work.md` (AC-3/AC-4; este spec cobre AC-1/AC-2).

## Boundaries & Constraints

**Always:** JWT HS256, segredo via env var/Secrets Manager; `auth-service` só emite, nunca valida; claim `role` presente mas informativo; senha com hash BCrypt; erro em RFC 7807; `401` idêntico p/ usuário inexistente ou senha errada.

**Ask First:** nenhuma — decisões de infra estão no Code Map para revisão no checkpoint.

**Never:** validação JWT/correlationId/RFC7807 no gateway; RBAC por `role`; refresh/rotação de token; CRUD de usuário via API; role de banco + `REVOKE`; rota de domínio real. (todos deferidos)

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Login válido | usuário pré-cadastrado, senha correta | `200` + JWT (`sub`/`role`/`iat`/`exp`) | N/A |
| Senha errada | usuário existe, senha incorreta | `401` genérico | RFC 7807 |
| Usuário inexistente | username não cadastrado | `401` idêntico ao caso acima | RFC 7807 |

</frozen-after-approval>

## Code Map

- `auth-service/pom.xml` -- + `spring-boot-starter-data-jpa`, `postgresql` (runtime), `flyway-database-postgresql`, `jjwt-{api,impl,jackson}:0.13.0`, `spring-security-crypto` (só BCrypt)
- `.../domain/Usuario.java` -- id/username/passwordHash/role, sem framework
- `.../application/query/AutenticarUsuario.java` -- porta repositório + porta `TokenIssuer`; exceção única p/ credencial inválida
- `.../infrastructure/persistence/` -- `UsuarioJpaEntity` (schema `auth`), repositório Spring Data, adapter; `db/migration/V1__create_auth_schema_and_users.sql` (tabela `usuarios`, 3 usuários com hash BCrypt: regulador/triagem/auditor)
- `.../infrastructure/security/JwtTokenIssuer.java` -- jjwt HS256, claims `sub`/`role`/`iat`/`exp`
- `.../infrastructure/web/AuthController.java` + `@RestControllerAdvice` -- `POST /v1/auth/login`; `401` RFC 7807
- `auth-service/src/main/resources/application.yml` -- datasource (`jdbc:postgresql://postgres.filajusta.local:5432/filajusta`, credenciais do secret admin da Story 1.1), `spring.flyway.schemas=auth`, `filajusta.jwt.secret`/`expiration-seconds`
- `gateway-service/src/main/resources/application.yml:11` -- + rota pública `/v1/auth/login` → `http://auth-service.filajusta.local:8081`, sem filtro
- `infra-cdk/.../FilaJustaStack.java:360` (`buildAuthService`) -- + `serviceConnectConfiguration` (DNS `auth-service:8081`, padrão de `buildPostgresService` L308-315) + dependência no `cloudMapNamespace` (mesma corrida da Story 1.1); secret `JwtSecret`; env vars de datasource reusando `dbSecret` (L84)

## Tasks & Acceptance

**Execution:**
- [x] `auth-service/pom.xml` -- dependências (JPA, driver, Flyway, jjwt, bcrypt)
- [x] `domain/Usuario.java` + `application/query/AutenticarUsuario.java` -- caso de uso
- [x] `infrastructure/persistence/` -- entidade, repositório, migration com 3 usuários
- [x] `infrastructure/security/JwtTokenIssuer.java` -- emissão HS256
- [x] `infrastructure/web/AuthController.java` + advice -- `POST /v1/auth/login`, `401` RFC 7807
- [x] `auth-service/application.yml` -- datasource, Flyway, segredo JWT
- [x] `gateway-service/application.yml` -- rota pública de login (AD-14)
- [x] `infra-cdk/FilaJustaStack.java` -- Service Connect, secret `JwtSecret`, env vars de datasource
- [x] Teste de integração `auth-service` -- válido → `200`+JWT; senha errada/inexistente → `401` idêntico

**Acceptance Criteria:**
- Given um usuário sintético pré-cadastrado, when `POST /v1/auth/login` com credenciais corretas, then `200` com JWT assinado (AD-14)
- Given credenciais inválidas, when `POST /v1/auth/login`, then `401`

## Spec Change Log

- 2026-09-07 — Spec original excedia 1600 tokens (2969: emissão+validação JWT+correlationId+RFC7807-gateway). Validação/correlationId/RFC7807-gateway (AC-3/AC-4) → `deferred-work.md`. Esta spec: só emissão (AC-1/AC-2).

## Design Notes

- `auth-service` reusa o secret admin `PostgresSecret` (role dedicado deferido).
- `jjwt` 0.13.0, não Resource Server -- HS256 direto, sem overhead OAuth2/JWK p/ 1 endpoint.
- JWT: 3600s. Hash BCrypt pré-computado na migration (Flyway roda SQL puro).

## Verification

**Commands:**
- `mvn -q -pl auth-service -am test` -- testes de login passam
- `cd infra-cdk && cdk synth` -- sem erro
- `curl -X POST https://<gateway>/v1/auth/login -d '{"username":"regulador","password":"..."}'` -- `200`+JWT; senha errada -- `401`

**Manual checks (if no CLI):**
- `git grep` -- `JwtSecret` gerado não está no repositório

## Suggested Review Order

- Ponto de entrada do login — único endpoint público do `auth-service`.
  [`AuthController.java:24`](../../auth-service/src/main/java/com/filajusta/auth/infrastructure/web/AuthController.java#L24)

**Caso de uso e domínio (Clean Architecture)**

- Núcleo do login: busca usuário, compara hash, emite token — mesma exceção para os dois tipos de falha (não vaza qual ocorreu).
  [`AutenticarUsuario.java:29`](../../auth-service/src/main/java/com/filajusta/auth/application/query/AutenticarUsuario.java#L29)
- Agregado de domínio puro, sem framework (AD-2).
  [`Usuario.java:10`](../../auth-service/src/main/java/com/filajusta/auth/domain/Usuario.java#L10)
- Exceção única para credencial inválida (usuário inexistente ou senha errada).
  [`CredencialInvalidaException.java:10`](../../auth-service/src/main/java/com/filajusta/auth/application/query/CredencialInvalidaException.java#L10)
- Porta de saída para emissão de token (implementada em `infrastructure/security`).
  [`TokenIssuer.java:10`](../../auth-service/src/main/java/com/filajusta/auth/application/query/TokenIssuer.java#L10)
- Porta de saída para busca de usuário (implementada em `infrastructure/persistence`).
  [`UsuarioRepositorio.java:11`](../../auth-service/src/main/java/com/filajusta/auth/application/query/UsuarioRepositorio.java#L11)

**Validação e erros RFC 7807 (patch da review adversarial)**

- `@NotBlank` em username/password — sem isso, campo ausente vazava como 500 não-RFC-7807.
  [`LoginRequest.java:14`](../../auth-service/src/main/java/com/filajusta/auth/infrastructure/web/LoginRequest.java#L14)
- 3 handlers: credencial inválida → 401, requisição inválida → 400, fallback inesperado → 500 — todos RFC 7807.
  [`AuthExceptionHandler.java:28`](../../auth-service/src/main/java/com/filajusta/auth/infrastructure/web/AuthExceptionHandler.java#L28)

**Emissão de JWT**

- HS256 via jjwt; guarda contra `expiration-seconds` ≤ 0 (patch da review) evita token já expirado na emissão.
  [`JwtTokenIssuer.java:28`](../../auth-service/src/main/java/com/filajusta/auth/infrastructure/security/JwtTokenIssuer.java#L28)
- Claims emitidos (`sub`/`role`/`iat`/`exp`).
  [`JwtTokenIssuer.java:41`](../../auth-service/src/main/java/com/filajusta/auth/infrastructure/security/JwtTokenIssuer.java#L41)

**Persistência (schema `auth`, AD-9)**

- Schema próprio + tabela `usuarios` — role dedicado de banco fica deferido (só 1 schema em uso).
  [`V1__create_auth_schema_and_users.sql:4`](../../auth-service/src/main/resources/db/migration/V1__create_auth_schema_and_users.sql#L4)
- 3 usuários sintéticos com hash BCrypt pré-computado (regulador/triagem/auditor).
  [`V1__create_auth_schema_and_users.sql:21`](../../auth-service/src/main/resources/db/migration/V1__create_auth_schema_and_users.sql#L21)
- Mapeamento JPA da tabela `auth.usuarios`.
  [`UsuarioJpaEntity.java:18`](../../auth-service/src/main/java/com/filajusta/auth/infrastructure/persistence/UsuarioJpaEntity.java#L18)
- Adapter que liga a porta de domínio ao Spring Data.
  [`UsuarioRepositorioAdapter.java:23`](../../auth-service/src/main/java/com/filajusta/auth/infrastructure/persistence/UsuarioRepositorioAdapter.java#L23)

**Roteamento público no gateway (AD-14)**

- Única rota pública de domínio: login sem filtro de validação (deferida).
  [`application.yml:16`](../../gateway-service/src/main/resources/application.yml#L16)

**Infraestrutura (CDK)**

- Secret `JwtSecret` (HS256, compartilhado com o gateway, deferido).
  [`FilaJustaStack.java:223`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L223)
- Service Connect do `auth-service` (DNS interno) — como o gateway alcança o login.
  [`FilaJustaStack.java:439`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L439)
- Credenciais do Postgres (reusando o secret admin) e o novo `JwtSecret` injetados no `auth-service`.
  [`FilaJustaStack.java:165`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L165)

**Testes (peripherals)**

- Cobre a I/O Matrix completa: login válido (3 usuários), senha errada, usuário inexistente, campos ausentes.
  [`AuthLoginIntegrationTest.java:72`](../../auth-service/src/test/java/com/filajusta/auth/AuthLoginIntegrationTest.java#L72)
- Prova que a rota do gateway de fato encaminha para o `auth-service` (patch da review — sem isso, um typo na rota não seria pego por nenhum teste).
  [`AuthLoginRouteTest.java:31`](../../gateway-service/src/test/java/com/filajusta/gateway/AuthLoginRouteTest.java#L31)
- Verifica Service Connect DNS e injeção dos 3 secrets via CDK.
  [`FilaJustaStackTest.java:141`](../../infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java#L141)
- Dependências novas (JPA, Postgres driver, Flyway, jjwt, validation, BCrypt).
  [`pom.xml:44`](../../auth-service/pom.xml#L44)
- Raiz de composição: liga as portas `application.query` aos adapters de `infrastructure`.
  [`AuthServiceApplication.java:28`](../../auth-service/src/main/java/com/filajusta/auth/AuthServiceApplication.java#L28)
