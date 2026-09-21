# Epic 4 Context: Log Auditável e Consulta de Auditoria

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Tornar toda decisão do sistema rastreável e defensável com dados: registro imutável (append-only) de cada evento do ciclo (notificação, confirmação, recusa, expiração, liberação, sugestão de repasse, decisão de repasse) com timestamp e motivo explícito. Um novo serviço (`auditoria-service`) consome eventos dos Epics 1 e 2 via SQS FIFO e oferece endpoints de consulta por Paciente ou Agendamento, habilitando UJ-4 (Auditor investiga uma reclamação). Atende FR-12 (registro) e FR-13 (consulta).

## Stories

- Story 4.1: Registro de Decisão no Log Auditável
- Story 4.2: Consulta de Auditoria por Paciente ou Agendamento

## Requirements & Constraints

**Functional:**
- **FR-12 (Registro em Log Auditável)**: Toda notificação, Confirmação, Recusa, expiração (Não Confirmado), Liberação, Sugestão de Repasse e decisão de repasse é registrada com `eventId` de origem, `motivo` (preenchido para recusa/expiração/liberação/decisão; nulo para confirmação/notificação/sugestão gerada) e `timestamp`.
- **FR-13 (Consulta de Auditoria)**: Um Auditor consulta o histórico completo do Log Auditável de um Paciente ou Agendamento específico, ordenado cronologicamente, com motivo e timestamp de cada decisão.
- **Eventos consumidos**: `ConfirmacaoRegistrada`, `RecusaRegistrada`, `AgendamentoNaoConfirmado`, `VagaLiberada`, `NotificacaoConfirmacaoPublicada`, `SugestaoRepasseGerada`, `RepasseConfirmado`, `SugestaoRepasseRecusada`.
- **Idempotência**: O mesmo `eventId` reentregue (redrive de DLQ ou reprocessamento) não gera entrada duplicada.
- **Schema evolutivo**: Um evento de `eventType` não reconhecido (evolução aditiva de schema, NFR-4) é registrado genericamente (payload bruto + `eventType`) sem quebrar o consumidor nem bloquear os demais eventos da fila.
- **Imutabilidade**: Uma entrada já registrada no Log Auditável nunca é alterada — é append-only.
- **Respostas claras**: Consulta a um Paciente/Agendamento sem histórico retorna explicitamente "sem histórico", nunca um erro.
- **Sem RBAC nesta fase**: Qualquer usuário autenticado (independentemente do claim `role`) pode consultar o histórico — FR-14.

**Non-Functional:**
- **Consumo assíncrono**: `auditoria-service` consome eventos via SQS FIFO e nunca é chamado de forma síncrona — continuidade sob falha parcial (NFR-1): confirmação/recusa/liberação continuam funcionando mesmo se o serviço de auditoria estiver temporariamente indisponível.
- **Observabilidade**: Logs estruturados com `X-Correlation-Id` propagado ponta a ponta (NFR-2).
- **Reprodutibilidade**: O serviço sobe via `cdk deploy` sem passos manuais (NFR-3).
- **Testes**: Cobertura ≥90% na camada de domínio (JaCoCo) + PIT + testes de integração cobrindo os contratos de consumo de evento e consulta (NFR-5).

## Technical Decisions

**Architecture:**
- **Ad-7 (Só leitura + consumidor)**: `auditoria-service` é puramente read-side e consumidor de eventos — nunca chamado de forma síncrona, nunca publica eventos próprios. Não resolvе CPF (Ad-8 — dados já chegam dentro do evento, referenciando `pacienteId`).
- **Ad-3 (Event sourcing via outbox/relay)**: Eventos são publicados pelos serviços de domínio via outbox (mesma transação do comando) + relay poller + SNS FIFO + SQS FIFO dedicada. `auditoria-service` consome dessa fila.
- **Ad-10 (Isolamento por schema)**: `auditoria-service` tem seu próprio schema PostgreSQL `auditoria`, isolado dos demais com `REVOKE` cross-schema (enforcement via ArchUnit no CI).
- **Ad-11 (Security groups)**: SG do `gateway-service` na porta HTTP de app; SQS FIFO acessível sem porta (permissão de API AWS).

**Data Model:**
- **Log Auditável**: Tabela append-only `decisao_auditoria` com campos: `id` (PK), `eventId` (unique), `agendamentoId`, `pacienteId`, `tipoDecisao` (enum: NOTIFICACAO, CONFIRMACAO, RECUSA, NAO_CONFIRMADO, LIBERACAO, SUGESTAO_GERADA, REPASSE_CONFIRMADO, SUGESTAO_RECUSADA), `motivo` (nullable), `timestamp`, `criadoEm`.
- **Índices**: `(agendamentoId, timestamp)` para consulta por Agendamento ordenada; `(pacienteId, timestamp)` para consulta por Paciente ordenada.

**Versionamento & Contratos:**
- **NFR-4 (Contratos versionados)**: Eventos carregam `version` (semântica aditiva). Incompatibilidade vai para DLQ.
- **Endpoints REST**: Versionados (`/v1/auditoria/agendamento/{id}`, `/v1/auditoria/paciente/{id}`).

**Testes & Qualidade:**
- **Cobertura**: ≥90% JaCoCo na camada de domínio (`com.confirmasus.auditoria.domain.*`); PIT mutation score.
- **Integração**: Teste cobrindo consumo de evento real (mock SQS ou testcontainers), armazenamento, e consulta posterior.
- **BDD**: Cenários Cucumber/JVM cobrindo UJ-4 (Auditor investiga) ponta a ponta.

## Cross-Story Dependencies

- **Story 4.1 depende de Epics 1 e 2**: Eventos consumidos são gerados pelos serviços de domínio já implementados nas histórias 1.2-1.5 e 2.3-2.5. A fila SQS FIFO deve estar provisionada (CDK de Epic 3 ou anterior).
- **Story 4.2 depende de Story 4.1**: Dados precisam estar registrados no Log antes de serem consultáveis.
- **Fila SQS FIFO**: Pré-requisito que deve estar provisionada — confirmar que CDK já criou `auditoria-decisoes-fifo.fifo` com DLQ (`auditoria-decisoes-dlq-fifo.fifo`, maxReceiveCount=5).
