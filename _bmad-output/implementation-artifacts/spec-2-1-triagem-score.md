---
title: 'Story 2.1: Registro de Triagem com Score de Prioridade Calculado'
type: 'feature'
created: '2026-09-08'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '9098cd107e79c0c1513a8ac1474da1ccf0db7a2b'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Não existe hoje nenhum serviço de domínio no FilaJusta — só autenticação (Epic 1). Um Profissional de Triagem não tem como registrar dados clínicos de um Paciente nem obter o Score de prioridade que deveria ordenar a fila (FR-1, FR-3).

**Approach:** Novo serviço `triagem-score-service` (Clean Architecture, Spring Boot MVC, schema Postgres próprio `triagem_score`) expõe `POST /v1/triagens`: valida CPF e sinais vitais, resolve/cria o Paciente por CPF de forma idempotente, calcula um Score determinístico versionado (algoritmo v1, `[ASSUMPTION]`) detalhado por fator contribuinte, persiste tudo na mesma transação e grava um evento `ScoreCalculado` em tabela outbox — sem publicar de fato. Verificável via `mvn test`, sem depender de ambiente AWS no ar; deploy (rota no gateway + CDK) é chore separado (`deferred-work.md`).

## Boundaries & Constraints

**Always:** CPF validado (formato/checksum) e sinais vitais dentro das faixas de AD-11 antes de qualquer cálculo de Score, retornando `400` no primeiro campo inválido encontrado. CPF já visto sempre reutiliza o mesmo Paciente/ID interno — nunca cria um segundo. `POST /v1/triagens` sempre retorna `201` de forma síncrona, mesmo que o evento de outbox nunca seja lido por ninguém ainda. Score determinístico: mesmos inputs + mesma versão de algoritmo ⇒ mesmo valor, sempre. CPF em texto claro nunca sai de `triagem-score-service` — respostas e eventos referenciam só `pacienteId`.

**Ask First:** Nenhuma pendente — formato de `sintomas`/`gravidade percebida` e fórmula do Score já confirmados com o usuário nesta sessão (ver Design Notes).

