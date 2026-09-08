---
title: 'Story 2.2: Consulta de Triagem com Score e Fatores Contribuintes'
type: 'feature'
created: '2026-09-08'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '2bebd77bb0fd6b9c0a258ea46bfce897a715f1d7'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Hoje não existe forma de consultar uma Triagem já registrada — o Score e os fatores contribuintes só existem na resposta síncrona do `POST /v1/triagens` (Story 2.1), perdidos se o cliente não guardar aquele payload (FR-3).

**Approach:** Novo endpoint `GET /v1/triagens/{id}` no mesmo `triagem-score-service`: caso de uso `ConsultarTriagem` (`application/query`, CQRS lógico da arquitetura) busca a Triagem persistida na tabela `triagens` (já existe desde a 2.1, sem migration nova) e reconstrói o Score detalhado por fator contribuinte, mantendo a mesma fronteira de CPF (nunca expõe CPF, só `pacienteId`).

## Boundaries & Constraints

**Always:** Resposta nunca inclui CPF, só `pacienteId` (mesma fronteira da 2.1). Score e fatores retornados são os persistidos no registro original — nunca recalculados. `id` inexistente retorna `404` RFC 7807 nomeando o id. `id` não numérico no path retorna `400` RFC 7807, não `500` genérico. Leitura não muta estado (sem `@Transactional` de escrita).

**Ask First:** Nenhuma pendente — escopo e formato de resposta derivam diretamente da 2.1 e do Epic 2 Context.

**Never:** Paginação ou listagem de triagens (só busca por id único, `ListarScoresAtuais` fica para quando Epic 3 precisar). Autenticação no próprio serviço (herda do `gateway-service`, Epic 1). Rota no `gateway-service`/CDK — mesmo chore adiado em `deferred-work.md` (agora cobre `GET` também, além do `POST` da 2.1).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Triagem existente | `id` de uma Triagem registrada via POST | `200`, corpo com sinais vitais, gravidade percebida, sintomas, Score (valor + versão + fatores) e `criadoEm`, idêntico ao que o POST retornou | N/A |
| `id` inexistente | `id` numérico válido sem Triagem correspondente | `404` nomeando o id | RFC 7807 |
| `id` não numérico | Ex.: `GET /v1/triagens/abc` | `400`, sem escapar como `500` genérico | RFC 7807 |

</frozen-after-approval>

## Code Map

- `triagem-score-service/.../infrastructure/persistence/TriagemJpaEntity.java` -- hoje só expõe `getId()`; adicionar getters para os demais campos privados, necessários para reconstruir o domínio na leitura
- `triagem-score-service/.../infrastructure/persistence/TriagemJpaRepository.java` -- já herda `findById(Long)` de `JpaRepository`, sem mudança
- `auth-service/.../application/query/{UsuarioRepositorio,CredencialInvalidaException,AutenticarUsuario}.java` -- padrão de porta/exceção/caso de uso em `application/query` a espelhar
- `triagem-score-service/.../domain/{SinaisVitais,GravidadePercebida,Score,FatorContribuinte,Triagem}.java` -- reconstrução do domínio a partir dos dados persistidos (mesmos construtores validando, dado já válido)
- `triagem-score-service/.../TriagemScoreServiceApplication.java:50` -- bean `LimitesSinaisVitais` já existe, reutilizável no novo adapter de leitura
- `triagem-score-service/.../infrastructure/persistence/TriagemRepositorioAdapter.java` -- padrão de serialização JSON (Jackson 3 `ObjectMapper`) a espelhar (inverso) na leitura
- `triagem-score-service/.../infrastructure/web/{TriagemController,RegistrarTriagemResponse,TriagemExceptionHandler}.java` -- padrões de controller/DTO/RFC 7807 a estender

## Tasks & Acceptance

**Execution:**
- [x] `triagem-score-service/.../infrastructure/persistence/TriagemJpaEntity.java` -- adicionar getters para todos os campos -- necessário para reconstruir o domínio na leitura
- [x] `triagem-score-service/.../application/query/ConsultaTriagemRepositorio.java` -- nova porta, `Optional<Triagem> buscarPorId(Long id)`
- [x] `triagem-score-service/.../application/query/TriagemNaoEncontradaException.java` -- exceção de caso de uso (mirror de `CredencialInvalidaException`), mensagem nomeando o id
- [x] `triagem-score-service/.../application/query/ConsultarTriagem.java` -- caso de uso: delega à porta, lança a exceção acima quando vazio
- [x] `triagem-score-service/.../infrastructure/persistence/ConsultaTriagemRepositorioAdapter.java` -- implementa a porta via `TriagemJpaRepository.findById` + `ObjectMapper` (desserializa `sintomas`/`score_fatores` JSONB) + `LimitesSinaisVitais` (reconstrói `SinaisVitais`)
- [x] `triagem-score-service/.../infrastructure/web/ConsultarTriagemResponse.java` -- DTO de `200` (sinais vitais + gravidade percebida + score + fatores; nunca CPF)
- [x] `triagem-score-service/.../infrastructure/web/TriagemController.java` -- adicionar `GET /v1/triagens/{id}`
- [x] `triagem-score-service/.../infrastructure/web/TriagemExceptionHandler.java` -- mapear `TriagemNaoEncontradaException` → `404` e `MethodArgumentTypeMismatchException` → `400` (id não numérico)
- [x] `triagem-score-service/src/test/.../application/query/ConsultarTriagemTest.java` -- Mockito, cenários found/not-found
- [x] `triagem-score-service/src/test/.../ConsultarTriagemIntegrationTest.java` -- Testcontainers, cobre os 3 cenários da I/O Matrix ponta a ponta (registra via POST, consulta via GET)
- [x] `triagem-score-service/.../TriagemScoreServiceApplication.java` -- `@Bean ConsultarTriagem(...)` na raiz de composição (não listado explicitamente no plano; necessário pelo mesmo padrão já usado para `RegistrarTriagem`/`ResolverOuCriarPaciente`, sem `@Component` em casos de uso)
- [x] `triagem-score-service/.../infrastructure/web/TriagemController.java` -- `@PathVariable("id") Long id` (nome explícito): sem a flag `-parameters` no compilador deste módulo, `@PathVariable Long id` sem nome falhava em runtime para toda requisição -- achado durante a verificação

