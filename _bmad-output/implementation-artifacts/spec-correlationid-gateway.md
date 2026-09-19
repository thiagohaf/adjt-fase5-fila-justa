---
title: 'CorrelationId no Gateway'
type: 'feature'
created: '2026-09-08'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'b92c431987674103ceb10e258d489280af5d17cd'
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `epic-1-context.md` declara a convenção "`correlationId` (UUID) gerado no gateway por requisição, propagado em header HTTP" como válida desde o Epic 1, mas nenhum serviço a implementa ainda. Sem isso, não há como correlacionar logs de uma mesma requisição entre `gateway-service`, `auth-service` e (a partir do Epic 2) os serviços de domínio. Item deferido duas vezes em `deferred-work.md` (Story 1.1 e Story 1.2-gateway) pelo mesmo motivo ("só faz sentido com mais rotas") — cada rota nova adiada aumenta o custo de retrofit.

**Approach:** `GlobalFilter` reativo dedicado em `gateway-service` (mesmo padrão de `JwtAuthenticationFilter`): lê o header `X-Correlation-Id` da requisição recebida; se ausente ou em branco, gera um UUID novo. Propaga o valor tanto na requisição encaminhada ao serviço downstream quanto em toda resposta ao cliente — incluindo os `401` do `JwtAuthenticationFilter`, o que exige rodar com precedência maior (antes) dele.

## Boundaries & Constraints

**Always:** `GlobalFilter` reativo (`Ordered`), `getOrder()` estritamente menor que `JwtAuthenticationFilter.getOrder()` (`HIGHEST_PRECEDENCE + 100`) para que toda resposta, inclusive `401`, carregue o header; nome do header `X-Correlation-Id` (convenção de mercado — `epic-1-context.md` não fixa um nome); um único valor determinístico por requisição (se o cliente mandar múltiplos valores, usar/gerar um só, tanto no forward quanto na resposta).

**Ask First:** nenhuma — decisão técnica já resolvida (nome do header, ordenação) na intenção acima.

**Never:** alterar `JwtAuthenticationFilter`'s lógica de validação de JWT (só o comentário de `getOrder()`, que hoje descreve este filtro como hipotético/futuro e fica desatualizado assim que este existir); logging estruturado usando o `correlationId` (fora de escopo — só geração/propagação via header); propagação para `auth-service` ou demais serviços via chamada de rede própria (a propagação já ocorre naturalmente porque o header viaja na requisição HTTP encaminhada pelo `RouteLocator`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Sem header | requisição sem `X-Correlation-Id` | UUID novo gerado; presente na requisição encaminhada e na resposta | N/A |
| Com header | requisição com `X-Correlation-Id: abc-123` | mesmo valor (`abc-123`) propagado, sem gerar novo | N/A |
| Header em branco | `X-Correlation-Id: ` (string vazia) | tratado como ausente — UUID novo gerado | N/A |
| Resposta de erro (401 do filtro JWT) | requisição sem JWT válido a rota protegida | resposta `401` carrega o mesmo `X-Correlation-Id` da requisição | N/A |

</frozen-after-approval>

## Code Map

- `gateway-service/src/main/java/com/filajusta/gateway/infrastructure/web/CorrelationIdFilter.java` (novo) -- `GlobalFilter implements Ordered`, `getOrder() = Ordered.HIGHEST_PRECEDENCE` (roda antes de tudo, inclusive `JwtAuthenticationFilter`)
- `gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java:140-146` (`getOrder()`) -- comentário atual descreve este filtro como hipotético ("um futuro filtro de correlationId... precisa rodar ANTES deste") -- atualizar para referenciar `CorrelationIdFilter` como fato consumado, não mudar `Ordered.HIGHEST_PRECEDENCE + 100`
- `gateway-service/src/test/java/com/filajusta/gateway/JwtAuthenticationFilterTest.java` (referência, não alterar) -- padrão de stub HTTP local (`HttpServer`) + `@DynamicPropertySource` redefinindo a rota `[0]` para simular endpoint de domínio protegido; reusar o mesmo padrão para o novo teste, com o stub também ecoando o header `X-Correlation-Id` recebido no corpo da resposta para permitir assertar a propagação ao downstream
- `gateway-service/src/main/resources/application.yml` -- não precisa mudar (nenhuma config nova; filtro é `@Component` autoconfigurado)

## Tasks & Acceptance

**Execution:**
- [x] `gateway-service/.../infrastructure/web/CorrelationIdFilter.java` -- `GlobalFilter` que lê/gera `X-Correlation-Id`, propaga na requisição mutada (`ServerHttpRequest.mutate().header(...)`, que substitui valores existentes) e na resposta (`exchange.getResponse().getHeaders().set(...)`, antes de `chain.filter`) -- cobre a intenção
- [x] `JwtAuthenticationFilter.java:140-146` -- atualizar comentário de `getOrder()` para referenciar `CorrelationIdFilter` (não mudar o valor numérico) -- evita comentário desatualizado (mesma categoria de achado da retrospectiva do Epic 1: `package-info.java` que prometia código futuro)
- [x] `gateway-service/src/test/java/com/filajusta/gateway/CorrelationIdFilterTest.java` (novo) -- cobre a I/O Matrix completa (sem header, com header, header em branco, propagação para downstream via stub, presença no `401` do filtro JWT)

