---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-Fase5-2026-09-16/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md
  - _bmad-output/specs/spec-confirmasus/SPEC.md
---

# ConfirmaSUS - Epic Breakdown

## Overview

Este documento decompõe o PRD (`prd-Fase5-2026-09-16`), a Arquitetura final (`ARCHITECTURE-SPINE.md`, 13 ADs) e o SPEC distilado (`spec-confirmasus/SPEC.md`, CAP-1..14) do ConfirmaSUS em epics e stories implementáveis. Não há documento de UX — o MVP é backend-only (Swagger/Postman), conforme Non-Goals do PRD §5. Este documento **substitui** `epics-filajusta-2026-09-05.md` (arquivado), do produto anterior descontinuado por restrição legal.

## Requirements Inventory

### Functional Requirements

FR-1: O sistema carrega, via seed, um conjunto de Agendamentos sintéticos (Paciente por CPF, Recurso/especialidade, unidade, data/hora), simulando uma Camada Adaptadora sobre sistemas oficiais do SUS.
FR-2: O CPF recebido na carga do Agendamento é resolvido para um ID de Paciente interno (gerado ou reaproveitado pelo sistema); CPF com formato/checksum inválido rejeita o registro de seed correspondente.
FR-3: Ao entrar na Janela de Confirmação de um Agendamento, o sistema publica uma notificação (mock) pedindo a confirmação de presença do Paciente, exatamente uma vez por Agendamento por abertura de janela.
FR-4: Um Paciente confirma presença em um Agendamento dentro da Janela de Confirmação, via API; confirmação é idempotente e impede liberação da vaga mesmo que o prazo expire depois.
FR-5: Um Paciente recusa presença em um Agendamento dentro da Janela de Confirmação, via API; a Recusa dispara a liberação da vaga imediatamente.
FR-6: Se a Janela de Confirmação expira sem Confirmação nem Recusa, o sistema marca o Agendamento como Não Confirmado automaticamente, sem intervenção manual.
FR-7: Um Agendamento com Recusa ou Não Confirmado tem sua Vaga marcada como Liberada, disparando a consulta à Lista de Espera e a geração da Sugestão de Repasse.
FR-8: Qualquer usuário autenticado pode consultar a Lista de Espera de um tipo de Recurso, ordenada exclusivamente por ordem de chegada da solicitação — nunca por gravidade ou critério clínico.
FR-9: Ao liberar uma vaga, o sistema gera automaticamente uma Sugestão de Repasse apontando o próximo Paciente da Lista de Espera daquele Recurso, e notifica (mock) o Gestor de Agenda responsável.
FR-10: Um Gestor de Agenda confirma uma Sugestão de Repasse, criando um Repasse Confirmado, definitivo; tentar confirmar uma sugestão já resolvida retorna erro de conflito.
FR-11: Um Gestor de Agenda recusa uma Sugestão de Repasse; o sistema gera uma nova Sugestão para o próximo Paciente da Lista de Espera, pulando os recusados.
FR-12: Toda notificação, Confirmação, Recusa, transição para Não Confirmado, Liberação, Sugestão de Repasse e decisão de repasse é registrada no Log Auditável (append-only) com timestamp e motivo.
FR-13: Um Auditor consulta o histórico completo do Log Auditável de um Paciente ou de um Agendamento específico, ordenado cronologicamente.
FR-14: Um usuário sintético pré-cadastrado autentica-se via `POST /v1/auth/login` contra o `auth-service` já implementado, recebendo um JWT validado pelo `gateway-service`; sem RBAC aplicado nesta fase.

### NonFunctional Requirements

NFR-1 (Continuidade sob falha parcial): o fluxo de Confirmação/Recusa continua aceitando respostas do Paciente mesmo se o `auditoria-service` estiver temporariamente indisponível; a propagação para auditoria é processada quando ele voltar, nunca perdida.
NFR-2 (Observabilidade mínima): logs estruturados e health-check por serviço, com `X-Correlation-Id` propagado ponta a ponta (herdado do `gateway-service`).
NFR-3 (Reprodutibilidade): todo o sistema (5 serviços + `seed-adapter` + banco + mensageria) sobe com um único comando (`cdk deploy`), sem passos manuais.
NFR-4 (Contratos versionados): comunicação entre serviços usa contratos versionados — schema de evento versionado (aditivo dentro de major version, `version` incompatível vai para DLQ) e versionamento de path nos endpoints REST (`/v1/`) e de pacote proto (`vN`).
NFR-5 (Testes automatizados): cobertura de linha ≥90% (JaCoCo) na camada de domínio de cada microsserviço + teste de mutação (PIT) na mesma camada + testes de integração cobrindo os contratos entre serviços + testes de aceitação BDD (Cucumber-JVM) cobrindo o ciclo único ponta a ponta.
NFR-6 (Segredos fora do código): credenciais e segredos de configuração não ficam versionados no repositório (env/AWS Secrets Manager).

