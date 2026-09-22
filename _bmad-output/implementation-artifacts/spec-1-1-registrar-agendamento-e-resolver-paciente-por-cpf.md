---
title: 'Story 1.1: Registrar Agendamento e Resolver Paciente por CPF'
type: 'feature'
created: '2026-09-18'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: 'c87a1f3c0a11dce7a0c054e4103037ddb7a8d933'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `triagem-score-service` (produto ConfirmaSus, descartado) precisa virar `agendamento-confirmacao-service` (AD-1) antes de qualquer código novo; e não existe hoje nenhum jeito de registrar um Agendamento associado a um Paciente resolvido por CPF sem o CPF circular além do serviço de ingestão.

**Approach:** Renomear/podar o serviço (mantendo `Paciente`/`Cpf`/`ResolverOuCriarPaciente`, descartando `Triagem`/`Score`/domínio correlato sem migração de dado), depois adicionar o agregado `Agendamento` (estado inicial `AGUARDANDO_JANELA`) com um endpoint REST de registro que resolve/cria o Paciente internamente e valida `recursoId`/`dataHoraAgendamento`, e subir o construto CDK correspondente.

## Boundaries & Constraints

**Always:**
- CPF em texto claro nunca sai de `agendamento-confirmacao-service`; apenas `pacienteId` (UUID interno) circula em respostas/eventos futuros.
- Toda rejeição de entrada inválida (CPF, `recursoId`, `dataHoraAgendamento`) retorna `422` via `ProblemDetail` (RFC 7807, mesmo formato de `RecursosExceptionHandler`), sem persistir Paciente nem Agendamento.
- `recursoId` é validado apenas por formato (UUID bem formado, não nulo/vazio) — **não** há checagem de existência contra o catálogo real de `Recurso` (que vive em `liberacao-repasse-service`); não há tabela/endpoint local de Recurso neste serviço (AD-1: sem sincronização em runtime entre os dois catálogos).
- `dataHoraAgendamento` deve ser um instante futuro (`> now()`) — passado ou ausente é `422`.
- Reaproveitar `Paciente`/`Cpf`/`CpfInvalidoException`/`ResolverOuCriarPaciente`/`PacienteRepositorio` como estão (mover de pacote, não redesenhar).
- Módulo Maven, artifactId, pacote Java (`com.confirmasus.triagem` → `com.confirmasus.agendamento`) e schema Postgres (`triagem_score` → `agendamento_confirmacao`) são renomeados por completo — nenhuma referência textual a `triagem`/`Triagem`/`Score` sobrevive fora de comentários de changelog/migration antiga já removida.
- Migration Flyway é reescrita do zero para o novo schema (só tabelas `pacientes` e `agendamentos`) — nenhuma migração de dado do schema antigo.
- Construto CDK novo segue o molde de `buildAuthService` (`infra-cdk/.../ConfirmaSusStack.java:608-674`): Fargate arm64, SG de app liberando só `sgGatewayApp`, SG de health público, rota registrada em `gateway-service/src/main/resources/application.yml`, segredos via Secrets Manager.

**Ask First:**
- Qualquer decisão que reabra a necessidade de sincronizar/validar `recursoId` contra `liberacao-repasse-service` em runtime (contradiria AD-1).
- Qualquer decisão de implementar o endpoint gRPC `ResolverOuCriarPaciente` ou a regra de SG cruzada com `liberacao-repasse-service` nesta story (adiado para Story 2.1 — decisão humana já tomada, não reabrir sem novo input do humano).

**Never:**
- Não criar `infrastructure/grpc/` nem qualquer regra de SG/porta gRPC nesta story — `liberacao-repasse-service` ainda não existe como construto CDK (fica para Epic 2 Story 2.1).
- Não publicar nenhum evento de domínio via outbox/SNS nesta story — não existe evento definido para "Agendamento registrado" no AD-3; outbox entra a partir da Story 1.2.
- Não reaproveitar `RelaySnsPublisherJob`/`RelaySnsClientConfig`/`EventoOutbox*` do serviço antigo nesta story (fora de escopo — sem publisher para reaproveitar ainda).
- `TriagemScoreServiceTaskRole` em `ConfirmaSusStack.java` não é reaproveitada com o mesmo nome/permissão (`score-calculado.fifo`) — remover ou deixar renomeação real para quando o outbox existir (Story 1.2); não criar role fantasma sem uso nesta story.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Registro feliz, paciente novo | CPF válido inédito + `recursoId` UUID válido + `dataHoraAgendamento` futura | `201`, Paciente criado, Agendamento persistido com `pacienteId`/`recursoId`/`dataHoraAgendamento`/status `AGUARDANDO_JANELA` | N/A |
| Registro feliz, paciente já existente | Mesmo CPF de um Paciente já persistido + dados válidos | `201`, Paciente reaproveitado (mesmo `pacienteId`), novo Agendamento criado | N/A |
| CPF inválido | CPF com formato/checksum inválido | Nenhum Paciente/Agendamento criado | `422` ProblemDetail |
| `recursoId` malformado/ausente | `recursoId` nulo, vazio ou não-UUID | Nenhum Agendamento persistido | `422` ProblemDetail |
| `dataHoraAgendamento` no passado ou ausente | Instante `<= now()` ou campo ausente | Nenhum Agendamento persistido | `422` ProblemDetail |