**Acceptance Criteria:**
- Given uma requisição sem `X-Correlation-Id`, when ela passa pelo gateway (rota pública ou protegida), then a resposta contém `X-Correlation-Id` com um UUID válido
- Given uma requisição com `X-Correlation-Id: abc-123`, when ela passa pelo gateway, then a resposta e a requisição encaminhada ao downstream carregam o mesmo valor `abc-123`
- Given uma requisição a uma rota protegida sem JWT válido, when o `JwtAuthenticationFilter` responde `401`, then essa resposta também carrega `X-Correlation-Id`

## Spec Change Log

- 2026-09-08: revisão adversarial (3 camadas) sobre o diff original encontrou 6 achados classificados como "patch" (fix trivial, sem renegociar Intent/Boundaries). Todos endereçados:
  1. teste de header multi-valor ausente -- adicionado (`multiplosValoresNoHeader_usaOPrimeiroDeFormaDeterministica`)
  2. assertion fraca em `semHeader_...` (só checava existência da chave no corpo do stub, não o valor propagado) -- corrigida para comparar o valor ecoado pelo downstream com o header da resposta
  3. AC1 ("rota pública ou protegida") só cobria rota protegida -- adicionado teste na rota pública real `/v1/auth/login`
  4. `CorrelationIdFilter` não validava o valor recebido do cliente (CR/LF, tamanho) antes de reusá-lo, risco de `500` em vez de cair no fallback -- filtro estendido para tratar como inválido (gera UUID novo) qualquer valor fora de ASCII imprimível sem caracteres de controle, até 128 caracteres; testes cobrindo ambos os casos
  5. teste usava string literal `"X-Correlation-Id"` em vez de `CorrelationIdFilter.CORRELATION_ID_HEADER` -- corrigido
  6. README sem documentar o contrato do header -- adicionado parágrafo na seção "Arquitetura" (nova) do `README.md`
  Verification re-executada após os patches: `mvn -q -pl gateway-service -am test` -- 20/20 testes verdes (`CorrelationIdFilterTest` 9, `JwtAuthenticationFilterTest` 7, `AuthLoginRouteTest` 3, `HealthEndpointTest` 1).

## Design Notes

Ordenação: `Ordered.HIGHEST_PRECEDENCE` é o valor mínimo possível (`Integer.MIN_VALUE`) — não sobra margem para outro filtro rodar antes deste. Aceitável porque nenhum outro cross-cutting concern do gateway precisa rodar antes da geração do `correlationId` (é o primeiro filtro do pipeline, por definição). Diferente de `JwtAuthenticationFilter`, que deixou margem (`+100`) porque sabia que este filtro viria depois — mesmo raciocínio não se aplica aqui, pois nada precede o `correlationId`.

## Verification

**Commands:**
- `mvn -q -pl gateway-service -am test` -- testes do novo filtro + suíte existente (`JwtAuthenticationFilterTest`, `AuthLoginRouteTest`, `HealthEndpointTest`) continuam verdes

## Suggested Review Order

**Filtro de correlationId (entrada única, primeiro do pipeline)**

- Ponto de entrada: lê/gera o `X-Correlation-Id`, valida o valor recebido (ASCII imprimível, até 128 chars) antes de reusá-lo, propaga na requisição mutada e na resposta antes de `chain.filter`.
  [`CorrelationIdFilter.java:52`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/web/CorrelationIdFilter.java#L52)
- Guard de validação (patch da review) -- trata header ausente, em branco ou inválido (CR/LF, oversized) da mesma forma, evitando `500` do framework HTTP.
  [`CorrelationIdFilter.java:79`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/web/CorrelationIdFilter.java#L79)
- `getOrder()` no valor mínimo possível -- roda antes de todo o resto, inclusive o `401` do `JwtAuthenticationFilter`.
  [`CorrelationIdFilter.java:68`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/web/CorrelationIdFilter.java#L68)

**Ordenação entre filtros (fronteira com Story 1.2)**

- Comentário atualizado para referenciar o `CorrelationIdFilter` como fato consumado (antes descrevia como hipotético) -- valor numérico do `getOrder()` inalterado.
  [`JwtAuthenticationFilter.java:139`](../../gateway-service/src/main/java/com/filajusta/gateway/infrastructure/security/JwtAuthenticationFilter.java#L139)

**Documentação**

- Novo parágrafo (patch da review) documentando o contrato do header para futuros integradores.
  [`README.md:3`](../../README.md#L3)

**Testes (peripherals)**

- Cobre a I/O Matrix completa: sem header, com header, header em branco, múltiplos valores, rota pública real (`/v1/auth/login`), header inválido (oversized e CR/LF), presença no `401`.
  [`CorrelationIdFilterTest.java:152`](../../gateway-service/src/test/java/com/filajusta/gateway/CorrelationIdFilterTest.java#L152)
- Teste de header com CR/LF roda no nível do filtro isolado (`MockServerHttpRequest`), já que o cliente HTTP real rejeita esse valor antes de sair de processo.
  [`CorrelationIdFilterTest.java:310`](../../gateway-service/src/test/java/com/filajusta/gateway/CorrelationIdFilterTest.java#L310)