**Definition of Done por story (NFR-5, aplica-se a todas as stories abaixo, não repetido AC a AC):** cobertura ≥90% (JaCoCo) + PIT na camada de domínio do serviço tocado + teste de integração do contrato exercitado + cenário Cucumber/BDD quando a story participa do ciclo ponta a ponta demonstrado em vídeo.

### Additional Requirements

| AD | Decisão | Onde se aplica |
| --- | --- | --- |
| AD-1 | Não é greenfield para 2 dos 3 serviços de domínio: `triagem-score-service`→`agendamento-confirmacao-service` e `matching-alocacao-service`→`liberacao-repasse-service` são renomeados e podados (não recriados); `auditoria-service` é novo; `seed-adapter` entra só pelo gateway e faz upsert idempotente nesta ordem: catálogo de Recurso → Agendamentos → Lista de Espera | Preparação das Stories 1.1 e 2.1; ordem de execução do Epic 4 |
| AD-2 | Clean Architecture (`domain/` sem framework → `application/command\|query/` → `infrastructure/`) e CQRS lógico por serviço, mesmo banco sem read-model separado | Todos os serviços de domínio |
| AD-3 | Outbox (mesma transação do comando) + relay poller + SNS FIFO + SQS FIFO dedicada por consumidor + DLQ (`maxReceiveCount=5`, investigada manualmente — sem story dedicada, fora do escopo do MVP); `MessageGroupId` por agregado (`agendamentoId`/`recursoId`), `MessageDeduplicationId=eventId` | Todo evento publicado pelos Epics 1–3 |
| AD-4/AD-5 | Abertura/expiração da Janela de Confirmação via dois pollers `@Scheduled` com escrita condicional (`UPDATE ... WHERE status = <esperado>`) — não fila de delay; cadência exata do poller é `[Deferred]` para `bmad-build` | Stories 1.2, 1.5 |
| AD-7 | `auditoria-service`: só leitura + consumidor de eventos via SQS FIFO, nunca chamado de forma síncrona | Epic 3 |
| AD-8 | gRPC interno `ResolverOuCriarPaciente(cpf) -> pacienteId`, exclusivo `liberacao-repasse-service`→`agendamento-confirmacao-service`, chamado pelo `seed-adapter` em tempo de deploy (timeout curto, sem retry); CPF nunca persistido fora de `agendamento-confirmacao-service` | Stories 1.1, 2.1 |
| AD-9/AD-13 | Emissão de JWT (HS256) via `auth-service`, validação só no `gateway-service`; claim `role` puramente informativo, sem RBAC aplicado | FR-14, cross-cutting (explícito nas Stories 2.2 e 3.2) |
| AD-10 | Isolamento por schema num único cluster PostgreSQL 18 (`agendamento_confirmacao`, `liberacao_repasse`, `auditoria`, `auth`), `REVOKE` cross-schema, enforcement via ArchUnit obrigatório no CI | CDK/schema de cada serviço (Stories 1.1, 2.1, 3.1) |
| AD-11 | VPC subnet pública única (2 AZs, sem NAT Gateway), Fargate `assignPublicIp=ENABLED`, security group por serviço — só o SG do `gateway-service` alcança as portas HTTP de app; gRPC liberado só entre os SGs de `liberacao-repasse-service` e `agendamento-confirmacao-service` | CDK de cada serviço (Stories 1.1, 2.1, 3.1) |
| AD-12 | Runtime misto deliberado: Spring Boot/Spring Cloud nos 5 serviços de aplicação; Quarkus só no `seed-adapter` (cold-start otimizado) | Todo o sistema |
| — | Stack fixada: Java 25, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Spring gRPC 1.1.1, PostgreSQL 18, JaCoCo ≥0.8.14, PIT ≥1.30.0, Cucumber-JVM, Testcontainers — ver Architecture Spine para versões completas | Todo o sistema |
| — | Convenção de erro (cross-cutting, sustenta NFR-4): `409` + `{error, motivo}` para conflito de estado; `422` para entrada inválida (CPF, IDs inexistentes); `404` só para recurso/rota inexistente — nunca para "sem histórico"/"sem candidatos", que são respostas de sucesso vazias | Toda API REST dos 4 serviços |

