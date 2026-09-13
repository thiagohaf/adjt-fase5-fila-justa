---
title: 'Persistência da Liberação Agendada (3-4a1)'
type: 'feature'
created: '2026-09-13'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '1dfefadecc1c0300bc931860b33b5b1f0a7e70c2'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `ConfirmarAlocacao` (Story 3-3b1) não deixa nenhum rastro de que o Recurso deve voltar ao pool após o atendimento simulado — não existe hoje tabela, domínio ou porto para registrar "esta Alocação precisa ser liberada em N segundos", pré-requisito para publicar a mensagem de liberação (3-4a2, deferida) e depois consumi-la (3-4b, deferida).

**Approach:** Criar o domínio `LiberacaoAgendada` + porto `LiberacaoAgendadaRepositorio` + persistência JPA + migration; `ConfirmarAlocacao` passa a gravar, na mesma transação da confirmação, uma linha com o delay calculado por `especificidadeRank` do Recurso. Nenhuma publicação real acontece nesta story — só a escrita local.

## Boundaries & Constraints

**Always:**
- Duração calibrável por `especificidadeRank` via `filajusta.liberacao.duracao.rank-{1..4}` (formato `Duration`, ex. `PT2M`) em `application.yml`, sempre ≤15min (limite físico do `DelaySeconds` do SQS, respeitado aqui mesmo sem publicar ainda, para não exigir revalidação em 3-4a2).
- Linha de `liberacao_agendada` carrega `alocacaoId`, `recursoId`, `correlationId` original (propagado explicitamente por `ConfirmarAlocacao`; não é recuperável depois só pela tabela `alocacao`).
- Escrita de `liberacao_agendada` é atômica com a confirmação — mesma transação de `ConfirmarAlocacao` (linhas 76-100), mesmo padrão das outras 2 escritas já existentes ali (`alocacaoRepositorio.confirmar`, `recursoRepositorio.marcarIndisponivel`).
- `LiberacaoAgendadaRepositorio` expõe `buscarPendentes` (com `FOR UPDATE SKIP LOCKED`, molde de `EventoOutboxRepositorioAdapter.buscarNaoPublicados`) mesmo sem consumidor nesta story — é o contrato que 3-4a2 (relay) vai usar; não deixar método especulativo além deste.

**Ask First:** Nenhuma decisão de negócio em aberto — mapeamento de duração por `especificidadeRank` (não existe campo "tipo de Recurso") já resolvido na investigação por ser o único consistente com o domínio atual.

**Never:**
- Não implementar o job relay, a fila SQS, nem qualquer infraestrutura CDK — escopo de 3-4a2.
- Não implementar o consumidor nem a liberação real do Recurso — escopo de 3-4b.
- Não expor endpoint REST de liberação manual ou de consulta a `liberacao_agendada`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Confirmação agenda liberação | `ConfirmarAlocacao` confirma um Recurso de rank N | `liberacao_agendada` recebe 1 linha: `delay_segundos` = duração configurada para rank N, `enviado_em IS NULL` | N/A |
| Rank sem duração configurada | Recurso com `especificidadeRank` fora do mapa `filajusta.liberacao.duracao.*` | erro claro na inicialização do bean de config (fail-fast) | Falha de startup, não silenciosa em runtime por Recurso |
| Rollback da confirmação | `alocacaoRepositorio.confirmar` ou `recursoRepositorio.marcarIndisponivel` lança exceção | nenhuma linha em `liberacao_agendada` é persistida (mesma transação) | Exceção propaga normalmente, como hoje |
| `buscarPendentes` sem consumidor | linhas com `enviado_em IS NULL` acumulando (nenhum relay ainda nesta story) | método retorna a lista corretamente; ausência de consumidor é esperada e não é bug desta story | N/A |

</frozen-after-approval>

## Code Map

- `application/command/ConfirmarAlocacao.java:76-100` -- após `eventoOutboxRepositorio.salvar` (linha 97), gravar `LiberacaoAgendada` (novo) na mesma transação; `delaySegundos` resolvido a partir do `especificidadeRank` do Recurso já carregado nas linhas 80-84 via `LiberacaoDuracaoProperties`
- `domain/LiberacaoAgendada.java` (novo) -- `alocacaoId, recursoId, correlationId, delaySegundos, criadoEm, enviadoEm` (molde de `EventoOutbox.java:24-32`)
- `application/command/LiberacaoAgendadaRepositorio.java` (porto, novo) -- `salvar(LiberacaoAgendada)`, `buscarPendentes(limit)` (molde de `EventoOutboxRepositorioAdapter.buscarNaoPublicados`, `FOR UPDATE SKIP LOCKED`); `marcarComoEnviado` fica declarado no porto mas só ganha chamador em 3-4a2
- `infrastructure/persistence/LiberacaoAgendadaJpaEntity.java` + `LiberacaoAgendadaRepositorioAdapter.java` (novos) -- molde de `EventoOutboxJpaEntity.java`/`EventoOutboxRepositorioAdapter.java`
- `db/migration/V8__create_liberacao_agendada.sql` (novo) -- `alocacao_id UUID PK, recurso_id UUID, correlation_id TEXT, delay_segundos INT, criado_em TIMESTAMPTZ, enviado_em TIMESTAMPTZ NULL`
- `infrastructure/config/LiberacaoDuracaoProperties.java` (novo, `@ConfigurationProperties(prefix = "filajusta.liberacao.duracao")`) -- `Map<Integer, Duration>` chaveado por rank (`rank-1`..`rank-4`), molde de estilo de `filajusta.aging.*` (`application.yml:93-100`); validação fail-fast se rank do Recurso não tem entrada
- `application.yml` -- novo bloco `filajusta.liberacao.duracao.rank-{1..4}`
- `MatchingAlocacaoServiceApplication.java` -- wiring dos novos beans (`LiberacaoAgendadaRepositorio`, `LiberacaoDuracaoProperties`) no bean `confirmarAlocacao`
- `test/.../ConfirmarAlocacaoTest.java` -- atualizar construtor/mocks, novos testes cobrindo o cálculo de delay por rank e a gravação de `LiberacaoAgendada`
- `test/.../LiberacaoAgendadaRepositorioAdapterIntegrationTest.java` (novo) -- Postgres real, cobre `salvar`/`buscarPendentes`

