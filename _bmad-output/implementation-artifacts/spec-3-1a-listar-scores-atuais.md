---
title: 'Story 3.1a: ListarScoresAtuais (endpoint interno em triagem-score-service)'
type: 'feature'
created: '2026-09-08'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'dd1c7bc1f0df3bf29cb8968eeeb24dc961e51a33'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `matching-alocacao-service` (Stories 3.1b/3.1c) precisa popular sua réplica local de Score em boot a frio, mas `triagem-score-service` não expõe nenhum jeito de listar os Scores atuais — `ListarScoresAtuais` foi explicitamente adiado da Story 2.2 para quando o Epic 3 precisasse (ver `spec-2-2-consulta-triagem-score-fatores-contribuintes.md`, Boundaries/Never).

**Approach:** Novo caso de uso de leitura `ListarScoresAtuais` (`application/query`) + endpoint interno `GET /internal/scores` em `triagem-score-service`, sem paginação, sem passar pelo gateway/JWT (chamada serviço-a-serviço, mesmo padrão de escopo mínimo de `ConsultarTriagem`).

## Boundaries & Constraints

**Always:** Retorna todos os Scores atuais (`pacienteId`, `score`, `occurredAt`, `eventId`) sem paginação. Endpoint não exige JWT nem passa pelo `gateway-service` — uso interno, serviço-a-serviço. Banco vazio retorna lista vazia com `200`, nunca erro.

**Ask First:** Nenhuma pendente.

**Never:** Paginação ou filtros. Exposição via rota do `gateway-service` (permanece interno). Autenticação própria no endpoint (fora do escopo — Epic 1 cobre autenticação de rotas externas, não é o caso aqui).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Consulta normal | N Triagens já registradas com Score | Lista completa dos Scores atuais | N/A |
| Banco vazio | Nenhuma Triagem registrada | Lista vazia, `200` | N/A |

</frozen-after-approval>

## Code Map

- `triagem-score-service/src/main/java/com/filajusta/triagem/application/query/ConsultarTriagem.java` -- padrão de query use case a espelhar
- `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/TriagemController.java:35` -- padrão de controller REST a espelhar (novo path `/internal/scores`)
- `triagem-score-service/src/main/java/com/filajusta/triagem/domain/Score.java` -- entidade já existente, reaproveitar na resposta
- `triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/` -- repositório JPA existente, leitura simples sem query nova complexa
- `_bmad-output/implementation-artifacts/spec-2-2-consulta-triagem-score-fatores-contribuintes.md:25` -- registro do adiamento original deste endpoint

## Tasks & Acceptance

**Execution:**
- [x] `triagem-score-service/.../application/query/ListarScoresAtuais.java` -- novo caso de uso, lista todos os Scores atuais -- fecha o gap adiado da Story 2.2
- [x] `triagem-score-service/.../infrastructure/web/ScoresInternalController.java` -- `GET /internal/scores`, expõe o caso de uso -- endpoint interno de bootstrap
- [x] Teste unitário de `ListarScoresAtuais` + teste de integração do controller cobrindo lista vazia e lista populada -- cobre a I/O Matrix (integração via `HttpClient` + Testcontainers, mesmo padrão já usado em `ConsultarTriagemIntegrationTest`/`RegistrarTriagemIntegrationTest` -- `MockMvc` não é usado em nenhum teste existente do serviço)

**Acceptance Criteria:**
- Given Triagens registradas com Score calculado, when `GET /internal/scores`, then retorna todos os Scores atuais sem paginação
- Given nenhuma Triagem registrada, when `GET /internal/scores`, then retorna lista vazia com `200`

### Review Findings