### UX Design Requirements

Não aplicável — nenhum documento de UX encontrado; entrega é backend-only (Non-Goal explícito do PRD §5, demonstrável via Swagger/Postman).

### FR Coverage Map

FR-1: Epic 4 — orquestração completa da carga sintética (catálogo de Recurso → Agendamentos → Lista de Espera); endpoints de escrita subjacentes entregues como base pelos Epics 1 e 2.
FR-2: Epic 1 — resolução de CPF para ID de Paciente interno.
FR-3: Epic 1 — notificação (mock) ao abrir a Janela de Confirmação.
FR-4: Epic 1 — Confirmação de Presença.
FR-5: Epic 1 — Recusa Ativa.
FR-6: Epic 1 — Expiração por Não-Resposta.
FR-7: Epic 1 — Liberação da Vaga.
FR-8: Epic 2 — Consulta da Lista de Espera.
FR-9: Epic 2 — Sugestão de Repasse automática.
FR-10: Epic 2 — Confirmação do Repasse.
FR-11: Epic 2 — Recusa do Repasse.
FR-12: Epic 3 — Registro em Log Auditável.
FR-13: Epic 3 — Consulta de Auditoria.
FR-14: Cross-cutting — já implementado (`auth-service`/`gateway-service` reaproveitados sem alteração); validado como critério de aceite em todo endpoint de todos os epics, sem epic dedicado.

### NFR Coverage Map

NFR-1: Stories 1.3–1.5, 2.3–2.5 publicam via outbox independentemente do `auditoria-service` estar no ar (Epic 3 nunca é chamado de forma síncrona, AD-7).
NFR-2: ACs de provisionamento CDK (Stories 1.1, 2.1, 3.1) — health-check público e `X-Correlation-Id` propagado.
NFR-3: Epic 4 completo — dataset de demonstração sobe via `cdk deploy` único comando.
NFR-4: Convenção de erro + versionamento de evento/endpoint (tabela de Additional Requirements).
NFR-5: Definition of Done por story (nota acima, Requirements Inventory).
NFR-6: ACs de provisionamento CDK (Secrets Manager, Stories 1.1, 2.1, 3.1).

## Epic List

1. **Epic 1: Confirmação Ativa de Presença** — FR-2–FR-7 (+ base de FR-1) — `agendamento-confirmacao-service`
2. **Epic 2: Liberação e Repasse de Vaga** — FR-8–FR-11 (+ base de FR-1) — `liberacao-repasse-service`
3. **Epic 3: Log Auditável e Consulta de Auditoria** — FR-12, FR-13 — `auditoria-service`
4. **Epic 4: Carga de Dados Sintéticos (Camada Adaptadora)** — FR-1 completa — `seed-adapter`

## Epic 1: Confirmação Ativa de Presença

Paciente é identificado por CPF, notificado ao abrir a Janela de Confirmação, e pode confirmar/recusar presença — ou ter a ausência registrada automaticamente, liberando a vaga. Serviço: `agendamento-confirmacao-service` (renomeado/podado de `triagem-score-service`, AD-1). FRs: FR-2, FR-3, FR-4, FR-5, FR-6, FR-7 (+ base de escrita para FR-1).

### Story 1.1: Registrar Agendamento e Resolver Paciente por CPF

Como sistema de ingestão (seed-adapter),
quero registrar um Agendamento associando-o a um Paciente resolvido por CPF,
para que o Agendamento exista pronto para a Janela de Confirmação, sem o CPF circular além deste ponto.

