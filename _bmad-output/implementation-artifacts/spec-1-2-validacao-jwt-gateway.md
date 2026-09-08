---
title: 'Validação de JWT no Gateway (RFC 7807)'
type: 'feature'
created: '2026-09-08'
status: 'done'
review_loop_iteration: 0
baseline_commit: '686a6c7b9841ecf8804073776afb01636b667a4d'
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `gateway-service` não valida nada hoje — a Story 1.2 só entregou a emissão do JWT (`auth-service`, AC-1/AC-2); AC-3/AC-4 (gateway valida assinatura/expiração de qualquer endpoint protegido) ficaram deferidos em `deferred-work.md`.

**Approach:** `GlobalFilter` reativo em `gateway-service` valida o JWT (mesmo segredo HS256 do `auth-service`) em toda rota exceto a allowlist pública (`/actuator/health`, `/v1/auth/login`); erros formatados em RFC 7807. `correlationId` fica deferido para entrega própria (`deferred-work.md`).

## Boundaries & Constraints

**Always:** `GlobalFilter` reativo (`Ordered`), allowlist pública restrita às 2 rotas já públicas; `Authorization: Bearer <token>`; jjwt HS256, mesmo segredo (`FILAJUSTA_JWT_SECRET`, Secrets Manager, AD-14); erro `401` em RFC 7807 (`application/problem+json`), mensagem genérica idêntica para ausente/expirado/assinatura inválida (não vaza qual caso ocorreu); claim `role` não aplica RBAC (Non-Goal).

**Ask First:** nenhuma — decisões técnicas já resolvidas em `epic-1-context.md`/AD-8.

**Never:** `correlationId` (deferido, `deferred-work.md`); RBAC por `role`; refresh/rotação de token; endpoint de introspecção; alterar claims do `auth-service` (`iss`/`aud` seguem deferidos).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| JWT válido | `Bearer <jwt válido>` em rota protegida (stub de teste) | requisição encaminhada normalmente | N/A |
| Sem token | sem header `Authorization` em rota protegida | `401` | RFC 7807 |
| Token expirado | `Bearer <jwt expirado>` | `401` | RFC 7807 |
| Assinatura inválida | `Bearer <jwt assinado com outro segredo>` | `401` | RFC 7807 |
| Rota pública | `GET /actuator/health`, `POST /v1/auth/login`, sem token | não passa pela validação do filtro | N/A |

</frozen-after-approval>

## Code Map

- `gateway-service/pom.xml` -- + `jjwt-{api,impl,jackson}:0.13.0` (versão de `auth-service/pom.xml:27`)
- `gateway-service/.../infrastructure/security/JwtAuthenticationFilter.java` (novo) -- `GlobalFilter implements Ordered`; allowlist de paths públicos; parse/valida JWT (jjwt, `Keys.hmacShaKeyFor`, padrão de `JwtTokenIssuer.java:36`); escreve `401` RFC 7807 direto no `exchange` em falha
- `gateway-service/src/main/resources/application.yml` -- + `filajusta.jwt.secret: ${FILAJUSTA_JWT_SECRET}` (padrão de `auth-service/application.yml`)
- `infra-cdk/.../FilaJustaStack.java:171` -- mover `Secret jwtSecret = buildJwtSecret();` p/ antes de `buildGatewayService` (L162); passar como parâmetro; injetar `FILAJUSTA_JWT_SECRET` como ECS Secret no container `gateway-service` (L363), padrão de `buildAuthService` L432-438
- `auth-service/.../security/JwtTokenIssuer.java` (referência, não alterar) -- claims `sub`/`role`/`iat`/`exp`, HS256
- `gateway-service/.../AuthLoginRouteTest.java` (referência) -- padrão de rota stub via `@DynamicPropertySource` a reutilizar

## Tasks & Acceptance

**Execution:**
- [x] `gateway-service/pom.xml` -- adicionar dependências jjwt -- parsear/validar JWT
- [x] `infrastructure/security/JwtAuthenticationFilter.java` -- valida Bearer JWT fora da allowlist; `401` RFC 7807 em falha -- AD-8
- [x] `gateway-service/application.yml` -- `filajusta.jwt.secret` via env -- consumir segredo compartilhado
- [x] `infra-cdk/FilaJustaStack.java` -- reordenar `buildJwtSecret`, injetar `FILAJUSTA_JWT_SECRET` no container `gateway-service` -- segredo chega no runtime
- [x] Teste de integração `gateway-service` (rota protegida stub, mesmo padrão de `AuthLoginRouteTest`) -- cobre a I/O Matrix completa
- [x] Teste `FilaJustaStackTest` -- `gateway-service` recebe `FILAJUSTA_JWT_SECRET` como ECS Secret