- [x] [Review][Patch] `ScoresAtuaisRepositorioAdapter.paraScoreAtual` fazia cast/deref direto de `pacienteId`/`scoreValor`/`algoritmoVersao`/`fatores` sem checar null/tipo -- uma linha malformada em `eventos_outbox` (JSON inválido, campo ausente, tipo errado) derrubava a resposta inteira de `GET /internal/scores` com NPE/ClassCastException não tratada, quebrando o bootstrap a frio inteiro do `matching-alocacao-service` por causa de UM registro ruim. Corrigido: `listarTodos()` isola a falha por linha (try/catch por evento, mesmo padrão de `RelaySnsPublisherJob`) -- loga o `eventId` da linha problemática e a pula, sem interromper a listagem; `paraScoreAtual` agora valida os campos explicitamente antes de castar, lançando uma exceção nomeando o `eventId` em vez de propagar NPE/ClassCastException cru [triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapter.java]. Coberto por `ScoresAtuaisRepositorioAdapterTest` (payload válido, campo ausente, JSON inválido, todas as linhas malformadas) [triagem-score-service/src/test/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapterTest.java]
- [x] [Review][Patch] Nenhum teste cobria "Score já publicado pelo relay continua aparecendo em `/internal/scores`" -- a query `findByEventTypeOrderByOccurredAtAsc` ignora `publicado_em` de propósito (Design Notes), mas sem teste um filtro `WHERE publicadoEm IS NULL` adicionado por engano no futuro (erro fácil, a query irmã `buscarPendentesParaAtualizar` usa exatamente esse filtro para outro propósito) faria todo Score já relayado sumir silenciosamente sem nenhum teste quebrar. Corrigido: novo teste `scoreJaPublicadoPeloRelayContinuaAparecendoNaListagem` marca a linha do outbox como publicada via `EventoOutboxRepositorio.marcarComoPublicado` (relay real não usado -- mais simples, mesmo padrão já aceito no serviço de autowireiar repositórios direto num teste de integração) e confirma que a entrada continua na listagem [triagem-score-service/src/test/java/com/filajusta/triagem/ListarScoresAtuaisIntegrationTest.java]. Achado colateral durante a aplicação: o Postgres/contexto Spring é compartilhado entre os métodos `@Test` desta classe (cache de contexto), então os 3 testes passaram a interferir entre si (contagens de linha vazando de um teste para o outro) -- corrigido com `@BeforeEach` truncando as tabelas do schema `triagem_score` antes de cada teste

## Spec Change Log

## Design Notes

Fonte de dados: `ScoresAtuaisRepositorioAdapter` lê `triagem_score.eventos_outbox` (não `triagens`) via um novo método derivado `EventoOutboxJpaRepository.findByEventTypeOrderByOccurredAtAsc("ScoreCalculado")` -- é a única tabela que carrega, na mesma linha, `event_id`/`occurred_at` (colunas próprias) junto de `pacienteId` e o `Score` completo (dentro de `payload`, jsonb). `TriagemJpaEntity` não tem `eventId`, então lê-la exigiria um join com o outbox -- contrário ao Code Map ("leitura simples sem query nova complexa"). O filtro `publicado_em` não entra na query: um Score é "atual" independente de o relay já ter publicado no SNS.

Sem deduplicação por `pacienteId`: a I/O Matrix mapeia "N Triagens registradas" -> "Lista completa dos Scores atuais" (1:1), e Boundaries não pede "mais recente por paciente". Um paciente com N Triagens aparece N vezes na lista -- decisão assumida por ausência de qualificador explícito na spec; revisitar com o usuário se `matching-alocacao-service` (3.1b/3.1c) precisar de semântica de "último Score por paciente" em vez de "todo Score já calculado".

Resposta reaproveita o mesmo padrão de DTO aninhado (`ScoreResponse`/`FatorContribuinteResponse`) já usado por `RegistrarTriagemResponse`/`ConsultarTriagemResponse` -- Score completo (valor, versão, fatores), não só o valor escalar.

## Verification