**Preparação do serviço** (pré-requisito de implementação, fora do escopo de teste BDD): `triagem-score-service` é renomeado para `agendamento-confirmacao-service`, mantendo `Paciente`/`Cpf`/`ResolverOuCriarPaciente` e descartando por completo `Triagem`/`Score`/`GravidadePercebida`/`SinaisVitais`/`CalculadorDeScore` (sem migração de dado, sem endpoint remanescente) — AD-1. Definition of Done desta etapa: build verde, sem classe/endpoint do domínio antigo remanescente.

**Acceptance Criteria:**

**Given** um CPF com formato/checksum válido e dados de Recurso/data-hora referenciando um Recurso existente
**When** um Agendamento é registrado
**Then** um Paciente é criado ou reaproveitado (ID interno) e o Agendamento é persistido com `pacienteId`, `recursoId`, `dataHoraAgendamento`, estado inicial `AGUARDANDO_JANELA`
**And** nenhum componente persiste o CPF fora deste serviço — apenas o `pacienteId` circula em eventos e consultas

**Given** um CPF com formato/checksum inválido
**When** o registro do Agendamento é tentado
**Then** o registro é rejeitado com erro `422`, sem criar Paciente nem Agendamento

**Given** um `recursoId` inexistente ou uma `dataHoraAgendamento` inválida/no passado
**When** o registro do Agendamento é tentado
**Then** o registro é rejeitado com erro `422`, sem persistir o Agendamento

**Given** o construto CDK ainda inexistente para este serviço
**When** o deploy é executado
**Then** `agendamento-confirmacao-service` sobe em Fargate com schema próprio `agendamento_confirmacao` (AD-10), security group liberando só o SG do `gateway-service` na porta HTTP e o SG do `liberacao-repasse-service` na porta gRPC (AD-11), rota registrada no gateway, health-check público, e segredos via variável de ambiente/Secrets Manager (NFR-2/NFR-6)

### Story 1.2: Abertura da Janela de Confirmação e Notificação

Como Paciente com Agendamento marcado,
quero ser notificado quando a Janela de Confirmação abre,
para que eu saiba que preciso confirmar ou recusar presença dentro do prazo.

**Acceptance Criteria:**

**Given** um Agendamento em `AGUARDANDO_JANELA` cujo horário de abertura da janela já chegou
**When** o poller de abertura roda
**Then** o Agendamento transiciona para `AGUARDANDO_CONFIRMACAO` e um evento `NotificacaoConfirmacaoPublicada` é publicado via outbox na mesma transação
**And** o reprocessamento do poller sobre o mesmo Agendamento não gera uma segunda notificação (escrita condicional `WHERE status = 'AGUARDANDO_JANELA'`)

### Story 1.3: Confirmação de Presença

Como Paciente notificado dentro da Janela de Confirmação,
quero confirmar minha presença via API,
para que minha vaga não seja liberada.

**Acceptance Criteria:**

**Given** um Agendamento em `AGUARDANDO_CONFIRMACAO`
**When** o Paciente confirma presença via API
**Then** o Agendamento transiciona para `CONFIRMADO` (terminal) via escrita condicional (`UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'`) e um evento `ConfirmacaoRegistrada` é publicado
**And** a mesma confirmação repetida para o mesmo `agendamentoId` retorna sucesso silencioso, sem novo registro (idempotência)

**Given** um Agendamento fora da Janela (ainda `AGUARDANDO_JANELA`, ou já `LIBERADO`)
**When** uma tentativa de confirmação é feita
**Then** a API retorna erro `409` explicando o motivo (janela ainda não aberta, ou vaga já liberada) — nunca aceita silenciosamente

**Given** Confirmação (esta story) e Recusa (Story 1.4) chegando concorrentemente para o mesmo Agendamento em `AGUARDANDO_CONFIRMACAO`
**When** ambas competem pela mesma transição de estado
**Then** a escrita condicional garante que só uma vence; a perdedora recebe o erro `409` acima, nunca as duas committam

### Story 1.4: Recusa Ativa e Liberação Imediata da Vaga

Como Paciente que não poderá comparecer,
quero recusar minha presença via API dentro da Janela de Confirmação,
para que minha vaga seja liberada imediatamente e possa ajudar outro paciente.

**Acceptance Criteria:**

