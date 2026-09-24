---
title: 'Story 5.3 — Carga Idempotente de Lista de Espera Sintética'
type: 'feature'
created: '2026-09-23'
status: 'in-progress'
review_loop_iteration: 0
baseline_commit: 'd598acd'
context:
  - _bmad-output/implementation-artifacts/epic-5-context.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O seed-adapter (Stories 5.1/5.2) carrega Recursos e Agendamentos, mas não carrega Lista de Espera. Sem entrada de Lista de Espera sintética com Pacientes aguardando, os cenários de demonstração de Sugestão de Repasse (UJ-2/UJ-3) não são validáveis — falta preenchimento completo do dataset.

**Approach:** Estender seed-adapter para carregar entradas de Lista de Espera via `POST /v1/lista-espera` (Story 2.1 endpoint), resolvendo CPF internamente para pacienteId via gRPC; manter idempotência via pacienteId+recursoId; validar que cada entrada referencia um Recurso já carregado (Story 5.1) sem duplicatas.

## Boundaries & Constraints

**Always:**
- Idempotência total: cada entrada de Lista de Espera é identificada univocamente por pacienteId + recursoId — reexecução não duplica
- Orquestração via gateway (`POST /v1/lista-espera`) usando JWT autenticado (Story 5.1 AuthClient)
- Falha explícita sob indisponibilidade: se `liberacao-repasse-service` estiver indisponível, aborta com erro claro
- Ordem de dependência rigorosa: Recurso (Story 5.1) sempre antes de Lista de Espera; Agendamentos (Story 5.2) antes de Lista de Espera
- CPF em texto claro circula apenas dentro do seed-adapter; semente nunca persiste CPF
- Resolução de CPF via gRPC chamada interna (Story 1.1 ResolverOuCriarPaciente): timeout curto, falha explícita se CPF inválido (`422`)
- Formato seed-data.json: bloco `listasEspera: [{cpf, recursoId, dataSolicitacao, ...}]` coexistindo com blocos `recursos` e `agendamentos`

**Ask First:**
- Qualquer decisão de criar estados que exijam endpoints não-publicados (ex: endpoints administrativos de rejeição de entrada) — usar apenas endpoints já implementados (POST /v1/lista-espera)

**Never:**
- Não criar/modificar endpoints novos no liberacao-repasse-service nesta story — usar apenas Story 2.1 endpoint
- Não implementar validação de CPF/recursoId em seed-adapter (responsabilidade do liberacao-repasse-service, Story 2.1)
- Não persistir CPF em banco de dados do seed-adapter — ler apenas de seed-data.json, usar em runtime, descartar
- Não criar lógica de retry/circuit-breaker nesta story (falha explícita é o padrão)

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Entrada de Lista de Espera nova, CPF válido | CPF válido inédito + recursoId UUID (de Recurso carregado em 5.1) + dataSolicitacao com timestamp válido | POST /v1/lista-espera retorna `201`, entrada criada com pacienteId resolvido internamente | N/A |
| Reexecução idempotente, mesmo CPF/recursoId | Já existe no banco | Seed-adapter detecta duplicata, pula entrada (não refaz POST) — lista de sucesso lista idempotente | N/A (sucesso silencioso) |
| CPF inválido | Formato/checksum inválido | gRPC ResolverOuCriarPaciente retorna erro, seed-adapter reporta entrada rejeitada, continua com próximas | `422` (entrada pulada, pipeline continua) |
| recursoId malformado/ausente | recursoId nulo, vazio ou não-UUID | POST /v1/lista-espera retorna `422` | `422` (entrada pulada) |
| Recurso não existe | recursoId válido mas não carregado em Story 5.1 | POST /v1/lista-espera retorna `404`, entrada rejeitada, continua | `404` (entrada pulada) |
| dataSolicitacao inválida | Formato inválido ou timestamp > now() | POST /v1/lista-espera retorna `422` | `422` (entrada pulada) |
| Liberacao-repasse-service indisponível | Serviço offline, conexão recusada | Falha com erro claro, aborta pipeline | `IllegalStateException` com "Gateway indisponível" (fail-fast) |

</frozen-after-approval>

## Code Map