## Tasks & Acceptance

**Execution:**
- [x] `db/migration/V8__create_liberacao_agendada.sql` -- tabela de agendamento
- [x] `domain/LiberacaoAgendada.java` + `application/command/LiberacaoAgendadaRepositorio.java` (porto) -- modelo + contrato
- [x] `infrastructure/persistence/LiberacaoAgendadaJpaEntity.java` + `LiberacaoAgendadaRepositorioAdapter.java` -- implementação JPA
- [x] `infrastructure/config/LiberacaoDuracaoProperties.java` -- binding via `@Value` individual por rank (`filajusta.liberacao.duracao.rank-{1..4}`) + fail-fast (desvio deliberado do Code Map original, que sugeria `@ConfigurationProperties` com `Map`; ver nota na classe)
- [x] `application/command/ConfirmarAlocacao.java` -- agendar `LiberacaoAgendada` na mesma transação
- [x] `application.yml`, `MatchingAlocacaoServiceApplication.java` -- config + wiring
- [x] Testes unitários (`ConfirmarAlocacaoTest`, `LiberacaoDuracaoPropertiesTest`) + integração Postgres real (`LiberacaoAgendadaRepositorioAdapterIntegrationTest`) cobrindo a I/O Matrix

**Acceptance Criteria:**
- Given uma Alocação confirmada para um Recurso de rank N, when a transação de `ConfirmarAlocacao` commita, then `liberacao_agendada` tem exatamente 1 linha com `delay_segundos` igual à duração configurada para rank N e `enviado_em IS NULL`
- Given `especificidadeRank` sem entrada em `filajusta.liberacao.duracao.*`, when a aplicação sobe, then falha no boot (fail-fast), não em runtime
- Given `mvn -pl matching-alocacao-service -am verify`, then testes verdes, JaCoCo domínio/aplicação ≥90%

## Verification

**Commands:**
- `mvn -pl matching-alocacao-service -am verify` -- expected: testes verdes, JaCoCo domínio/aplicação ≥90%

## Suggested Review Order

**Agendamento da liberação (entry point)**

- `ConfirmarAlocacao` calcula o delay pelo rank do Recurso e agenda `LiberacaoAgendada` na mesma transação da confirmação.
  [`ConfirmarAlocacao.java:114-118`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/application/command/ConfirmarAlocacao.java#L114-L118)

- Contrato do domínio: `alocacaoId` é a própria PK, guardas de invariante no construtor.
  [`LiberacaoAgendada.java:38`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/domain/LiberacaoAgendada.java#L38)

**Calibração da duração por rank (achado do code review)**

- Fail-fast no boot: rejeita duração ausente, ≤0, sub-segundo (`toSeconds() < 1`) e acima de 15min.
  [`LiberacaoDuracaoProperties.java:61`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/config/LiberacaoDuracaoProperties.java#L61)

- Wiring dos 4 `@Value` posicionais (`rank-1`..`rank-4`) para o bean de config.
  [`MatchingAlocacaoServiceApplication.java:149`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/MatchingAlocacaoServiceApplication.java#L149)

- Valores calibráveis por rank, molde de `filajusta.aging.*`.
  [`application.yml:113-116`](../../matching-alocacao-service/src/main/resources/application.yml#L113-L116)

**Persistência (schema, porto, adapter)**

- Schema da tabela + índice parcial de pendentes.
  [`V8__create_liberacao_agendada.sql:13-28`](../../matching-alocacao-service/src/main/resources/db/migration/V8__create_liberacao_agendada.sql#L13-L28)

- `FOR UPDATE SKIP LOCKED` na leitura de pendentes; `marcarEnviado` guardado por `enviado_em IS NULL` (uso real só a partir de 3-4a2).
  [`LiberacaoAgendadaJpaRepository.java:23-34`](../../matching-alocacao-service/src/main/java/com/filajusta/matching/infrastructure/persistence/LiberacaoAgendadaJpaRepository.java#L23-L34)

**Testes (prova end-to-end contra o binding real, achado do code review)**

- E2E real (Spring context + Testcontainers + `application.yml` real): prova que o wiring dos 4 `@Value` posicionais está na ordem certa.
  [`AlocacaoControllerIntegrationTest.java:153-159`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/AlocacaoControllerIntegrationTest.java#L153-L159)

- Orquestração com mocks: delay correto por rank, nenhuma escrita nos caminhos de erro.
  [`ConfirmarAlocacaoTest.java:73`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/application/command/ConfirmarAlocacaoTest.java#L73)

- Invariantes do domínio isoladas (achado do code review: antes só cobertas indiretamente).
  [`LiberacaoAgendadaTest.java:17`](../../matching-alocacao-service/src/test/java/com/filajusta/matching/domain/LiberacaoAgendadaTest.java#L17)
</content>