**Given** um Agendamento em `AGUARDANDO_CONFIRMACAO`
**When** o Paciente recusa presença via API
**Then** o Agendamento transiciona para `LIBERADO` com `motivoLiberacao = RECUSA` via escrita condicional (`UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'`), sem esperar o fim da Janela
**And** os eventos `RecusaRegistrada` e `VagaLiberada` são publicados via outbox na mesma transação

**Given** um Agendamento ainda `AGUARDANDO_JANELA` (janela não aberta)
**When** uma tentativa de recusa é feita
**Then** a API retorna erro `409` (janela ainda não aberta), sem alterar o Agendamento

**Given** um Agendamento já `CONFIRMADO`, ou já `LIBERADO` com `motivoLiberacao = NAO_CONFIRMADO`
**When** uma tentativa de recusa é feita
**Then** a API retorna erro `409` de estado inválido, sem alterar o Agendamento

**Given** a mesma Recusa reenviada (retry de rede) para um Agendamento já `LIBERADO` com `motivoLiberacao = RECUSA` pela própria recusa
**When** a tentativa repetida chega
**Then** a API retorna sucesso silencioso (idempotência), não erro de conflito

### Story 1.5: Expiração da Janela e Liberação Automática

Como sistema,
quero marcar automaticamente um Agendamento como Não Confirmado quando a Janela expira sem resposta,
para que a vaga não fique presa indefinidamente por ausência de ação do Paciente.

**Acceptance Criteria:**

**Given** um Agendamento em `AGUARDANDO_CONFIRMACAO` cujo prazo de expiração já passou
**When** o poller de expiração roda
**Then** o Agendamento transiciona para `LIBERADO` com `motivoLiberacao = NAO_CONFIRMADO`, e os eventos `AgendamentoNaoConfirmado` e `VagaLiberada` são publicados na mesma transação
**And** sob múltiplas instâncias do poller rodando concorrentemente, a escrita condicional garante que apenas uma transição ocorre por Agendamento (sem lock distribuído explícito)

## Epic 2: Liberação e Repasse de Vaga

Consulta à Lista de Espera por ordem de chegada, geração automática de Sugestão de Repasse ao liberar uma vaga, e decisão humana (confirmar/recusar) pelo Gestor de Agenda. Serviço: `liberacao-repasse-service` (renomeado/podado de `matching-alocacao-service`, AD-1). Depende do evento `VagaLiberada` do Epic 1. FRs: FR-8, FR-9, FR-10, FR-11 (+ base de escrita para FR-1).

### Story 2.1: Registrar Catálogo de Recurso e Entrada na Lista de Espera

Como sistema de ingestão (seed-adapter),
quero registrar o catálogo de Recurso e as entradas de Lista de Espera (resolvendo o Paciente via gRPC),
para que a Lista de Espera exista, ordenada por chegada, pronta para consulta e geração de sugestões.

**Preparação do serviço** (pré-requisito de implementação, fora do escopo de teste BDD): `matching-alocacao-service` é renomeado para `liberacao-repasse-service`, reaproveitando `Alocacao`/`ConfirmarAlocacao`/`RecusarSugestao` como molde estrutural renomeado para o domínio de Repasse (`RepasseConfirmado`/`ConfirmarRepasse`/`RecusarSugestaoRepasse`/`SugestaoRepasseRecusada`), e descartando por completo a infraestrutura de réplica de Score (`ScoreReplica`, `ScoreBootstrapService`, `TriagemScoreClient`, `ScoreCalculadoConsumerJob`) e o mecanismo antigo de delay (`LiberacaoAgendadaRelayJob`/fila SQS de delay) — AD-1. Definition of Done desta etapa: build verde, sem classe/job do domínio antigo remanescente.

**Acceptance Criteria:**

**Given** um `codigoRecurso` novo ou já existente
**When** o catálogo de Recurso é registrado
**Then** o upsert é idempotente — reexecutar não duplica o Recurso

**Given** um CPF de Paciente (recebido transientemente do `seed-adapter`, nunca persistido — AD-8) e um `recursoId` de destino
**When** uma entrada de Lista de Espera é registrada
**Then** o Paciente é resolvido para `pacienteId` via gRPC `ResolverOuCriarPaciente` e a entrada é criada com `criadoEm` = timestamp de chegada

**Given** o mesmo par `pacienteId`+`recursoId` já registrado na Lista de Espera
**When** o registro é tentado novamente
**Then** nenhuma entrada duplicada é criada (dedup por par)