- `seed-adapter/src/main/java/com/confirmasus/seedadapter/ListaEsperaClient.java` (novo) -- cliente HTTP para POST /v1/lista-espera, similar a RecursoClient e AgendamentoClient, autentica com JWT
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataLoader.java:50-60` -- estender `carregar()` para chamar `carregarListasEspera()` após `carregarAgendamentos()` (ordem rigorosa)
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataLoader.java` -- adicionar método privado `carregarListasEspera(List<ListaEsperaSeed>)` análogo a `carregarAgendamentos()`
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/ListaEsperaSeed.java` (novo) -- POJO para desserialização: `cpf`, `recursoId` (UUID), `dataSolicitacao` (Instant)
- `seed-adapter/src/main/java/com/confirmasus/seedadapter/SeedDataContainer.java` -- estender frontmatter para incluir `List<ListaEsperaSeed> listasEspera`
- `seed-adapter/src/main/resources/seed-data.json` -- adicionar bloco `"listasEspera": [{...}]` com 3-5 entradas de teste (CPF valores sintéticos válidos, recursoIds de Story 5.1, dataSolicitacao variadas, referenciando Agendamentos liberados)
- `seed-adapter/src/test/java/com/confirmasus/seedadapter/ListaEsperaClientTest.java` (novo) -- testes análogos a RecursoClientTest e AgendamentoClientTest: mock gateway, validar chamadas HTTP, erro handling
- `seed-adapter/src/test/java/com/confirmasus/seedadapter/SeedDataLoaderTest.java` -- estender testes existentes para cobrir fase 3 (lista de espera)

## Tasks & Acceptance

**Execution:**
- [ ] `ListaEsperaClient.java` -- criar cliente HTTP com `criarOuObter(cpf, recursoId, dataSolicitacao)` (retorna entrada criada idempotente) -- autentica com JWT do AuthClient já existente
- [ ] `ListaEsperaSeed.java` -- POJO com cpf/recursoId/dataSolicitacao para desserialização JSON
- [ ] `SeedDataLoader.carregarListasEspera()` -- orquestra chamadas ListaEsperaClient; fail-fast na primeira falha (não retrocede se falhar)
- [ ] `SeedDataContainer.listasEspera` -- estender classe interna para incluir `List<ListaEsperaSeed> listasEspera`
- [ ] `seed-data.json` -- adicionar bloco `listasEspera: [{cpf: "22222222222", recursoId: "<uuid-01>", dataSolicitacao: "2026-09-10T10:00:00Z"}, ...]` com 3-5 entradas de teste (referenciando Recursos carregados, CPFs distintos de Agendamentos)
- [ ] `ListaEsperaClientTest.java` -- testar criação idempotente, erro handling (CPF inválido, recursoId malformado, gateway indisponível, recurso não existe)
- [ ] `SeedDataLoaderTest.java` -- estender para cobrir carregarListasEspera(), validar ordem de execução (Recursos → Agendamentos → Lista de Espera)

**Acceptance Criteria:**
- Given seed-data.json com bloco recursos (Story 5.1) + bloco agendamentos (Story 5.2) + bloco listasEspera com 3-5 entradas de teste, when seed-adapter.carregar() é executado, then: (1) todos os Recursos são carregados, (2) todos os Agendamentos são carregados, (3) todas as entradas de Lista de Espera são criadas via POST /v1/lista-espera com CPF resolvido internamente, (4) reexecução não duplica entradas (idempotência por pacienteId+recursoId), (5) falha em Lista de Espera não retrocede para Agendamentos
- Given ListaEsperaClient instanciado com gateway válido + JWT autenticado, when `criarOuObter(cpf, recursoId, dataSolicitacao)` é chamado, then POST /v1/lista-espera retorna entrada criada sincrono
- Given CPF inválido/recursoId malformado/dataSolicitacao inválida, when seed-adapter processa a entrada, then gateway retorna `422` ou `404`, entrada é pulada, pipeline continua (não aborta)
- Given liberacao-repasse-service offline, when seed-adapter tenta criar entrada de Lista de Espera, then falha com `IllegalStateException` contendo "Gateway indisponível", pipeline aborta

## Spec Change Log

<!-- Empty until first review loop -->

## Design Notes

**Idempotência e Detecção de Duplicata:**
Lista de Espera não tem um endpoint de "upsert" — POST /v1/lista-espera sempre retorna `201` (novo) ou erro. Para detectar duplicatas, o seed-adapter mantém cache local de (pacienteId, recursoId) após resolução de CPF, ou consulta endpoint de leitura se disponível (Story 4.2 auditoria poderia servir como source de verdade). Implementação resolvida em investigation step.

**Ordenação por Chegada:**
Spec SPEC-confirmasus CAP-8 exige que Lista de Espera seja ordenada exclusivamente por ordem de chegada (dataSolicitacao). O seed-adapter garante que timestamps de `dataSolicitacao` das entradas variam e refletem ordem de chegada esperada — o serviço liberacao-repasse-service (Story 2.1) é responsável por manter essa ordem.

**CPF Válidos Sintéticos:**
Usar CPFs sintéticos válidos de checksum (ex: 22222222222, 33333333333, etc.) — validação real fica a cargo de Story 1.1 ResolverOuCriarPaciente.

## Verification

**Commands:**
- `cd seed-adapter && mvn clean test` -- expected: ListaEsperaClientTest + SeedDataLoaderTest fase 3 todos passando
- `cd seed-adapter && mvn clean package` -- expected: JAR construído com seed-data.json incluído em resources
- `docker run -e GATEWAY_URL=http://localhost:8080 <seed-adapter-image>` (manual, em ambiente de dev com gateway rodando) -- expected: seed-data carregada ponta-a-ponta, logs mostram "Lista de Espera carregada com sucesso"

**Manual checks (if no CLI):**
- Abrir seed-data.json e validar bloco `listasEspera` com min. 3 entradas com CPFs/recursos distintos
- Executar seed-adapter contra gateway local + banco de testes; validar que 2ª execução não duplica entradas (idempotência)
- Simular gateway offline; validar que seed-adapter falha com erro claro contendo "Gateway indisponível"
