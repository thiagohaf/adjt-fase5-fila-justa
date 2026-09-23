---
title: 'Story 5.2 — Carga Idempotente de Agendamentos Sintéticos'
type: 'feature'
created: '2026-09-22'
status: 'in-progress'
review_loop_iteration: 0
baseline_commit: 'ab280510131504775423c180cd48565dd5d65259'
context:
  - _bmad-output/implementation-artifacts/epic-5-context.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O seed-adapter (Story 5.1) carrega Recursos, mas não carrega Agendamentos. Sem Agendamentos sintéticos com estados simulados, as jornadas de usuário (UJ-1: confirma presença; UJ-2: não responde; UJ-3: gestor decide repasse) não são demonstráveis — falta dataset de ponta-a-ponta.

**Approach:** Estender seed-adapter para carregar Agendamentos sintéticos via `POST /v1/agendamentos` (Story 1.1 endpoint), resolvendo CPF internamente pelo serviço; encadear chamadas de API (abrirJanela, confirmarPresenca, recusarPresenca) para simular estados pós-ação (AGUARDANDO_CONFIRMACAO, CONFIRMADO, LIBERADO); manter idempotência via CPF+recursoId+dataHora.

## Boundaries & Constraints

**Always:**
- Idempotência total: cada Agendamento é identificado univocamente por CPF + recursoId + dataHora — reexecução não duplica
- Orquestração via gateway (`POST /v1/agendamentos` + chamadas de transição) usando JWT autenticado (Story 5.1 AuthClient)
- Falha explícita sob indisponibilidade: se `agendamento-confirmacao-service` estiver indisponível, aborta com erro claro, sem prosseguir para Lista de Espera (Story 5.3)
- Ordem de dependência rigorosa: Recurso (Story 5.1) sempre antes de Agendamentos; Agendamentos antes de Lista de Espera (Story 5.3)
- Estados simulados alcançados via API: AGUARDANDO_JANELA (estado inicial do registro), AGUARDANDO_CONFIRMACAO (após abrirJanela), CONFIRMADO (após confirmarPresenca), LIBERADO (após recusarPresenca)
- CPF em texto claro circula apenas dentro do seed-adapter e do agendamento-confirmacao-service (Story 1.1 boundary); seed-adapter nunca armazena/persiste CPF
- Formato seed-data.json: bloco `agendamentos: [{cpf, recursoId, dataHora, estado, ...}]` coexistindo com bloco `recursos` já existente

**Ask First:**
- Qualquer decisão de criar estados que exijam endpoints não-publicados (ex: endpoints administrativos de transição de estado, gRPC interno) — usar apenas endpoints já implementados (POST /v1/agendamentos + POST /v1/agendamentos/{id}/confirmacao + POST /v1/agendamentos/{id}/recusa)

**Never:**
- Não criar/modificar endpoints novos no agendamento-confirmacao-service nesta story — usar apenas Story 1.1/1.3/1.4 endpoints
- Não implementar validação de CPF/recursoId em seed-adapter (responsabilidade do agendamento-confirmacao-service, Story 1.1)
- Não persistir CPF em banco de dados do seed-adapter — ler apenas de seed-data.json, usar em runtime, descartar
- Não criar lógica de retry/circuit-breaker nesta story (falha explícita é o padrão)

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Agendamento novo, estado AGUARDANDO_JANELA | CPF válido inédito + recursoId UUID (de Recurso carregado em 5.1) + dataHora futura + estado=AGUARDANDO_JANELA | POST /v1/agendamentos retorna `201`, agendamentoId, status AGUARDANDO_JANELA | N/A |
| Agendamento novo, estado AGUARDANDO_CONFIRMACAO | Mesmo CPF/recursoId/dataHora + estado=AGUARDANDO_CONFIRMACAO | POST /v1/agendamentos → `201` AGUARDANDO_JANELA, POST /v1/agendamentos/{id}/confirmacao → `200` (nota: spec 1.2 abre janela, spec 1.3 confirma) | N/A |
| Agendamento novo, estado CONFIRMADO | Mesmo CPF/recursoId/dataHora + estado=CONFIRMADO | POST /v1/agendamentos + abrirJanela (spec 1.2) + confirmarPresenca (spec 1.3) → `201` + transições de estado | N/A |
| Agendamento novo, estado LIBERADO | Mesmo CPF/recursoId/dataHora + estado=LIBERADO | POST /v1/agendamentos + abrirJanela + recusarPresenca (spec 1.4) → `201` + transições de estado | N/A |
| Reexecução idempotente, mesmo CPF/recursoId/dataHora | Já existe no banco | Seed-adapter detecta duplicata, pula entrada (não refaz POST) — lista de sucesso lista idempotente | N/A (sucesso silencioso) |
| CPF inválido | Formato/checksum inválido | POST /v1/agendamentos retorna `422`, entrada rejeitada, prossegue com próximas | `422` (entrada pulada, pipeline continua) |
| recursoId malformado/ausente | recursoId nulo, vazio ou não-UUID | POST /v1/agendamentos retorna `422` | `422` (entrada pulada) |
| dataHora no passado | Instante <= now() | POST /v1/agendamentos retorna `422` | `422` (entrada pulada) |
| Agendamento-confirmacao-service indisponível | Serviço offline, conexão recusada | Falha com erro claro, aborta pipeline (não prossegue para Story 5.3) | `IllegalStateException` com "Gateway indisponível" (fail-fast) |