**Given** o gRPC `ResolverOuCriarPaciente` expira (timeout curto, sem retry — AD-8) ou está indisponível
**When** a entrada de Lista de Espera é registrada
**Then** a entrada não é criada e a falha é reportada explicitamente ao chamador

**Given** o construto CDK ainda inexistente para este serviço
**When** o deploy é executado
**Then** `liberacao-repasse-service` sobe em Fargate com schema próprio `liberacao_repasse` (AD-10), security group liberando só o SG do `gateway-service` na porta HTTP e permitindo gRPC de saída para o SG do `agendamento-confirmacao-service` (AD-11), rota registrada no gateway, health-check público, e segredos via variável de ambiente/Secrets Manager (NFR-2/NFR-6)

### Story 2.2: Consulta da Lista de Espera

Como usuário autenticado (Gestor de Agenda ou Auditor),
quero consultar a Lista de Espera de um Recurso ordenada por ordem de chegada,
para que eu veja quem está aguardando sem qualquer viés clínico.

**Acceptance Criteria:**

**Given** um Recurso com entradas na Lista de Espera
**When** a Lista é consultada
**Then** os candidatos retornam ordenados exclusivamente por `criadoEm` (ordem de chegada) — nunca por gravidade, especialidade ou qualquer proxy de julgamento clínico

**Given** um `recursoId` existente sem entradas na Lista de Espera
**When** a Lista é consultada
**Then** a resposta retorna explicitamente "sem candidatos", nunca um erro

**Given** um `recursoId` inexistente
**When** a Lista é consultada
**Then** a API retorna erro `404`, distinto de "sem candidatos"

**Given** qualquer usuário autenticado, independentemente do claim `role`
**When** a Lista é consultada
**Then** o acesso é permitido — sem RBAC aplicado nesta fase (FR-14)

### Story 2.3: Geração Automática de Sugestão de Repasse

Como sistema,
quero gerar automaticamente uma Sugestão de Repasse ao consumir o evento `VagaLiberada`,
para que o Gestor de Agenda receba um candidato pronto sem precisar descobrir a vaga vazia por conta própria.

**Acceptance Criteria:**

**Given** um evento `VagaLiberada` consumido para um `agendamentoId` ainda sem `SugestaoRepasse`
**When** a Lista de Espera do `recursoId` correspondente não está vazia
**Then** uma `SugestaoRepasse` é criada apontando o primeiro candidato elegível por ordem de chegada — excluindo candidatos que já tenham uma `SugestaoRepasse` `PENDENTE` para outra vaga — e `SugestaoRepasseGerada` é publicado (serve também como notificação mock ao Gestor)

**Given** o mesmo evento `VagaLiberada` reentregue (redrive de DLQ ou reprocessamento)
**When** já existe `SugestaoRepasse` para aquele `agendamentoId`
**Then** a mensagem é descartada sem efeito — garantido por constraint única em `SugestaoRepasse.agendamentoId` (idempotência)

**Given** dois eventos `VagaLiberada` do mesmo `recursoId` processados concorrentemente
**When** ambos disputam o mesmo candidato do topo da Lista de Espera
**Then** a seleção do candidato é uma operação atômica (ex.: leitura com lock de linha) — nunca o mesmo candidato recebe duas `SugestaoRepasse` simultâneas para vagas diferentes

**Given** a Lista de Espera do `recursoId` está vazia no momento da liberação
**When** o evento é consumido
**Then** a vaga permanece Liberada sem sugestão pendente, sem repescagem automática retroativa

### Story 2.4: Confirmação do Repasse

Como Gestor de Agenda,
quero confirmar uma Sugestão de Repasse,
para que a vaga seja definitivamente repassada ao paciente sugerido.

**Acceptance Criteria:**

**Given** uma `SugestaoRepasse` em estado `PENDENTE`
**When** o Gestor confirma via API
**Then** a transição é feita por escrita condicional (`UPDATE ... WHERE status = 'PENDENTE'`), criando um `RepasseConfirmado` (definitivo), e a vaga deixa de aparecer como Liberada

**Given** uma `SugestaoRepasse` já resolvida (confirmada ou recusada por outro Gestor)
**When** uma tentativa de confirmação é feita
**Then** a escrita condicional afeta zero linhas e a API retorna erro `409` de conflito, sem sobrescrever a decisão já tomada