</frozen-after-approval>

## Code Map

- `pom.xml:72-83` -- lista de módulos; renomear `triagem-score-service` para `agendamento-confirmacao-service`.
- `triagem-score-service/` (raiz do módulo) -- renomear diretório/artifactId/`<name>`/`finalName` (`pom.xml:15,141`) para `agendamento-confirmacao-service`.
- `.../com/confirmasus/triagem/domain/{Paciente,Cpf,CpfInvalidoException}.java` -- mover para pacote `com.confirmasus.agendamento.domain`, manter lógica.
- `.../application/command/{ResolverOuCriarPaciente,PacienteRepositorio}.java` -- mover para `com.confirmasus.agendamento.application.command`, manter lógica.
- `.../infrastructure/persistence/{PacienteJpaEntity,PacienteJpaRepository,PacienteRepositorioAdapter}.java` -- mover de pacote; `PacienteJpaEntity` muda `schema="triagem_score"` para `schema="agendamento_confirmacao"`.
- Todo o domínio Triagem/Score listado na investigação (domain/application/infrastructure/web/testes de `Triagem`, `Score`, `GravidadePercebida`, `SinaisVitais`, `CalculadorDeScore`, `TriagemController`, `ScoresInternalController`, `TriagemExceptionHandler`, `RelaySnsClientConfig`, `RelaySnsPublisherJob`, `EventoOutbox*`) -- deletar por completo, incluindo os testes correspondentes.
- `src/main/resources/db/migration/V1__create_triagem_schema.sql`, `V2__add_relay_columns_eventos_outbox.sql` -- deletar; criar `V1__create_agendamento_confirmacao_schema.sql` novo, só com `pacientes` (mesmo desenho da tabela atual) e `agendamentos` (`id`, `paciente_id`, `recurso_id`, `data_hora_agendamento`, `status`, timestamps).
- `application.yml` do serviço -- `spring.flyway.schemas`/datasource schema `triagem_score` → `agendamento_confirmacao`.
- `matching-alocacao-service/.../web/AlocacaoController.java:51-59`, `.../RecursosExceptionHandler.java` -- molde de controller `@Valid`/`ProblemDetail` a seguir; adaptar handler novo para incluir `422` (caso ainda inédito no projeto).
- `agendamento-confirmacao-service/.../infrastructure/web/` (novo) -- `AgendamentoController` (`POST /v1/agendamentos`), `RegistrarAgendamentoRequest`, `RegistrarAgendamentoResponse`, `AgendamentoExceptionHandler`.
- `agendamento-confirmacao-service/.../application/command/RegistrarAgendamento.java` (novo) -- orquestra `ResolverOuCriarPaciente` + validação de `recursoId`/`dataHoraAgendamento` + persistência do Agendamento.
- `agendamento-confirmacao-service/.../domain/Agendamento.java` (novo) -- agregado com estado `AGUARDANDO_JANELA` (demais estados chegam nas próximas stories, AD-4).
- `infra-cdk/.../ConfirmaSusStack.java:608-674` (`buildAuthService`) -- molde do novo `buildAgendamentoConfirmacaoService`; `:125-157` (SGs existentes) -- molde para `sgAgendamentoConfirmacaoApp`/`sgAgendamentoConfirmacaoHealth`.
- `infra-cdk/.../ConfirmaSusStack.java:282-292` (`TriagemScoreServiceTaskRole`) -- remover (sem outbox/SNS nesta story; não recriar role fantasma).
- `auth-service/Dockerfile` -- molde para `agendamento-confirmacao-service/Dockerfile` (build multi-stage Maven, mesmo padrão).
- `gateway-service/src/main/resources/application.yml:22-26` -- molde de rota; adicionar rota nova para `agendamento-confirmacao-service`.

## Tasks & Acceptance