**Acceptance Criteria:**
- Given uma Triagem registrada via `POST /v1/triagens`, when `GET /v1/triagens/{id}` com esse id, then `200` com Score idêntico (valor, versão, fatores) ao retornado no registro
- Given um `id` sem Triagem correspondente, when `GET /v1/triagens/{id}`, then `404` RFC 7807 nomeando o id
- Given um `id` não numérico no path, when `GET /v1/triagens/{id}`, then `400` RFC 7807

### Review Findings

- [x] [Review][Patch] Corrigir typo "RfC7807" → "RFC7807" nos nomes dos métodos de teste `idInexistenteRetorna404RfC7807NomeandoOId`/`idNaoNumericoRetorna400RfC7807` [triagem-score-service/src/test/java/com/filajusta/triagem/ConsultarTriagemIntegrationTest.java:599,611]
- [x] [Review][Patch] Atualizar `application/package-info.java` para documentar o novo subpacote `application.query` (CQRS lógico introduzido por esta story), hoje só descreve `application.command` [triagem-score-service/src/main/java/com/filajusta/triagem/application/package-info.java]
- [x] [Review][Patch] ~~Declarar `ConsultarTriagem` como `final`~~ — revertido durante a aplicação: `final` impede o proxy CGLIB que `@Transactional` exige (mesmo motivo de `RegistrarTriagem` não ser `final`); mantido `public class` [triagem-score-service/src/main/java/com/filajusta/triagem/application/query/ConsultarTriagem.java:13]
- [x] [Review][Patch] Adicionar `@Transactional(readOnly = true)` em `ConsultarTriagem.consultar`, consistente com o `@Transactional` explícito de `RegistrarTriagem` [triagem-score-service/src/main/java/com/filajusta/triagem/application/query/ConsultarTriagem.java:20]
- [x] [Review][Defer] Reconstrução de domínio na leitura (`SinaisVitais`, `GravidadePercebida.fromTexto`, `Score`) pode lançar as mesmas exceções de validação usadas no `POST` se limites AD-11 mudarem, o enum de gravidade evoluir, ou o score persistido sair de 0..100 — o `@RestControllerAdvice` compartilhado mapearia isso para `400`, rotulando um problema de integridade de dado/config como erro do cliente [triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/ConsultaTriagemRepositorioAdapter.java:54-62] — deferred, pre-existing
- [x] [Review][Defer] `ConsultaTriagemRepositorioAdapter.ler(...)` não trata JSON nulo/malformado nem elemento nulo em array (`List.of` rejeita null) — cai no handler genérico de `500` (já RFC 7807, mas sem diagnóstico específico) [triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/ConsultaTriagemRepositorioAdapter.java:64-66] — deferred, pre-existing
- [x] [Review][Defer] Cobertura de teste não inclui casos de borda do mapeamento JSON do adapter (lista vazia, unicode em fator) nem ids negativos/zero/decimais — além dos 3 cenários exigidos pela I/O & Edge-Case Matrix, já cobertos [triagem-score-service/src/test/java/com/filajusta/triagem/application/query/ConsultarTriagemTest.java] — deferred, pre-existing

## Spec Change Log

## Design Notes

Reconstrução do domínio na leitura: `ConsultaTriagemRepositorioAdapter` desserializa `sintomas` (JSON array de strings) e `score_fatores` (JSON array de `{fator, contribuicao}`) com o mesmo `ObjectMapper` (Jackson 3) usado na escrita, e usa o bean `LimitesSinaisVitais` já existente para reconstruir `SinaisVitais` via seu construtor validador — os dados já são válidos (persistidos passando pela mesma validação no registro), então a reconstrução nunca deveria lançar `SinalVitalInvalidoException`.

Sem migration nova: a tabela `triagens` (schema `triagem_score`) e o índice na FK `paciente_id` já existem desde a 2.1.

## Verification

**Commands:**
- `mvn -q -pl triagem-score-service -am test` -- expected: todos os testes (unit + Testcontainers) passam, incluindo os 3 cenários da I/O Matrix