### Story 2.5: Recusa do Repasse e Nova Sugestão

Como Gestor de Agenda,
quero recusar uma Sugestão de Repasse,
para que o próximo candidato da Lista de Espera seja sugerido automaticamente, pulando os já recusados.

**Acceptance Criteria:**

**Given** uma `SugestaoRepasse` em estado `PENDENTE`
**When** o Gestor recusa via API
**Then** a transição é feita por escrita condicional (`UPDATE ... WHERE status = 'PENDENTE'`), `SugestaoRepasseRecusada` é registrado (par `recursoId`+`pacienteId`) e uma nova `SugestaoRepasse` é gerada para o próximo candidato elegível, pulando os já recusados para aquela vaga

**Given** uma `SugestaoRepasse` já resolvida (confirmada, ou já recusada por outro Gestor)
**When** uma tentativa de recusa é feita
**Then** a escrita condicional afeta zero linhas e a API retorna erro `409` de conflito

**Given** nenhum candidato elegível restante na Lista de Espera após a recusa
**When** a nova sugestão é calculada
**Then** a vaga permanece Liberada sem sugestão pendente

## Epic 3: Log Auditável e Consulta de Auditoria

Toda decisão do ciclo (notificação, confirmação/recusa, liberação, sugestão, repasse) fica registrada de forma imutável e consultável por Paciente/Agendamento, com motivo e timestamp. Serviço: `auditoria-service` (novo, desenho retomado do AD-10 do spine antigo). Consome eventos dos Epics 1 e 2 via SQS FIFO, nunca de forma síncrona. FRs: FR-12, FR-13.

### Story 3.1: Registro de Decisão no Log Auditável

Como sistema,
quero consumir os eventos publicados pelos serviços de domínio e registrar cada decisão no Log Auditável,
para que toda decisão do ciclo (notificação, confirmação, recusa, liberação, sugestão, repasse) fique explicável com motivo e timestamp.

**Acceptance Criteria:**

**Given** um evento de domínio consumido (`ConfirmacaoRegistrada`, `RecusaRegistrada`, `AgendamentoNaoConfirmado`, `VagaLiberada`, `NotificacaoConfirmacaoPublicada`, `SugestaoRepasseGerada`, `RepasseConfirmado` ou `SugestaoRepasseRecusada`)
**When** o evento é processado
**Then** uma entrada append-only é criada com o `eventId` de origem, o campo `motivo` (nulo apenas para `ConfirmacaoRegistrada`/`NotificacaoConfirmacaoPublicada`/`SugestaoRepasseGerada`, preenchido nos demais) e timestamp

**Given** o mesmo `eventId` reentregue (redrive de DLQ ou reprocessamento do consumidor)
**When** o evento é processado novamente
**Then** nenhuma entrada duplicada é criada (dedup por `eventId`)

**Given** um evento de `eventType` não reconhecido (evolução aditiva de schema, NFR-4)
**When** ele chega ao consumidor
**Then** é registrado genericamente (payload bruto + `eventType`) sem quebrar o consumidor nem bloquear os demais eventos da fila

**Given** uma entrada já registrada
**When** qualquer tentativa de alteração é feita
**Then** a entrada permanece imutável — o Log Auditável é append-only

**Given** o construto CDK ainda inexistente para este serviço
**When** o deploy é executado
**Then** `auditoria-service` sobe em Fargate com schema próprio `auditoria` (AD-10), security group liberando só o SG do `gateway-service` na porta HTTP e permitindo consumo das filas SQS FIFO dedicadas (sem gRPC-client, nunca resolve CPF — AD-8), rota registrada no gateway para os endpoints de consulta, health-check público, e segredos via variável de ambiente/Secrets Manager (NFR-2/NFR-6)

### Story 3.2: Consulta de Auditoria por Paciente ou Agendamento

Como Auditor,
quero consultar o histórico completo do Log Auditável de um Paciente ou de um Agendamento específico,
para que eu possa justificar decisões passadas com dados, não com "confie em nós".

**Acceptance Criteria:**

**Given** um Paciente ou Agendamento com entradas registradas
**When** o histórico é consultado
**Then** a linha do tempo completa retorna ordenada cronologicamente, com motivo e timestamp de cada decisão

