# Epic 3 Context: Matching, Alocação e Liberação de Recursos

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Um Regulador consulta a fila de Pacientes priorizada por Prioridade Efetiva (Score clínico + Urgência Acumulada), pede uma sugestão de Matching para um Recurso disponível específico e decide — confirmar (gera Alocação) ou recusar com motivo — sem nunca selecionar manualmente um Paciente fora da ordem objetiva do sistema. Depois de confirmado, o Recurso retorna sozinho ao pool após o tempo de atendimento simulado, ficando elegível para nova sugestão. Este epic fecha o ciclo sugestão → confirmação → uso → liberação e é o núcleo de justiça objetiva do FilaJusta: garante, via Aging com teto, que Pacientes de prioridade moderada avancem na fila sem nunca ultrapassar por artifício quem tem gravidade clínica real maior.

## Stories

- Story 3.1: Consulta da Fila Priorizada com Urgência Acumulada -- **dividida em 3 sub-stories em 2026-09-08** (spec único excedia ~2500 tokens por cruzar `triagem-score-service` e `matching-alocacao-service`; decisão do usuário no checkpoint de token count do `bmad-build`, ver `sprint-status.yaml`):
  - `3-1a` ListarScoresAtuais (endpoint interno em `triagem-score-service`)
  - `3-1b` réplica local de Score + consumidor SQS FIFO + fila CDK (`matching-alocacao-service`, sem HTTP)
  - `3-1c` `GET /v1/fila` + bootstrap síncrono a frio (liga 3.1a+3.1b ao AC original da story)
- Story 3.2: Sugestão de Matching para um Recurso com Desempates -- **dividida em 2 sub-stories em 2026-09-10** (spec único excedia ~2700-2800 tokens por cruzar `triagem-score-service` e `matching-alocacao-service` além de introduzir o domínio `Recurso` do zero; decisão do usuário no checkpoint de token count do `bmad-build`, mesmo padrão da Story 3.1 — ver `sprint-status.yaml` e `deferred-work.md`):
  - `3-2a` Expor `numeroSequencialTriagem` em `GET /internal/scores` (`triagem-score-service`) -- o campo já existe no payload do evento `ScoreCalculado` (`RegistrarTriagem`), só nunca foi lido/exposto
  - `3-2b` Propaga `numeroSequencialTriagem` até `ScoreReplica`, cria domínio/persistência/upsert interno de `Recurso`, e `GET /v1/recursos/{id}/sugestao` com o algoritmo de tiers de desempate (`matching-alocacao-service`, depende de 3-2a)
- Story 3.3: Confirmação ou Recusa da Sugestão de Matching
- Story 3.4: Liberação Automática de Recurso

## Requirements & Constraints

- Prioridade Efetiva = `score + min(k × max(0, horas_espera), teto)`, sempre recomputada sob demanda a partir do estado persistido (nunca cacheada em memória) para que múltiplas instâncias concordem por construção; `horas_espera` nunca é negativa mesmo sob defasagem de relógio entre serviços.
- Teto = 20% da amplitude do Score (`teto = 20`), atingido entre 12–24h de espera simulada (`k ≈ 1,111 pontos/hora`, ponto médio = 18h). Empate de Score inicial: quem espera mais tempo tem Prioridade Efetiva igual ou maior, nunca menor.
- A consulta da fila e da sugestão reflete, sem reprocessamento manual, qualquer Triagem nova ou Recurso liberado.
- Sugestão de Matching nunca aloca automaticamente — sempre exige confirmação humana explícita. É recalculada a cada consulta e não reserva o Paciente: o mesmo Paciente pode aparecer sugerido para mais de um Recurso simultaneamente até uma confirmação o consumir. Nenhum Recurso elegível retorna resposta indicando ausência de sugestão, nunca erro.
- Desempate entre Pacientes com Prioridade Efetiva igual: vence a Triagem mais antiga (price-time priority); empate residual de timestamp desempata pelo número de sequência da Triagem (não por `pacienteId`, para permanecer explicável a um auditor).
- Desempate entre Recursos elegíveis para o mesmo Paciente: menor `especificidadeRank` suficiente vence sempre (nunca sugere o mais específico se o genérico resolve); empate de rank prefere o ocioso há mais tempo; empate residual final por `recursoId`.
- Confirmar uma sugestão remove o Recurso do pool e cria uma Alocação; confirmações concorrentes para o mesmo Recurso: a segunda é rejeitada com `409`. Confirmar para um Paciente já alocado a outro Recurso também é `409`, e o sistema recalcula automaticamente a próxima sugestão elegível para aquele Recurso.
- Recusa exige motivo obrigatório (`400` se ausente); o par (recursoId, pacienteId) recusado nunca é resugerido para aquele Recurso, mas o Paciente permanece elegível para qualquer outro Recurso com a mesma Prioridade Efetiva — sem penalização.
- Toda confirmação, recusa, sugestão gerada (quando o Paciente sugerido muda) e liberação alimenta o Log Auditável (fatores, timestamp, IDs internos).
- Recurso liberado retorna ao pool com o mesmo `especificidadeRank`; liberação é sempre automática — não existe endpoint de liberação manual.
- Redelivery da mensagem de delay de liberação não libera o mesmo Recurso duas vezes nem duplica o evento `RecursoLiberado` — idempotência por `alocacaoId`.
- Cobertura de linha ≥90% (JaCoCo) na camada de domínio, complementada por teste de mutação (PIT), testes de integração de contrato e cenários de aceitação BDD cobrindo este trecho do fluxo ponta a ponta.
- Continuidade sob falha parcial: este serviço deve seguir operando (fila, sugestão) mesmo com Triagem/Auditoria temporariamente indisponíveis, processando propagação pendente quando eles voltarem.