**Execution:**
- [x] `pom.xml` -- atualizar módulo `triagem-score-service` → `agendamento-confirmacao-service` -- AD-1
- [x] `triagem-score-service/` -- renomear diretório e artefato Maven para `agendamento-confirmacao-service`, pacote `com.confirmasus.triagem` → `com.confirmasus.agendamento` -- AD-1
- [x] `.../domain/{Paciente,Cpf,CpfInvalidoException}.java`, `.../application/command/{ResolverOuCriarPaciente,PacienteRepositorio}.java`, `.../infrastructure/persistence/Paciente*.java` -- mover de pacote sem alterar lógica, testes `CpfTest`/`ResolverOuCriarPacienteTest` acompanham -- reaproveitamento AD-1
- [x] Domínio/aplicação/infraestrutura/testes de Triagem/Score (lista completa no Code Map) -- deletar por completo -- AD-1 (sem endpoint/classe remanescente)
- [x] `db/migration/V1__create_agendamento_confirmacao_schema.sql` -- criar migration nova com `pacientes` + `agendamentos`; deletar migrations antigas -- AD-10, sem migração de dado
- [x] `application.yml` do serviço -- schema `agendamento_confirmacao` -- AD-10
- [x] `domain/Agendamento.java` -- criar agregado com estado inicial `AGUARDANDO_JANELA` -- AD-4
- [x] `application/command/RegistrarAgendamento.java` -- comando que chama `ResolverOuCriarPaciente`, valida `recursoId` (formato) e `dataHoraAgendamento` (futuro), persiste Agendamento -- AC principal
- [x] `infrastructure/web/AgendamentoController.java` + DTOs -- `POST /v1/agendamentos`, `201` no sucesso -- segue molde `AlocacaoController` (validação 100% em domínio/aplicação, sem Bean Validation `@Valid`, mesmo padrão já usado para CPF)
- [x] `infrastructure/web/AgendamentoExceptionHandler.java` -- `ProblemDetail` com `422` para CPF/recursoId/data inválidos -- segue molde `RecursosExceptionHandler`, introduz `422` novo no projeto
- [x] Testes unitários de `RegistrarAgendamento` cobrindo a I/O Matrix (paciente novo, paciente existente, CPF inválido, recursoId malformado, data no passado) -- cobertura ≥90% domínio (epic-1-context.md)
- [x] Teste de integração do endpoint (`AgendamentoControllerIntegrationTest`) cobrindo `201` e os três `422` -- valida contrato HTTP
- [x] `infra-cdk/.../ConfirmaSusStack.java` -- `buildAgendamentoConfirmacaoService` (molde `buildAuthService`), 2 SGs (app só de `sgGatewayApp`, health público), sem SG/porta gRPC -- AD-11 (gRPC adiado)
- [x] `infra-cdk/.../ConfirmaSusStack.java` -- remover `TriagemScoreServiceTaskRole` (sem substituto nesta story) -- evita role fantasma sem uso
- [x] `agendamento-confirmacao-service/Dockerfile` -- criar a partir do molde `auth-service/Dockerfile` -- build da imagem
- [x] `gateway-service/src/main/resources/application.yml` -- adicionar rota para `agendamento-confirmacao-service` -- expõe o endpoint via gateway

**Acceptance Criteria:**
- Given o build do monorepo após a renomeação, when `mvn -pl agendamento-confirmacao-service -am package` roda, then o build é verde e nenhuma classe/endpoint com `Triagem`/`Score`/`GravidadePercebida`/`SinaisVitais`/`CalculadorDeScore` no nome permanece no módulo.
- Given um CPF válido reaproveitado de um Paciente já existente, when dois Agendamentos são registrados para esse mesmo CPF em `recursoId`s diferentes, then ambos persistem com o mesmo `pacienteId` (Paciente não duplicado).
- Given o construto CDK ainda inexistente, when o deploy roda, then `agendamento-confirmacao-service` sobe em Fargate com schema `agendamento_confirmacao`, SG de app só a partir de `sgGatewayApp`, rota no gateway, health-check público, segredos via Secrets Manager — sem nenhum SG/porta gRPC associado nesta story.

## Design Notes

`RegistrarAgendamento` não precisa gravar em `eventos_outbox` nesta story (nenhum evento de domínio definido para o registro em si — ver AD-3). O agregado `Agendamento` deve, porém, já expor um campo `status` como enum preparado para as transições futuras (`AGUARDANDO_CONFIRMACAO`, `CONFIRMADO`, `LIBERADO`) mesmo que só `AGUARDANDO_JANELA` seja atingível nesta story, para que a Story 1.2 não precise alterar o tipo da coluna.

