---
title: 'Registro de Decisão no Log Auditável (Story 4.1)'
type: 'feature'
created: '2026-09-21'
status: 'in-progress'
review_loop_iteration: 0
baseline_commit: 'b9936b10f6b3932b5fb4e215ddd779aab2e28dd3'
context: ['_bmad-output/implementation-artifacts/epic-4-context.md']
---

<!-- Target: 900–1300 tokens. -->

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nenhuma decisão do sistema (notificação, confirmação, recusa, liberação, sugestão, repasse) é rastreável hoje. Não existe registro imutável que permita a um Auditor justificar com dados por que uma vaga foi repassada a um paciente — falta o componente "auditoria-service" que consome eventos e mantém o log.

**Approach:** Criar `auditoria-service` (novo serviço Spring Boot) que consome eventos via SQS FIFO dedicada. Para cada evento recebido (ConfirmacaoRegistrada, RecusaRegistrada, AgendamentoNaoConfirmado, VagaLiberada, NotificacaoConfirmacaoPublicada, SugestaoRepasseGerada, RepasseConfirmado, SugestaoRepasseRecusada), registrar uma entrada append-only no banco com `eventId`, `motivo` (onde aplicável), `timestamp`. Garantir idempotência por `eventId`, registro genérico de eventos desconhecidos (evolução de schema), e imutabilidade pós-escrita.

## Boundaries & Constraints

**Always:**
- Consumo assíncrono: SQS FIFO dedicada `auditoria-decisoes-fifo`, sem chamadas síncronas.
- Cada evento é persistido uma única vez (dedup por `eventId`).
- Eventos de `eventType` desconhecido são registrados com payload bruto + `eventType`, sem erro nem bloqueio dos demais.
- Log Auditável é append-only — nunca altera entrada já registrada.
- Schema `auditoria` isolado em PostgreSQL (AD-10), com ArchUnit enforcing `REVOKE` cross-schema.
- Cobertura ≥90% JaCoCo na camada de domínio, PIT mutation score, teste de integração com SQS real (Testcontainers).
- Segurança: health-check público, segredos via env/Secrets Manager, `X-Correlation-Id` propagado (NFR-2).

**Ask First:**
- Nome/estrutura interna de pacotes: seguir convenção do auth-service / gateway-service (e.g., `com.confirmasus.auditoria.domain.*`, `infrastructure.*`)? 
- Quantas entradas de teste devem estar no dataset de seed (Story 4 futuro) para validar ponta a ponta?
- Timing de CDK provisioning: Story 4.1 pode usar Testcontainers para mockar SQS (teste de unidade/integração local), mas validação de AC final exige fila real `auditoria-decisoes-fifo.fifo` já provisionada? Se sim, criar story de "Infraestrutura CDK para auditoria-service" antes de 4.1 entrar em produção, ou considerar como parte de uma história de "Preparação" inicial?

**Never:**
- Não criar CDK ainda (é a Story 3.1.2 do sprint-status futuro, deferida).
- Não publicar eventos próprios (só consome).
- Não implementar endpoints de consulta (é a Story 4.2).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| EVENTO_VALIDO | Evento SQS com `eventId`, `eventType`, `payload` reconhecido (ex.: ConfirmacaoRegistrada) | Entrada criada em `decisao_auditoria` com motivo preenchido (ou nulo conforme tipo), `timestamp` = agora | N/A |
| EVENTO_DUPLICADO | Mesmo `eventId` reentregue (DLQ redrive, reprocessamento) | Nenhuma entrada duplicada criada; segunda inserção respeitando constraint único em `eventId` | Constraint violation capturada, log de warn, mensagem descartada (dedup idempotente) |
| EVENTO_DESCONHECIDO | Evento com `eventType` não reconhecido (evolução aditiva de schema) | Entrada criada com `tipoDecisao = GENERICO`, payload bruto persistido, sem erro | N/A |
| CONSUMIDOR_PARADO | SQS FIFO com 10+ mensagens acumuladas, consumidor reinicia | Reprocessa fila respeitando exactly-once semantics (dedup), sem duplicar ou perder | Batch error handling, partial batch retry via DLQ |

</frozen-after-approval>

## Code Map

**Event Publishing Infrastructure (Referência):**
- `agendamento-confirmacao-service/src/main/java/.../relay/RelaySnsPublisherJob.java:1-220` -- Scheduled relay job que poll outbox → SNS FIFO (pattern a replicar para auditoria-service)
- `matching-alocacao-service/.../infrastructure/persistence/EventoOutboxJpaRepository.java` -- JPA repo com @Modifying @Query para marcar evento como enviado

**Event Consumption Pattern (Molde):**
- `matching-alocacao-service/src/main/java/.../relay/ScoreCalculadoConsumerJob.java:1-237` -- SQS FIFO @Scheduled consumer com batch ReceiveMessage, processamento, e deleteMessage condicional (replicar estrutura para auditoria-service DecisaoSqsConsumerJob)

**Data Model - Envelope Padrão:**
- `EventoOutbox.java:25-58` -- estrutura padrão de evento (eventId, eventType, occurredAt, version, correlationId, payload)
- `EventoOutboxRepositorioAdapter.java:1-79` -- padrão Clean Architecture (domain entity → port → JPA adapter com Jackson JSONB)