**Given** um Paciente ou Agendamento sem nenhuma entrada
**When** o histórico é consultado
**Then** a resposta retorna explicitamente "sem histórico", nunca um erro

**Given** qualquer usuário autenticado, independentemente do claim `role`
**When** o histórico é consultado
**Then** o acesso é permitido — sem RBAC aplicado nesta fase (FR-14)

## Epic 4: Carga de Dados Sintéticos (Camada Adaptadora)

O sistema inteiro é populado com um dataset de demonstração via um job idempotente (catálogo de Recurso → Agendamentos → Lista de Espera, nesta ordem), sem passos manuais — habilita a demonstração ponta a ponta das jornadas do PRD (UJ-1 Paciente confirma presença, UJ-2 Paciente não responde e a vaga é repassada, UJ-3 Gestor decide o repasse, UJ-4 Auditor investiga; detalhadas em `_bmad-output/specs/spec-confirmasus/user-journeys.md`). Serviço: `seed-adapter` (Lambda/Quarkus). Depende dos endpoints de escrita das Stories 1.1 e 2.1. FR: FR-1 completa.

### Story 4.1: Autenticação do seed-adapter e Upsert do Catálogo de Recurso

Como job de seed (seed-adapter),
quero autenticar-me com um usuário técnico pré-cadastrado e carregar o catálogo de Recurso de forma idempotente,
para que o catálogo exista antes de qualquer Agendamento ou entrada de Lista de Espera ser carregada.

**Acceptance Criteria:**

**Given** um usuário técnico pré-cadastrado no `auth-service`
**When** o seed-adapter inicia a execução
**Then** ele obtém um JWT válido e usa esse token em toda chamada subsequente ao gateway — nunca chama um serviço de domínio diretamente

**Given** falha de autenticação (credencial inválida, ou `auth-service`/`gateway-service` indisponível)
**When** o seed-adapter tenta obter o JWT
**Then** a execução aborta com erro claro, sem prosseguir para o upsert do catálogo

**Given** o catálogo de Recurso da carga sintética
**When** o upsert é executado
**Then** cada Recurso é criado ou reaproveitado por `codigoRecurso`, e reexecutar a carga não duplica registros

### Story 4.2: Carga Idempotente de Agendamentos Sintéticos

Como job de seed (seed-adapter),
quero carregar os Agendamentos sintéticos, incluindo os que simulam estados pós-confirmação, após o catálogo de Recurso existir,
para que o dataset de demonstração tenha Agendamentos prontos para o ciclo de confirmação (UJ-1, UJ-2).

**Acceptance Criteria:**

**Given** o catálogo de Recurso já carregado (Story 4.1)
**When** o seed-adapter registra um Agendamento via o endpoint da Story 1.1
**Then** o Agendamento é criado com Paciente, Recurso e horário definidos

**Given** um cenário de demo que exige um estado além de `AGUARDANDO_JANELA` (confirmado, recusado, não confirmado, liberado sem lista de espera, repasse já confirmado, sugestão recusada gerando nova sugestão)
**When** o seed-adapter monta o dataset
**Then** ele encadeia, para cada Agendamento, as chamadas de API já entregues pelas Stories 1.3/1.4/1.5/2.4/2.5 (confirmar, recusar, ou retroceder `janelaAbreEm`/`janelaExpiraEm` para o poller processar de imediato) — nenhum estado de demo depende de endpoint ainda não implementado nas epics anteriores
**And** reexecutar a carga não duplica Agendamentos já criados

### Story 4.3: Carga Idempotente da Lista de Espera

Como job de seed (seed-adapter),
quero carregar as entradas de Lista de Espera após o catálogo de Recurso existir,
para que cenários de Vaga Liberada com candidato pendente sejam demonstráveis (UJ-2, UJ-3).

**Acceptance Criteria:**

**Given** o catálogo de Recurso já carregado (Story 4.1) e o `agendamento-confirmacao-service` disponível para resolução de CPF (Story 1.1 implantada)
**When** o seed-adapter carrega as entradas de Lista de Espera via o endpoint da Story 2.1
**Then** cada entrada é criada com `criadoEm` = timestamp de chegada, associada ao `recursoId` correto
**And** reexecutar a carga não duplica entradas já criadas