**Nota pós-implementação:** as Boundaries acima mencionam `pacienteId` como "UUID interno", mas `Paciente`/`Paciente.id` já existiam como `Long` (IDENTITY) no serviço herdado e a mesma seção manda reaproveitar essas classes "como estão, sem redesenhar". Prevaleceu não redesenhar — `pacienteId` circula como `Long`, não `UUID`. Se isso for inaceitável, é uma decisão de arquitetura nova (migrar `Paciente` para UUID), não um bug desta story.

## Verification

**Commands:**
- `mvn -pl agendamento-confirmacao-service -am -DskipTests package` -- expected: build verde, sem referência residual a `triagem`/`Triagem`/`Score`
- `mvn -pl agendamento-confirmacao-service -am test` -- expected: todos os testes passam, cobertura de domínio ≥90% (JaCoCo)
- `grep -ri "triagem\|score\|gravidade\|sinaisvitais" agendamento-confirmacao-service/src` -- expected: nenhuma ocorrência fora de eventual changelog/comentário histórico
- `mvn -pl infra-cdk -am test` (rodado da raiz do repo, não de dentro de `infra-cdk/`) -- expected: `ConfirmaSusStackTest` passa, incluindo a contagem de 4 `AWS::ECS::Service` e a ausência de `TriagemScoreServiceTaskRole`

**Manual checks (if no CLI):**
- Revisar `gateway-service/src/main/resources/application.yml` para confirmar que a nova rota aponta para o serviço correto e não colide com rotas existentes.

## Suggested Review Order

**Domínio e comando de registro**

- Agregado novo com estado inicial único e enum já preparado para as próximas transições (AD-4).
  [`Agendamento.java:19`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/domain/Agendamento.java#L19)

- Orquestração: CPF → recursoId → dataHoraAgendamento, nessa ordem determinística, antes de tocar o repositório.
  [`RegistrarAgendamento.java:49`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/application/command/RegistrarAgendamento.java#L49)

- `recursoId` validado só por formato UUID — decisão explícita de não sincronizar com o catálogo real (AD-1).
  [`RegistrarAgendamento.java:63`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/application/command/RegistrarAgendamento.java#L63)

**Contrato HTTP e erros (primeiro uso de 422 no projeto)**

- Endpoint de registro, sem Bean Validation — toda a validação vive no domínio/aplicação.
  [`AgendamentoController.java:27`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoController.java#L27)

- Os três `422` (CPF, recursoId, data) mapeados 1:1 para exceções de domínio/aplicação.
  [`AgendamentoExceptionHandler.java:42`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoExceptionHandler.java#L42)

- Fallback de erro inesperado agora loga a exceção original antes do `500` (patch do code review).
  [`AgendamentoExceptionHandler.java:75`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoExceptionHandler.java#L75)

**Preparação do serviço (AD-1: rename/poda)**

- Migration nova do zero, só `pacientes` + `agendamentos`, sem migração de dado do schema antigo; índice em `recurso_id` adicionado no review.
  [`V1__create_agendamento_confirmacao_schema.sql:12`](../../agendamento-confirmacao-service/src/main/resources/db/migration/V1__create_agendamento_confirmacao_schema.sql#L12)

- Entidade JPA reaproveitada do serviço antigo, só o schema muda.
  [`AgendamentoJpaEntity.java:23`](../../agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/persistence/AgendamentoJpaEntity.java#L23)

**Infraestrutura (CDK + gateway)**

- Novo construto Fargate, molde de `buildAuthService`; sem SG/porta gRPC nesta story (adiado para Story 2.1).
  [`ConfirmaSusStack.java:719`](../../infra-cdk/src/main/java/com/confirmasus/infra/ConfirmaSusStack.java#L719)

- `TriagemScoreServiceTaskRole` removida sem substituto — sem outbox/SNS nesta story.
  [`ConfirmaSusStack.java:270`](../../infra-cdk/src/main/java/com/confirmasus/infra/ConfirmaSusStack.java#L270)

- Rota nova no gateway expondo `POST /v1/agendamentos` para o serviço renomeado.
  [`application.yml:35`](../../gateway-service/src/main/resources/application.yml#L35)

**Testes de infraestrutura ajustados no review (4º ECS Service, role removida)**

- Contagem de `AWS::ECS::Service` atualizada de 3 para 4 e teste da role antiga substituído por asserção de ausência.
  [`ConfirmaSusStackTest.java:44`](../../infra-cdk/src/test/java/com/confirmasus/infra/ConfirmaSusStackTest.java#L44)