**Acceptance Criteria:**
- Given um JWT válido emitido pelo `auth-service`, when chamo um endpoint protegido, then a requisição é encaminhada normalmente
- Given ausência de token, token expirado ou assinatura inválida, when chamo um endpoint protegido, then recebo `401` em RFC 7807
- Given `GET /actuator/health` ou `POST /v1/auth/login` sem token, when chamados, then não são bloqueados pelo filtro

## Spec Change Log

- 2026-09-08 — Spec original (JWT + correlationId + RFC7807) excedeu 1600 tokens (~1774). `correlationId` (nenhum AC da Story 1.2 exige) → `deferred-work.md`. Esta spec: só JWT + RFC 7807 (AC-3/AC-4).

## Design Notes

- Allowlist hardcoded no filtro (só 2 rotas hoje) -- sem config externa para exceção tão pequena.
- Erro escrito direto no `exchange` (`setStatusCode` + `DataBuffer` com JSON `ProblemDetail`), não `@RestControllerAdvice` -- WebFlux Gateway não passa proxy por dispatch de controller como o MVC do `auth-service`.
- Sem endpoint protegido real ainda (domínio é Epic 2+); teste usa rota stub dinâmica, padrão de `AuthLoginRouteTest`.

## Verification

**Commands:**
- `mvn -q -pl gateway-service -am test` -- testes do filtro (I/O Matrix completa) passam
- `cd infra-cdk && cdk synth` -- sem erro
- `mvn -q -pl infra-cdk -am test` -- teste do novo ECS Secret passa

## Suggested Review Order

**Filtro de validação (entrada única, AD-8)**

- Ponto de entrada: intercepta toda rota do `RouteLocator` exceto a allowlist pública; javadoc explica por que `/actuator/health` na allowlist é inerte na prática (Gateway `GlobalFilter`s só rodam para rotas que dão match).
  [`JwtAuthenticationFilter.java:105`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java#L105)
- Falha rápido no boot se `filajusta.jwt.secret` for vazio/curto demais para HS256 (patch da review — evita `WeakKeyException` opaca).
  [`JwtAuthenticationFilter.java:88`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java#L88)
- Parser com folga de clock-skew (30s, patch da review) entre os containers ECS de `auth-service`/`gateway-service`.
  [`JwtAuthenticationFilter.java:88`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java#L88)
- `getOrder()` com folga acima de `HIGHEST_PRECEDENCE` (patch da review) para um futuro filtro de `correlationId` poder rodar antes.
  [`JwtAuthenticationFilter.java:140`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java#L140)
- Normalização de barra final antes da checagem da allowlist (patch da review — comparação exata de string).
  [`JwtAuthenticationFilter.java:132`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java#L132)

**Erros RFC 7807 (patch da review — sem `@RestControllerAdvice`, WebFlux Gateway não passa proxy por dispatch de controller)**

- Escreve `401` direto no `exchange` com corpo estático e `WWW-Authenticate: Bearer` (RFC 7235, patch da review).
  [`JwtAuthenticationFilter.java:148`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java#L148)

**Segredo compartilhado (CDK)**

- `JwtSecret` criado antes do `gateway-service` (reordenado) e passado como parâmetro.
  [`FilaJustaStack.java:169`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L169)
- Injeção do mesmo `JwtSecret` como ECS Secret `FILAJUSTA_JWT_SECRET` no container `gateway-service`.
  [`FilaJustaStack.java:388`](../../infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java#L388)
- `gateway-service/application.yml`: `filajusta.jwt.secret` via variável de ambiente (NFR-6, nunca no repositório).
  [`application.yml:32`](../../gateway-service/src/main/resources/application.yml#L32)
- `pom.xml`: dependências jjwt, mesma versão do `auth-service`.
  [`pom.xml:27`](../../gateway-service/pom.xml#L27)

**Testes (peripherals)**

- Cobre a I/O Matrix completa: JWT válido, sem token, expirado, assinatura inválida, token malformado (patch da review), header sem `Bearer`, mensagem idêntica nos 3 casos originais.
  [`JwtAuthenticationFilterTest.java:39`](../../gateway-service/src/test/java/com/filajusta/gateway/JwtAuthenticationFilterTest.java#L39)
- Comentário corrigido (patch da review): o token Bearer não influencia o 404 de rota sem match — o lookup de rota falha antes de qualquer `GlobalFilter` rodar.
  [`AuthLoginRouteTest.java:113`](../../gateway-service/src/test/java/com/filajusta/gateway/AuthLoginRouteTest.java#L113)
- Verifica `FILAJUSTA_JWT_SECRET` como ECS Secret no container `gateway-service`.
  [`FilaJustaStackTest.java:196`](../../infra-cdk/src/test/java/com/filajusta/infra/FilaJustaStackTest.java#L196)
</content>