</frozen-after-approval>

## Code Map

- `seed-adapter/src/main/java/com/confirmasus/seedadapter/AgendamentoClient.java` (novo) -- cliente HTTP para POST /v1/agendamentos + transições (confirma/recusa), similar a RecursoClient, autentica com JWT
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataLoader.java:42-50` -- estender `carregar()` para chamar `carregarAgendamentos()` após `upsertar_recursos()` (ordem rigorosa)
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataLoader.java` -- adicionar método privado `carregarAgendamentos(List<AgendamentoSeed>)` análogo a `upsertar_recursos()`
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/AgendamentoSeed.java` (novo) -- POJO para desserialização: `cpf`, `recursoId` (UUID), `dataHoraAgendamento`, `estado` (enum StatusAgendamento)
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataContainer.java` -- estender frontmatter para incluir `List<AgendamentoSeed> agendamentos` 
- `seed-adapter/src/main/resources/seed-data.json` -- adicionar bloco `"agendamentos": [{...}]` com 5-10 entradas de teste (CPF valores sintéticos válidos, recursoIds de Story 5.1, dataHoras futuras, estados variados)
- `seed-adapter/src/test/java/com/confirmasus/seedadapter/AgendamentoClientTest.java` (novo) -- testes análogos a RecursoClientTest: mock gateway, validar chamadas HTTP, erro handling
- `seed-adapter/src/test/java/com/confirmasus/seedadapter/SeedDataLoaderTest.java` -- estender testes existentes para cobrir fase 2 (agendamentos)
- `agendamento-confirmacao-service/src/main/java/com/confirmasus/agendamento/infrastructure/web/AgendamentoController.java:52-62` -- endpoints `/v1/agendamentos/{id}/confirmacao` e `/v1/agendamentos/{id}/recusa` já implementados (Story 1.3/1.4)

## Tasks & Acceptance

**Execution:**
- [ ] `AgendamentoClient.java` -- criar cliente HTTP com `criarOuObter(cpf, recursoId, dataHora)` (retorna agendamentoId idempotente) e `transicionarParaEstado(agendamentoId, estado)` (abre janela/confirma/recusa) -- autentica com JWT do AuthClient já existente
- [ ] `AgendamentoSeed.java` -- POJO com cpf/recursoId/dataHoraAgendamento/estado (enum) para desserialização JSON
- [ ] `SeedDataLoader.carregarAgendamentos()` -- orquestra chamadas AgendamentoClient; fail-fast na primeira falha (não continua para Story 5.3 se Agendamentos falharem)
- [ ] `SeedDataContainer.agendamentos` -- estender classe interna de SeedDataLoader para incluir `List<AgendamentoSeed> agendamentos`
- [ ] `seed-data.json` -- adicionar bloco `agendamentos: [{cpf: "11111111111", recursoId: "<uuid-01>", dataHoraAgendamento: "2026-10-01T14:00:00Z", estado: "AGUARDANDO_JANELA"}, ...]` com casos de teste (estados variados: AGUARDANDO_JANELA, AGUARDANDO_CONFIRMACAO, CONFIRMADO, LIBERADO)
- [ ] `AgendamentoClientTest.java` -- testar criação idempotente, transições de estado, erro handling (CPF inválido, recursoId malformado, gateway indisponível)
- [ ] `SeedDataLoaderTest.java` -- estender para cobrir carregarAgendamentos(), validar ordem de execução (Recursos antes de Agendamentos)