**Never:** Publisher/relay real do outbox para SNS, rota no gateway, wiring no CDK (deferido, ver Design Notes). Endpoints gRPC `ResolveCpfParaId`/`ObterCpfMascarado` (AD-7) — sem consumidor ainda. Autenticação/JWT no próprio serviço — confia inteiramente no `gateway-service` (mesmo modelo do `auth-service`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path, CPF novo | CPF nunca visto, sinais vitais e gravidade válidos | `201`, cria Paciente implícito, Score detalhado por fator | N/A |
| CPF já usado | CPF de uma Triagem anterior | `201`, reutiliza o mesmo `pacienteId` — nunca cria um segundo Paciente | N/A |
| Sinal vital ausente/fora de faixa | Ex.: PAS ≤ PAD, ou FC fora de 40–200 | `400` nomeando o campo inválido, sem calcular Score | RFC 7807 |
| CPF inválido | Formato ou checksum incorreto | `400`, sem calcular Score | RFC 7807 |
| Mesmos inputs, 2 Triagens distintas | Dados clínicos idênticos, mesma versão de algoritmo | Score idêntico nas duas respostas, versão registrada | N/A |
| Downstream sem consumidor ainda | `matching-alocacao-service`/`auditoria-service` não existem nesta fase | `201` com Score, evento gravado em `eventos_outbox`, resposta nunca bloqueia | N/A |

</frozen-after-approval>

## Code Map

- `pom.xml:79` -- descomentar `<module>triagem-score-service</module>` (já preparado, sem outro refactor)
- `auth-service/pom.xml` -- mirror para o pom do novo módulo (parent, packaging, spring-boot-maven-plugin); acrescentar `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core`
- `auth-service/src/main/resources/db/migration/V1__create_auth_schema_and_users.sql` -- padrão de migration a espelhar (schema próprio, `CREATE SCHEMA IF NOT EXISTS` primeiro statement)
- `auth-service/src/main/resources/application.yml` -- padrão de datasource/flyway.schemas/portas (app + `management.server.port` separado) a espelhar
- `auth-service/.../application/query/AutenticarUsuario.java` -- forma do use case (portas injetadas por construtor, zero Spring em `domain/`) a espelhar para `RegistrarTriagem`
- `auth-service/.../infrastructure/web/AuthExceptionHandler.java` -- `@RestControllerAdvice` RFC 7807 a espelhar para `TriagemExceptionHandler`
- `auth-service/.../infrastructure/persistence/{UsuarioJpaEntity,UsuarioJpaRepository,UsuarioRepositorioAdapter}.java` -- padrão adapter a espelhar para `Paciente`/`Triagem`/`EventoOutbox`
- `_bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-06/ARCHITECTURE-SPINE.md:200-209` -- esqueleto de pacotes autoritativo do serviço

## Tasks & Acceptance

**Execution:**
- [x] `pom.xml` -- descomentar módulo -- habilita o build do novo serviço
- [x] `triagem-score-service/pom.xml` -- mirror de `auth-service/pom.xml` + deps JPA/Postgres/Flyway
- [x] `triagem-score-service/.../domain/{Cpf,SinaisVitais,GravidadePercebida,Score,FatorContribuinte}.java` -- value objects com validação de invariantes no construtor (sem Spring)
- [x] `triagem-score-service/.../domain/CalculadorDeScore.java` -- algoritmo determinístico v1 (Design Notes), versão fixa `"v1"`
- [x] `triagem-score-service/.../application/command/{RegistrarTriagem,ResolverOuCriarPaciente}.java` + ports (`PacienteRepositorio`, `TriagemRepositorio`, `EventoOutboxRepositorio`)
- [x] `triagem-score-service/.../infrastructure/persistence/db/migration/V1__create_triagem_schema.sql` -- schema `triagem_score`, tabelas `pacientes`, `triagens` (score + fatores em JSONB), `eventos_outbox`
- [x] `triagem-score-service/.../infrastructure/persistence/*` -- entities/repos/adapters JPA para as 3 tabelas
- [x] `triagem-score-service/.../infrastructure/web/{TriagemController,RegistrarTriagemRequest,RegistrarTriagemResponse,TriagemExceptionHandler}.java` -- `POST /v1/triagens`
- [x] `triagem-score-service/src/main/resources/application.yml` -- `filajusta.triagem.limites.*` (faixas AD-11), datasource (schema `triagem_score`), porta app `8082`/mgmt `8091`
- [x] `triagem-score-service/src/test/java/.../domain/*Test.java` -- unit tests dos value objects + `CalculadorDeScoreTest` cobrindo a I/O Matrix
- [x] `triagem-score-service/src/test/java/.../RegistrarTriagemIntegrationTest.java` -- Testcontainers, cobre os 6 cenários da I/O Matrix ponta a ponta

**Acceptance Criteria:**
- Given um CPF novo e sinais vitais/gravidade válidos, when `POST /v1/triagens`, then `201` com Score e fatores contribuintes, e o Paciente criado implicitamente
- Given um CPF já registrado, when uma nova Triagem é registrada, then o mesmo `pacienteId` é reutilizado
- Given dois cálculos com os mesmos inputs e mesma versão de algoritmo, then o Score é idêntico e a versão fica registrada
- Given a Triagem é registrada, then uma linha aparece em `eventos_outbox` na mesma transação, sem afetar o tempo de resposta do `201`

## Spec Change Log

## Design Notes

Fórmula do Score v1 (`[ASSUMPTION]`, confirmada com o usuário — não validada clinicamente, versionada para evoluir): cada sinal vital contribui uma subnota `0..1` pela distância normalizada à faixa de AD-11 (quanto mais longe do centro da faixa, mais próxima de `1`); `gravidade percebida` contribui um peso fixo por nível (`LEVE=0, MODERADA=0.33, GRAVE=0.66, CRITICA=1`). Score final = média ponderada das subnotas × 100, arredondado. Cada subnota vira um `FatorContribuinte` nomeado na resposta (ex.: `{"fator": "frequencia_cardiaca", "contribuicao": 0.4}`). `sintomas` é lista livre de strings, armazenada e retornada, mas não entra na fórmula (nenhuma taxonomia de peso por sintoma existe nos artefatos de planejamento).

Idempotência do Paciente: `ResolverOuCriarPaciente` faz `SELECT ... WHERE cpf = ?` antes de `INSERT`, dentro da mesma transação de `RegistrarTriagem` -- evita corrida de duas Triagens simultâneas para o mesmo CPF criando dois Pacientes (constraint `UNIQUE(cpf)` como rede de segurança).

Outbox sem relay: a tabela `eventos_outbox` grava `{eventId (UUID v4), eventType="ScoreCalculado", occurredAt, payload}` na mesma transação, mas nada a lê ainda -- publisher/SNS fica para quando houver consumidor (Epic 3+).

Deploy fora desta spec: rota `/v1/triagens` no `gateway-service` e `buildTriagemScoreService(...)` no CDK (mirror de `buildAuthService`) viraram chore separado em `deferred-work.md` -- não bloqueiam `mvn test`, só a execução ao vivo na AWS (que já estava adiada, ver `sprint-status.yaml` action item 2 do Epic 1).

## Verification

**Commands:**
- `mvn -q -pl triagem-score-service -am test` -- expected: todos os testes (unit + Testcontainers) passam, incluindo os 6 cenários da I/O Matrix

## Suggested Review Order

**Fluxo de registro (orquestração)**

- Entry point: orquestra validação → resolução idempotente do Paciente → cálculo do Score → persistência → outbox, tudo em uma transação.
  [`RegistrarTriagem.java:58`](../../triagem-score-service/src/main/java/com/filajusta/triagem/application/command/RegistrarTriagem.java#L58)

- Único endpoint REST do serviço; delega inteiramente ao use case acima.
  [`TriagemController.java:30`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/TriagemController.java#L30)

**Algoritmo de Score (v1, `[ASSUMPTION]`)**

- Média das subnotas por sinal vital + peso de gravidade, normalizada para 0-100, versão fixa registrada.
  [`CalculadorDeScore.java:25`](../../triagem-score-service/src/main/java/com/filajusta/triagem/domain/CalculadorDeScore.java#L25)

**Invariantes de domínio e correções da revisão adversarial**

- Checksum de CPF (rejeita sequências com dígitos repetidos, que passariam o checksum ingênuo).
  [`Cpf.java:16`](../../triagem-score-service/src/main/java/com/filajusta/triagem/domain/Cpf.java#L16)

- Patch: `Double.isNaN` explícito antes da comparação de faixa — `NaN` passava despercebido porque toda comparação `<`/`>` com `NaN` é `false`.
  [`FaixaVital.java:31`](../../triagem-score-service/src/main/java/com/filajusta/triagem/domain/FaixaVital.java#L31)

- Patch: elemento `null` em `sintomas` agora vira `400` nomeado, em vez de `NPE` de `List.copyOf` (achado do edge-case-hunter).
  [`Triagem.java:38`](../../triagem-score-service/src/main/java/com/filajusta/triagem/domain/Triagem.java#L38)

**Idempotência sob concorrência**

- Patch: corrida de duas Triagens simultâneas para o mesmo CPF novo — a perdedora do `INSERT` agora recupera o Paciente da vencedora em vez de `500` opaco.
  [`ResolverOuCriarPaciente.java:30`](../../triagem-score-service/src/main/java/com/filajusta/triagem/application/command/ResolverOuCriarPaciente.java#L30)

**Erros RFC 7807**

- Mapeia cada exceção de domínio para `400` nomeando o campo; `HttpMessageNotReadableException` cobre corpo ausente/malformado.
  [`TriagemExceptionHandler.java:36`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/web/TriagemExceptionHandler.java#L36)

**Persistência**

- Patch: índice explícito na FK `paciente_id` (Postgres não indexa FK automaticamente).
  [`V1__create_triagem_schema.sql:39`](../../triagem-score-service/src/main/resources/db/migration/V1__create_triagem_schema.sql#L39)

- Adapter padrão espelhando `auth-service` (porta em `application`, implementação em `infrastructure`).
  [`PacienteRepositorioAdapter.java:1`](../../triagem-score-service/src/main/java/com/filajusta/triagem/infrastructure/persistence/PacienteRepositorioAdapter.java#L1)

**Config e testes**

- Faixas fisiológicas (AD-11) centralizadas aqui, não hardcoded no domínio; porta app `8082`/mgmt `8091`.
  [`application.yml:1`](../../triagem-score-service/src/main/resources/application.yml#L1)

- Módulo habilitado no reactor Maven.
  [`pom.xml:76`](../../pom.xml#L76)

- Cobre os 6 cenários da I/O Matrix ponta a ponta via Testcontainers, incluindo os 4 novos casos da revisão (gravidade inválida, sinais vitais ausentes, corpo malformado, payload do outbox verificado por campo).
  [`RegistrarTriagemIntegrationTest.java:1`](../../triagem-score-service/src/test/java/com/filajusta/triagem/RegistrarTriagemIntegrationTest.java#L1)

- Testes Mockito isolando a corrida de concorrência sem precisar de Testcontainers.
  [`ResolverOuCriarPacienteTest.java:1`](../../triagem-score-service/src/test/java/com/filajusta/triagem/application/command/ResolverOuCriarPacienteTest.java#L1)