**CDK Infrastructure (Futuro - Story referência):**
- `infra-cdk/src/main/java/.../ConfirmaSusStack.java:335-427` -- provisioning SNS FIFO topics
- `ConfirmaSusStack.java:362-401` -- provisioning SQS FIFO queues com DLQ (visibilityTimeout=60s, maxReceiveCount=5)
- Padrão: `Queue.Builder`, `DeadLetterQueue.builder()`, `Topic.addSubscription(SqsSubscription)` — será necessário criar `auditoria-decisoes-fifo.fifo` + DLQ em CDK antes do deploy final

**Onde Criar auditoria-service:**
- Novo diretório: `/auditoria-service/` (co-localizado com `auth-service`, `gateway-service`)
- Pacotes: `com.confirmasus.auditoria.domain`, `.application.*`, `.infrastructure.*`
- Parent POM: herdar do projeto (Spring Boot 4.1.1, Spring Cloud 2025.1.3, Java 25)
- Consumer Job paralelo a `ScoreCalculadoConsumerJob.java` — consumir de `auditoria-decisoes-fifo.fifo` e persistir em schema `auditoria`

## Tasks & Acceptance

**Execution:**
- [ ] `auditoria-service/pom.xml` -- criar serviço novo com Spring Boot 4.1.1, dependências SQS, PostgreSQL
- [ ] `auditoria-service/src/main/.../domain/DecisaoAuditoria.java` -- entidade de domínio: `eventId`, `agendamentoId`, `pacienteId`, `tipoDecisao` (enum), `motivo` (nullable), `timestamp`, `criadoEm`
- [ ] `auditoria-service/src/main/.../application/port/DecisaoAuditoriaRepositorio.java` -- porta de persistência
- [ ] `auditoria-service/src/main/.../infrastructure/persistence/DecisaoAuditoriaJpaRepository.java` -- repositório JPA com constraint unique em `eventId`
- [ ] `auditoria-service/src/main/.../infrastructure/event/DecisaoSqsConsumerJob.java` -- consumer job que consome eventos SQS FIFO, mapeia para domínio, persiste
- [ ] `auditoria-service/src/main/resources/application.yml` -- configuração de SQS (queue, credentials, deadletter)
- [ ] `auditoria-service/.../src/test/.../ DecisaoAuditoriaIntegrationTest.java` -- teste de integração com Testcontainers (SQS real ou mock), verifica idempotência e registro correto

**Acceptance Criteria:**
- Given um evento ConfirmacaoRegistrada com `eventId` válido chega à fila SQS FIFO, when o consumidor processa, then uma entrada é criada com `tipoDecisao = CONFIRMACAO`, `motivo = null`, `timestamp` = agora, e o `eventId` fica único (segunda entrega idempotente)
- Given um evento desconhecido (novo `eventType` aditivo) chega, when processado, then é registrado com `tipoDecisao = GENERICO`, payload bruto armazenado, sem exceção nem bloqueio de demais eventos
- Given a tabela `decisao_auditoria` vazia e 5 eventos simultâneos da fila SQS, when consumidor batch-processa, then exatamente 5 entradas são criadas, nenhuma duplicada, nenhuma perdida
- Given uma entrada já registrada com `eventId = X`, when a mesma mensagem chega novamente na fila, then segunda tentativa respeita constraint único — dedup guarantida sem erro de aplicação

## Design Notes

A arquitetura segue Clean Architecture com separação entre domínio (DecisaoAuditoria, lógica de registo), aplicação (consumidor SQS, orquestração) e infraestrutura (JPA, SQS client).

**Enumeração TipoDecisao**:
```java
enum TipoDecisao {
  NOTIFICACAO,           // motivo = null
  CONFIRMACAO,           // motivo = null
  RECUSA,                // motivo preenchido
  NAO_CONFIRMADO,        // motivo preenchido
  LIBERACAO,             // motivo preenchido
  SUGESTAO_GERADA,       // motivo = null
  REPASSE_CONFIRMADO,    // motivo preenchido (qual foi a decisão do gestor)
  SUGESTAO_RECUSADA,     // motivo preenchido
  GENERICO               // motivo = null, payload bruto
}
```

**Dedup**: Tabela `decisao_auditoria` tem constraint `UNIQUE (eventId)`. Quando DLQ ou reprocessamento reinveste o mesmo evento, a inserção falha com integridade (tratado como idempotência de aplicação, não re-thrown).

## Verification

**Commands:**
- `cd auditoria-service && mvn clean test` -- rodar testes unitários + integração
- `mvn jacoco:report` -- gerar cobertura JaCoCo, validar ≥90% na camada `com.confirmasus.auditoria.domain.*`
- `mvn org.pitest:pitest-maven:mutationCoverage` (opcional, sob demanda) -- validar mutation score

**Manual checks:**
- Verificar que `auditoria-service/pom.xml` herda parent do projeto (Spring Cloud 2025.1.3, Java 25, etc.)
- Verificar que aplicação roda em `java -jar target/auditoria-service.jar` sem erros de classpath
- Verificar que log de inicialização menciona consumer job e fila SQS FIFO registrada