**Acceptance Criteria:**
- Given seed-data.json com bloco recursos (Story 5.1) + bloco agendamentos com 5-10 entradas de teste com estados simulados, when seed-adapter.carregar() é executado, then: (1) todos os Recursos são upsertados via POST /v1/recursos (idempotente), (2) todos os Agendamentos são criados via POST /v1/agendamentos com CPF resolvido internamente, (3) estados simulados são alcançados via transições encadeadas (abrirJanela/confirmarPresenca/recusarPresenca), (4) reexecução não duplica entradas (idempotência por CPF+recursoId+dataHora), (5) falha em Agendamento aborta pipeline sem prosseguir para Lista de Espera (Story 5.3)
- Given AgendamentoClient instanciado com gateway válido + JWT autenticado, when `criarOuObter(cpf, recursoId, dataHora)` é chamado, then POST /v1/agendamentos retorna agendamentoId sincrono
- Given agendamentoId válido, when `transicionarParaEstado(agendamentoId, CONFIRMADO)` é chamado, then chamadas encadeadas (abrirJanela via spec 1.2 + confirmarPresenca via spec 1.3) são orquestradas atomicamente; falha em qualquer uma aborta com erro claro
- Given CPF inválido/recursoId malformado/dataHora no passado, when seed-adapter processa a entrada, then gateway retorna `422`, entrada é pulada, pipeline continua (não aborta)
- Given agendamento-confirmacao-service offline, when seed-adapter tenta criar Agendamento, then falha com `IllegalStateException` contendo "Gateway indisponível", pipeline aborta sem prosseguir para Lista de Espera

## Spec Change Log

<!-- Empty until first review loop -->

## Design Notes

**Idempotência e Detecção de Duplicata:**
Agendamentos não têm um endpoint de "upsert" — POST /v1/agendamentos sempre retorna `201` (novo) ou erro. Para detectar duplicatas, o seed-adapter consulta o banco via um endpoint de leitura (Story 4.2: `GET /v1/auditoria` ou equivalente) ou mantém um cache local de (CPF, recursoId, dataHora) após primeira carga. A decisão de implementação (endpoint de leitura vs. cache local) é resolvida no step-03 investigation — o spec permite ambas.

**Transições de Estado Encadeadas:**
Estado AGUARDANDO_JANELA é sempre o estado inicial de POST /v1/agendamentos (Story 1.1). Transições subsequentes requerem endpoints já implementados (Story 1.2: abrirJanela, Story 1.3: confirmarPresenca, Story 1.4: recusarPresenca). O seed-adapter orquestra essas chamadas de forma síncrona e sequencial — se qualquer uma falhar, aborta e reporta o erro.

**CPF Válidos Sintéticos:**
Para testes e demonstração, usar CPFs sintéticos válidos de checksum (ex: 11111111111, formato 99999999999 com dígitos verificadores corretos). Validação real (formato + checksum) fica a cargo do agendamento-confirmacao-service (Story 1.1).

## Verification

**Commands:**
- `cd seed-adapter && mvn clean test` -- expected: AgendamentoClientTest + SeedDataLoaderTest fase 2 todos passando
- `cd seed-adapter && mvn clean package` -- expected: JAR construído com seed-data.json incluído em resources
- `docker run -e GATEWAY_URL=http://localhost:8080 <seed-adapter-image>` (manual, em ambiente de dev com gateway rodando) -- expected: seed-data carregada, logs mostram "Agendamentos carregados com sucesso"

**Manual checks (if no CLI):**
- Abrir seed-data.json e validar bloco `agendamentos` com min. 5 entradas variando estados (AGUARDANDO_JANELA, AGUARDANDO_CONFIRMACAO, CONFIRMADO, LIBERADO)
- Executar seed-adapter contra gateway local + banco de testes; validar que 2ª execução não duplica Agendamentos (idempotência)
- Simular gateway offline; validar que seed-adapter falha com erro claro contendo "Gateway indisponível"