## Technical Decisions

- Serviço dono: `matching-alocacao-service` (Clean Architecture — `domain/` com Recurso, SugestaoMatching, Alocacao, Aging → `application/command|query/` → `infrastructure/`). CQRS lógico: `ConfirmarAlocacao`/`RecusarSugestao`/`LiberarRecurso` (interno, não exposto via REST) em `command`; `ConsultarFila`/`ConsultarSugestao` em `query` — a própria consulta de sugestão atualiza a tabela de apoio `ultima_sugestao_registrada` e, ao detectar mudança de Paciente sugerido para aquele Recurso, publica `SugestaoGerada` via outbox.
- Persistência: schema próprio `matching_alocacao` no cluster PostgreSQL 18 compartilhado, usuário de banco isolado. Alocação gravada sob constraint única de banco `(recursoId, status=ativa)` — mecanismo que garante o `409` em confirmações concorrentes.
- Réplica local somente-leitura de Score por Paciente, mantida por consumo do evento `ScoreCalculado` (fila SQS FIFO própria) via upsert idempotente: last-write-wins por `occurredAt`, desempate por `eventId` em ordem lexicográfica quando `occurredAt` empata. Réplica vazia em boot a frio dispara bootstrap REST síncrono a um endpoint interno de `triagem-score-service` que lista os Scores atuais, antes de aceitar tráfego em `ConsultarFila`.
- `especificidadeRank` de Recurso fixado no seed: 1=leito comum, 2=leito UTI, 3=leito UTI especializado, 4=especialista — não hardcoded fora de config, mas os valores concretos vêm do dataset de seed (Epic 5).
- Ciclo de vida do Recurso: confirmação publica mensagem numa fila SQS **standard** dedicada (distinta das filas FIFO de eventos) com delay = duração do atendimento simulado (2–5 min por tipo, ≤15 min — limite físico do `DelaySeconds` do SQS), carregando o `correlationId` da confirmação original. Consumidor da liberação é idempotente por `alocacaoId`; o evento `RecursoLiberado` resultante segue o padrão outbox normal (transação local + fila FIFO de eventos, `MessageGroupId = recursoId`).
- Eventos publicados por este serviço (`SugestaoGerada`, `AlocacaoConfirmada`, `SugestaoRecusada`, `RecursoLiberado`) seguem o mesmo padrão outbox do Epic 2: gravados na mesma transação local do comando, `eventId` (UUID v4) gerado uma vez e nunca regenerado em retry, relay publica em tópico SNS FIFO, `MessageGroupId = recursoId`, DLQ com `maxReceiveCount = 5`. Envelope: `{eventId, eventType, occurredAt, version, correlationId, payload}`, schema companion versionado, mudanças só aditivas.
- Valores calibráveis (`filajusta.aging.k`, `filajusta.aging.teto`, `filajusta.liberacao.duracao.*` por tipo de Recurso) centralizados em `application.yml` do serviço — fonte única, nunca espalhados no código.
- Convenções: eventos em PascalCase passado; IDs internos = UUID v4; datas ISO-8601 UTC; erros REST em RFC 7807; contrato versionado (`/v1/`); `correlationId` propagado do gateway até o evento.
- Autenticação: endpoints protegidos exigem JWT válido emitido por `auth-service` e validado no `gateway-service` (Epic 1) — este epic não implementa autenticação, apenas depende dela.

## Cross-Story Dependencies

- Story 3.2 (sugestão) e 3.3 (confirmação/recusa) dependem da fila priorizada de 3.1 já existir — não há sugestão sem uma ordenação por Prioridade Efetiva calculada.
- Story 3.3 (recusa) depende de 3.2 para recalcular a próxima sugestão elegível após recusa ou após um `409` de Paciente já alocado.
- Story 3.4 (liberação) depende de 3.3: só existe delay de liberação para uma Alocação previamente confirmada.
- Depende do Epic 2 (`triagem-score-service`) publicando corretamente o evento `ScoreCalculado` — tanto para manter a réplica local de Score quanto para o bootstrap REST síncrono em boot a frio de réplica vazia.
- Depende de Epic 1 (`gateway-service` + `auth-service`) para autenticação dos endpoints REST expostos (fila, sugestão, confirmação, recusa).
- Epic 4 (Auditoria) consome os eventos `SugestaoGerada`, `AlocacaoConfirmada`, `SugestaoRecusada` e `RecursoLiberado` publicados por este epic para registrar as decisões no Log Auditável — depende deste epic estar publicando corretamente, mas não bloqueia a entrega deste epic.
- Epic 5 (`seed-adapter`) depende da API de Recurso deste epic já existir para popular o dataset sintético via upsert por `codigoRecurso`.