**Commands:**
- `mvn -pl triagem-score-service -am verify` -- expected: testes verdes, `ListarScoresAtuais` coberto
- Executado nesta implementação (antes dos 2 patches do code review): `mvn -pl triagem-score-service -am verify` -- BUILD SUCCESS, 104 testes (0 falhas/erros), incluindo `ListarScoresAtuaisTest` (2) e `ListarScoresAtuaisIntegrationTest` (2)
- Reexecutado após os 2 patches (verificação independente): `mvn -pl triagem-score-service -am verify` -- BUILD SUCCESS, 109 testes (0 falhas/erros)

## Suggested Review Order

**Endpoint e caso de uso**

- Entrada: `GET /internal/scores` -- delega ao caso de uso e mapeia a resposta, sem lógica própria.
  [`ScoresInternalController.java:30`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/ScoresInternalController.java#L30)

- Caso de uso de leitura: delega direto à porta, sem transformação -- fiel ao padrão de `ConsultarTriagem`.
  [`ListarScoresAtuais.java:30`](../../triagem-score-service/src/main/java/com/filajusta/triagem/application/query/ListarScoresAtuais.java#L30)

**Resiliência por linha (achado do code review)**

- `listarTodos()` isola a falha de uma linha malformada em vez de derrubar `GET /internal/scores` inteiro -- mesmo padrão de `RelaySnsPublisherJob`.
  [`ScoresAtuaisRepositorioAdapter.java:64`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapter.java#L64)

- Validação explícita dos campos do payload antes de castar -- vira exceção nomeando o `eventId`, nunca NPE/ClassCastException cru.
  [`ScoresAtuaisRepositorioAdapter.java:98`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapter.java#L98)

**Fonte de dados (decisão de design)**

- Lê `eventos_outbox`, não `triagens` -- única tabela com `eventId`/`occurredAt` do evento junto do Score, sem join novo.
  [`ScoresAtuaisRepositorioAdapter.java:16`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapter.java#L16)

- Query derivada ignora `publicado_em` de propósito -- um Score é "atual" mesmo antes do relay publicar no SNS.
  [`EventoOutboxJpaRepository.java:47`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/EventoOutboxJpaRepository.java#L47)

**Contrato de leitura**

- Porta de saída: um método, sem paginação nem filtros (Boundaries da spec).
  [`ScoresAtuaisRepositorio.java:14`](../../triagem-score-service/src/main/java/com/filajusta/triagem/application/query/ScoresAtuaisRepositorio.java#L14)

- Projeção de leitura: uma linha por evento `ScoreCalculado`, sem deduplicação por paciente (ver Design Notes).
  [`ScoreAtual.java:24`](../../triagem-score-service/src/main/java/com/filajusta/triagem/application/query/ScoreAtual.java#L24)

- DTO de resposta: mesmo padrão aninhado já usado em `RegistrarTriagemResponse`/`ConsultarTriagemResponse`.
  [`ScoreAtualResponse.java:19`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/ScoreAtualResponse.java#L19)

**Testes**

- Unitário do adapter: payload válido, campo ausente, JSON inválido, todas as linhas malformadas.
  [`ScoresAtuaisRepositorioAdapterTest.java:1`](../../triagem-score-service/src/test/java/com/filajusta/triagem/infrastructure/persistence/ScoresAtuaisRepositorioAdapterTest.java#L1)

- Integração ponta a ponta: banco vazio, N Triagens, e Score já publicado pelo relay (achado do review).
  [`ListarScoresAtuaisIntegrationTest.java:1`](../../triagem-score-service/src/test/java/com/filajusta/triagem/ListarScoresAtuaisIntegrationTest.java#L1)

- Unitário do caso de uso: delega e devolve exatamente o que a porta retornar.
  [`ListarScoresAtuaisTest.java:1`](../../triagem-score-service/src/test/java/com/filajusta/triagem/application/query/ListarScoresAtuaisTest.java#L1)
- Executado após os 2 patches do code review: `mvn -pl triagem-score-service -am verify` -- BUILD SUCCESS, 109 testes (0 falhas/erros), incluindo `ListarScoresAtuaisTest` (2), `ListarScoresAtuaisIntegrationTest` (3) e o novo `ScoresAtuaisRepositorioAdapterTest` (4)
