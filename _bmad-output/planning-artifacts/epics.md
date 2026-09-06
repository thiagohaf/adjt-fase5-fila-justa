---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-Fase5-2026-09-05/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-06/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-06/solution-design.md
---

# FilaJusta - Epic Breakdown

## Overview

Este documento decompõe o PRD (`prd-Fase5-2026-09-05`) e a Arquitetura final (`ARCHITECTURE-SPINE.md`, 13 ADs) do FilaJusta em epics e stories implementáveis. Não há documento de UX — o MVP é backend-only (Swagger/Postman), conforme Non-Goals do PRD §5.

## Requirements Inventory

### Functional Requirements

FR-1: Um Profissional de Triagem pode submeter, via API, uma Triagem estruturada (CPF, sintomas, sinais vitais, gravidade percebida); retorna `201` com Score já calculado; sinais vitais ausentes/fora de faixa ou CPF inválido retornam `400`.
FR-2: O sistema associa a Triagem a um Paciente existente pelo CPF ou cria um registro mínimo, gerando/reutilizando um ID interno; CPF nunca propaga além desse ponto.
FR-3: O sistema calcula o Score de Prioridade Clínica de forma síncrona na resposta da Triagem, sem bloquear por propagação a Matching/Auditoria.
FR-4: O algoritmo de Score é determinístico e versionado (mesmas entradas → mesmo Score).
FR-5: O sistema sugere, para um Recurso disponível, o Paciente elegível de maior Prioridade Efetiva compatível, com regras de desempate por especificidade/ociosidade (Recursos) e price-time priority (Pacientes); nunca aloca automaticamente.
FR-6: Um Regulador pode consultar a fila atual ordenada por Prioridade Efetiva e a Sugestão de Matching para um Recurso específico.
FR-7: O sistema ajusta a Prioridade Efetiva por Urgência Acumulada (Aging) linear com teto (`score + min(k × tempo_espera, teto)`), teto = 20% da amplitude do Score, atingido em 12–24h simuladas.
FR-8: Toda decisão (Score calculado, Sugestão gerada, confirmação/recusa, Liberação) gera um registro append-only no Log Auditável com fatores, timestamp e IDs internos.
FR-9: Um Auditor pode consultar o histórico completo de decisões por Paciente/Recurso (por CPF mascarado ou ID interno), com justificativa legível.
FR-10: O sistema carrega dados sintéticos (unidades, leitos, especialistas) via seed reproduzível através de uma Camada Adaptadora que simula SISREG/DATASUS.
FR-11: Um usuário sintético pré-cadastrado autentica-se via `POST /login` (usuário/senha mockados) contra um serviço de autenticação dedicado e recebe um token assinado; a API exige esse token para qualquer endpoint não público/de login; sem distinção de papéis nesta fase.
FR-12: Um Regulador pode confirmar a Sugestão de Matching (cria Alocação) ou recusá-la com motivo obrigatório (Recurso permanece disponível, próxima sugestão é recalculada).
FR-13: Um Recurso alocado retorna automaticamente ao pool após tempo de atendimento simulado, tornando-se elegível para nova Sugestão de Matching.

### NonFunctional Requirements

NFR-1 (Continuidade sob falha parcial): Triagem continua aceitando e pontuando mesmo se Matching/Auditoria estiverem indisponíveis; propagação processada quando o serviço voltar (não perdida).
NFR-2 (Observabilidade mínima): logs estruturados e health-check por serviço.
NFR-3 (Reprodutibilidade): todo o sistema (serviços + seed) sobe com um único comando, sem passos manuais.
NFR-4 (Contratos versionados): comunicação entre serviços usa contratos versionados (REST/proto/evento), evitando quebras silenciosas.
NFR-5 (Testes automatizados): cobertura de linha ≥90% (JaCoCo) na camada de domínio por serviço + teste de mutação (PIT) na mesma camada + testes de integração dos contratos entre serviços/Camada Adaptadora + testes de aceitação BDD (Cucumber-JVM) cobrindo o fluxo ponta a ponta.
NFR-6 (Segredos fora do código): credenciais/segredos não versionados no repositório.
NFR-7 (Custo): privilegiar baixo custo e fácil desligamento (Free Tier, recursos pausáveis/destruíveis); evitar serviços gerenciados caros de operação contínua (ex.: NAT Gateway 24/7); manter padrão de scripts `deploy/pause/destroy`.
NFR-8 (Privacidade/LGPD by design): dados de Pacientes sintéticos; CPF e dados clínicos tratados por minimização e limitação de propagação; CPF não transita além da fronteira de ingestão; toda resposta de API usa ID interno como identificador primário, CPF mascarado quando exibido.

### Additional Requirements

- **Sem starter template** de framework citado na Arquitetura — a estrutura de pastas de cada serviço (`domain/application/infrastructure`, Clean Architecture) está definida no Structural Seed da spine e deve ser seguida desde a Epic 1 Story 1 de cada serviço.
- 3 serviços de runtime de domínio (`triagem-score-service`, `matching-alocacao-service`, `auditoria-service`) + `gateway-service` + `auth-service` (serviço de suporte transversal) + job `seed-adapter` (Lambda/Quarkus) — decomposição fixada em AD-1.
- `auth-service` (Spring Boot) tem schema próprio (`auth`), usuários sintéticos pré-cadastrados via migration (não via `seed-adapter`), expõe `POST /v1/auth/login` e emite JWT assinado (segredo compartilhado com o gateway, mesmo padrão do AD-7); `gateway-service` valida a assinatura/expiração do JWT em vez de comparar um bearer estático; claim de `role` só informativo, sem RBAC aplicado — AD-14.
- `seed-adapter` autentica-se com um usuário técnico pré-cadastrado em `auth-service` para obter seu próprio token antes de chamar o gateway (AD-1/AD-14).
- Clean Architecture (`domain/` sem framework → `application/command|query/` → `infrastructure/`) e CQRS lógico por serviço — AD-2.
- Propagação de eventos de domínio via Outbox + tópico SNS FIFO + fila SQS FIFO por consumidor + DLQ (`maxReceiveCount=5`) — AD-3.
- Fila SQS **standard** separada e dedicada para o delay da Liberação de Recurso (FIFO não suporta delay por mensagem) — AD-3/AD-6.
- Score ∈ [0,100], dono exclusivo `triagem-score-service`; réplica somente-leitura em `matching-alocacao-service` via upsert idempotente (last-write-wins por `occurredAt`, desempate por `eventId`); bootstrap REST síncrono em boot a frio de réplica vazia — AD-4.
- Fórmula de Aging fixada: `teto=20`, `k≈1,111 pontos/hora` (18h) — AD-4.
- `especificidadeRank` de Recurso fixado no seed: 1=leito comum, 2=leito UTI, 3=leito UTI especializado, 4=especialista; desempates determinísticos por `recursoId`/sequência de Triagem — AD-5.
- Ciclo de vida do Recurso: constraint única de banco `(recursoId, status=ativa)`; delay de Liberação via SQS standard (2–5 min por tipo, ≤15 min); consumidor idempotente por `alocacaoId`; `LiberarRecurso` não exposto via API — AD-6.
- CPF confinado a `triagem-score-service`; dois endpoints gRPC internos exclusivos (`ResolveCpfParaId`, `ObterCpfMascarado`) protegidos por segredo compartilhado em metadata gRPC além de isolamento de rede; máscara `123.***.***-09` — AD-7.
- `gateway-service` (Spring Cloud Gateway) é o único ponto de validação de token; health-check público via exceção estreita de security group — AD-8.
- Um único cluster PostgreSQL 18, schema e usuário próprios por serviço (`triagem_score`, `matching_alocacao`, `auditoria`, `auth`), `REVOKE` cross-schema, regra ArchUnit em CI, migrations versionadas por serviço, snapshot diário (retenção 1–3 dias) — AD-9.
- `auditoria-service` append-only, dedup por `eventId`; `SugestaoGerada` só registrado quando o Paciente sugerido muda (rastreamento "última sugestão" mantido por `matching-alocacao-service`, tabela `ultima_sugestao_registrada`) — AD-10.
- Faixas fisiológicas de Triagem fixadas: FC 40–200 bpm, PAS 60–260 mmHg, PAD 30–150 mmHg, SpO2 50–100%, FR 5–60 irpm, Temp 30–42°C, todos obrigatórios, PAS > PAD — AD-11.
- Topologia de rede: VPC subnet pública única (2 AZs, sem NAT Gateway), ECS Fargate `assignPublicIp=ENABLED`, isolamento por security group (gateway → serviços; health-check estreito; gRPC auditoria↔triagem liberado explicitamente) — AD-12.
- Runtime misto deliberado: Spring Boot 4.1.1/Spring Cloud 2025.1.2+ para os 5 serviços principais (incluindo `auth-service`); Quarkus só para `seed-adapter` (Lambda) — AD-13.
- Convenções: eventos em PascalCase passado; IDs = UUID v4; datas ISO-8601 UTC; envelope de evento `{eventId, eventType, occurredAt, version, correlationId, payload}`; schema companion JSON Schema versionado; mudanças de schema só aditivas (versão incompatível → DLQ); erros REST em RFC 7807; gRPC com status codes padrão; contratos versionados (`/v1/`, proto `vN`).
- `correlationId` gerado no gateway por requisição, propagado em HTTP/gRPC/evento, para rastreio ponta a ponta sem tracing distribuído completo.
- `seed-adapter` faz upsert de Recursos por `codigoRecurso` (idempotente a redeploy); entra pelo gateway como qualquer cliente (mesmo token mockado).
- Deferred/fora de escopo de arquitetura (não gerar stories para calibração fina além de deixá-los configuráveis): calibração fina de k/teto e duração de Liberação (já centralizados em `application.yml`), RDS gerenciado vs. Postgres em container (decisão de custo, cabe a uma story de infra), estratégia de migration (Flyway vs. Liquibase), HA full-produção, tracing distribuído completo (X-Ray).

### UX Design Requirements

Não aplicável — o MVP é backend-only (Swagger/Postman), sem frontend (PRD §5 Non-Goals). Nenhum documento de UX foi encontrado ou é esperado.

### FR Coverage Map

FR-1: Epic 2 - Registro de Triagem
FR-2: Epic 2 - Identificação mínima do Paciente
FR-3: Epic 2 - Cálculo do Score
FR-4: Epic 2 - Determinismo do Score
FR-5: Epic 3 - Sugestão de Matching
FR-6: Epic 3 - Consulta da fila e sugestões
FR-7: Epic 3 - Aging da Prioridade Efetiva
FR-8: Epic 4 - Registro de decisão (Log Auditável)
FR-9: Epic 4 - Consulta de auditoria
FR-10: Epic 5 - Carga de dados sintéticos
FR-11: Epic 1 - Autenticação via auth-service dedicado
FR-12: Epic 3 - Confirmação/recusa da sugestão
FR-13: Epic 3 - Liberação de Recurso

## Epic List

### Epic 1: Fundação da Plataforma — Autenticação Dedicada
Sobe o sistema completo (gateway + `auth-service` + serviços de domínio + Postgres + mensageria) com um único comando. Usuários sintéticos pré-cadastrados (Regulador, Profissional de Triagem, Auditor) autenticam-se via `POST /login` contra o `auth-service` dedicado e recebem um JWT validado pelo gateway em cada chamada.
**FRs covered:** FR-11
**Also addresses:** NFR-2, NFR-3, NFR-4, NFR-6, NFR-7; AD-1, AD-8, AD-9, AD-12, AD-13, AD-14

### Epic 2: Triagem Estruturada e Score de Prioridade Clínica
Profissional de Triagem registra dados clínicos e recebe, na mesma resposta, o Score já calculado e explicado por fatores contribuintes.
**FRs covered:** FR-1, FR-2, FR-3, FR-4
**Also addresses:** NFR-1, NFR-5, NFR-8; AD-2, AD-3, AD-4, AD-7, AD-11

### Epic 3: Matching, Alocação e Liberação de Recursos
Regulador consulta a fila priorizada, recebe sugestão justificada para um Recurso, confirma/recusa, e o Recurso volta automaticamente ao pool após o atendimento — fechando sugestão → confirmação → uso → liberação, com Aging garantindo que ninguém fique represado.
**FRs covered:** FR-5, FR-6, FR-7, FR-12, FR-13
**Also addresses:** NFR-1, NFR-5; AD-3, AD-4, AD-5, AD-6

### Epic 4: Log Auditável e Explicabilidade
Auditor consulta o histórico completo e legível de toda decisão (score, sugestão, confirmação, recusa, liberação) para qualquer Paciente/Recurso.
**FRs covered:** FR-8, FR-9
**Also addresses:** NFR-5, NFR-8; AD-3, AD-7, AD-10

### Epic 5: Camada Adaptadora — Carga de Dados Sintéticos
O `seed-adapter` carrega automaticamente, num único comando de deploy, um dataset sintético de unidades de saúde, leitos e especialistas via Triagem/Recurso — dando ao Regulador, ao Profissional de Triagem e ao Auditor um ambiente de demonstração pronto para uso, sem passos manuais. Depende de `auth-service` (Epic 1, para o seed se autenticar), `triagem-score-service` (Epic 2, API de Paciente/Triagem) e `matching-alocacao-service` (Epic 3, API de Recurso) já existirem — por isso vem por último, mesmo sendo uma FR "de fundação" no PRD.
**FRs covered:** FR-10
**Also addresses:** NFR-3, NFR-7; AD-1, AD-13

**Dependências:** Epic 1 é pré-requisito de todos. Epic 2 é independente após Epic 1. Epic 3 consome eventos de Score do Epic 2. Epic 4 consome eventos de Score (Epic 2) e de Matching/Alocação/Liberação (Epic 3). Epic 5 depende de Epic 1 (auth), Epic 2 (API de Triagem/Paciente) e Epic 3 (API de Recurso) — é o único epic sequenciado por uma dependência técnica dura, não por valor incremental; ainda assim não bloqueia nenhum dos anteriores (cada um funciona e é demonstrável sem o seed, só com dados inseridos manualmente via API).

## Epic 1: Fundação da Plataforma — Autenticação Dedicada

Sobe o sistema completo (gateway + `auth-service` + serviços de domínio + Postgres + mensageria) com um único comando. Usuários sintéticos pré-cadastrados (Regulador, Profissional de Triagem, Auditor) autenticam-se via `POST /login` contra o `auth-service` dedicado e recebem um JWT validado pelo gateway em cada chamada.

### Story 1.1: Subida do Ambiente com Health-Check Público

As a operador do sistema (Regulador/Auditor/equipe técnica),
I want subir toda a infraestrutura base (VPC, cluster ECS Fargate, cluster Postgres, mensageria) com um único comando,
So that eu possa confirmar que a plataforma está no ar antes de qualquer outra operação, sem passos manuais.

**Acceptance Criteria:**

**Given** o ambiente não está provisionado
**When** executo o script de deploy
**Then** a VPC (subnet pública única, 2 AZs, sem NAT Gateway), o cluster Postgres 18 e as tasks ECS Fargate sobem sem intervenção manual adicional (NFR-3, AD-12)

**Given** o ambiente está no ar
**When** faço `GET /actuator/health` em `gateway-service`
**Then** recebo `200` sem precisar de token (rota pública, AD-8/AD-12)

**Given** os security groups configurados
**When** uma requisição tenta alcançar a porta de aplicação de um serviço por trás do gateway diretamente (bypass)
**Then** a conexão é recusada — só o security group do gateway e a exceção de health-check têm acesso (AD-12)
**And** os scripts `pause`/`destroy` (herdados da Fase 4) escalam as tasks a 0 ou destroem o ambiente sem deixar recursos órfãos cobrando fora da janela de demo (NFR-7)

### Story 1.2: Autenticação de Usuário via auth-service

As a Regulador, Profissional de Triagem ou Auditor,
I want autenticar-me com usuário e senha mockados e usar o token recebido nas chamadas seguintes,
So that eu acesse os endpoints protegidos da API sem depender de um valor fixo compartilhado manualmente.

**Acceptance Criteria:**

**Given** um usuário sintético pré-cadastrado (via migration em `auth-service`)
**When** faço `POST /v1/auth/login` com usuário e senha corretos
**Then** recebo `200` com um JWT assinado (AD-14)

**Given** credenciais inválidas
**When** faço `POST /v1/auth/login`
**Then** recebo `401`

**Given** um JWT válido emitido por `auth-service`
**When** chamo qualquer endpoint protegido através do gateway
**Then** a requisição é encaminhada normalmente — gateway valida assinatura e expiração (AD-8)

**Given** nenhum token, um token expirado, ou um token com assinatura inválida
**When** chamo um endpoint protegido
**Then** recebo `401`
**And** o claim de `role` no JWT não bloqueia nem libera nenhum endpoint adicional — qualquer token válido acessa qualquer endpoint protegido (sem RBAC aplicado, Non-Goal do PRD)

## Epic 2: Triagem Estruturada e Score de Prioridade Clínica

Profissional de Triagem registra dados clínicos e recebe, na mesma resposta, o Score já calculado e explicado por fatores contribuintes.

### Story 2.1: Registro de Triagem com Score de Prioridade Calculado

As a Profissional de Triagem,
I want submeter os dados clínicos de um Paciente (CPF, sintomas, sinais vitais, gravidade percebida) e receber imediatamente o Score de prioridade calculado,
So that eu não precise calcular ou justificar a prioridade manualmente nem fazer uma segunda chamada.

**Acceptance Criteria:**

**Given** um CPF válido (formato/checksum) e todos os sinais vitais obrigatórios dentro das faixas fisiológicas plausíveis (FC 40–200 bpm, PAS 60–260, PAD 30–150 e PAS>PAD, SpO2 50–100%, FR 5–60 irpm, Temp 30–42°C — AD-11)
**When** faço `POST /v1/triagens`
**Then** recebo `201` com o identificador da Triagem e o Score já calculado, detalhado por fator contribuinte (FR-1, FR-3)

**Given** um CPF já usado em uma Triagem anterior
**When** registro uma nova Triagem para o mesmo CPF
**Then** o sistema reutiliza o mesmo Paciente/ID interno — nunca cria um segundo Paciente ou um segundo ID (FR-2, idempotência por CPF)

**Given** um CPF inexistente até então
**When** registro a primeira Triagem para ele
**Then** o sistema cria um registro mínimo de Paciente (CPF + dados demográficos mockados) implicitamente, sem cadastro separado (FR-2)

**Given** um sinal vital obrigatório ausente ou fora da faixa fisiológica plausível (ex.: PAS ≤ PAD)
**When** faço `POST /v1/triagens`
**Then** recebo `400` indicando o campo inválido, antes de qualquer cálculo de Score (FR-1, AD-11)

**Given** um CPF com formato ou checksum inválido
**When** faço `POST /v1/triagens`
**Then** recebo `400` antes de qualquer cálculo de Score (FR-1)

**Given** duas Triagens com entradas idênticas e a mesma versão do algoritmo
**When** o Score é calculado para ambas
**Then** o valor resultante é sempre o mesmo, e a versão do algoritmo fica registrada junto ao Score (FR-4)

**Given** `matching-alocacao-service` ou `auditoria-service` temporariamente indisponíveis
**When** registro uma Triagem
**Then** a resposta `201` com Score ainda é retornada imediatamente — a propagação do evento `ScoreCalculado` ocorre via outbox de forma assíncrona e não bloqueia nem atrasa a resposta (NFR-1, AD-3)
**And** nenhum registro gerado a partir dessa chamada expõe o CPF fora de `triagem-score-service` — a resposta referencia o Paciente pelo ID interno (AD-7)

### Story 2.2: Consulta de Triagem com Score e Fatores Contribuintes

As a Profissional de Triagem ou Regulador,
I want consultar uma Triagem já registrada e ver o Score com o detalhamento dos fatores que o compõem,
So that eu entenda por que aquele Paciente recebeu aquela prioridade, sem recalcular nada manualmente.

**Acceptance Criteria:**

**Given** uma Triagem já registrada
**When** faço `GET /v1/triagens/{id}`
**Then** recebo o Score final e a lista de fatores contribuintes que o compõem, não apenas o número final (FR-3)

**Given** um ID de Triagem inexistente
**When** faço `GET /v1/triagens/{id}`
**Then** recebo `404`
**And** a consulta nunca expõe o CPF em texto claro — referencia o Paciente pelo ID interno (AD-7)

## Epic 3: Matching, Alocação e Liberação de Recursos

Regulador consulta a fila priorizada, recebe sugestão justificada para um Recurso, confirma/recusa, e o Recurso volta automaticamente ao pool após o atendimento — fechando sugestão → confirmação → uso → liberação, com Aging garantindo que ninguém fique represado.

### Story 3.1: Consulta da Fila Priorizada com Urgência Acumulada

As a Regulador,
I want consultar a fila atual de Pacientes ordenada pela Prioridade Efetiva (Score + Urgência Acumulada),
So that eu saiba, a qualquer momento, quem deveria ser atendido a seguir, sem represamento de casos moderados.

**Acceptance Criteria:**

**Given** Pacientes com Scores distintos já triados (réplica consumida de `triagem-score-service`)
**When** faço `GET /v1/fila`
**Then** recebo a lista ordenada por Prioridade Efetiva decrescente (FR-6)

**Given** dois Pacientes com Score inicial idêntico e tempos de espera diferentes
**When** consulto a fila
**Then** o que espera há mais tempo tem Prioridade Efetiva igual ou maior, nunca menor (FR-7)

**Given** um Paciente que atinge 18h de espera simulada
**When** consulto a fila
**Then** a Prioridade Efetiva dele não ultrapassa Score + 20% da amplitude do Score — o teto foi atingido, não superado (FR-7, AD-4: teto=20, k≈1,111/h)

**Given** uma nova Triagem inserida ou um Recurso liberado
**When** consulto a fila em seguida
**Then** o resultado já reflete a mudança sem reprocessamento manual (FR-6, SM-1)

**Given** a réplica local de Score vazia num boot a frio
**When** o serviço recebe a primeira consulta
**Then** busca os Scores atuais via bootstrap REST síncrono em `triagem-score-service` antes de responder, em vez de retornar uma fila incompleta silenciosamente (AD-4)
**And** a Prioridade Efetiva nunca é negativa nem menor que o Score por defasagem de relógio entre serviços — `horas_espera` é sempre ≥0 (AD-4)

### Story 3.2: Sugestão de Matching para um Recurso com Desempates

As a Regulador,
I want consultar a sugestão de matching para um Recurso disponível específico,
So that eu saiba qual Paciente oferecer, com justificativa objetiva.

**Acceptance Criteria:**

**Given** um Recurso disponível e a fila com prioridades distintas
**When** faço `GET /v1/recursos/{id}/sugestao`
**Then** recebo o Paciente elegível de maior Prioridade Efetiva compatível, com justificativa (Score, tempo de espera, critério de desempate) (FR-5)

**Given** dois Pacientes com Prioridade Efetiva idêntica para o mesmo Recurso
**When** consulto a sugestão
**Then** vence o de Triagem mais antiga; em empate residual de timestamp, desempata pelo número de sequência da Triagem (FR-5, AD-5)

**Given** um Paciente elegível simultaneamente para um Recurso genérico e um mais específico, ambos livres
**When** consulto a sugestão
**Then** o sistema nunca sugere o específico se o genérico resolve o caso — prefere o menor `especificidadeRank` suficiente (FR-5, AD-5)

**Given** dois Recursos de mesmo rank elegíveis para o mesmo Paciente
**When** consulto a sugestão
**Then** prefere o ocioso há mais tempo; em empate residual, desempate final determinístico por `recursoId` (AD-5)

**Given** nenhum Paciente elegível para aquele Recurso
**When** faço `GET /v1/recursos/{id}/sugestao`
**Then** recebo uma resposta indicando ausência de sugestão, sem erro (FR-6)
**And** a sugestão é recalculada a cada consulta e não reserva o Paciente — o mesmo Paciente pode aparecer sugerido para mais de um Recurso simultaneamente até uma confirmação consumi-lo (FR-5)

### Story 3.3: Confirmação ou Recusa da Sugestão de Matching

As a Regulador,
I want confirmar a sugestão como está, ou recusá-la informando um motivo,
So that eu decida com um clique, sem selecionar manualmente um Paciente fora da ordem objetiva do sistema.

**Acceptance Criteria:**

**Given** uma sugestão vigente para um Recurso
**When** faço `POST /v1/recursos/{id}/alocacoes` confirmando
**Then** o Recurso é removido do pool, uma Alocação é criada e um registro é gerado para o Log Auditável (FR-12)

**Given** duas confirmações concorrentes para o mesmo Recurso
**When** a segunda chega
**Then** é rejeitada com `409` pela constraint única de banco `(recursoId, status=ativa)` (AD-6)

**Given** uma tentativa de confirmar uma Alocação para um Paciente já alocado a outro Recurso
**When** a confirmação chega
**Then** é rejeitada com `409` e o sistema recalcula automaticamente a próxima sugestão elegível para aquele Recurso (FR-5, FR-12)

**Given** uma recusa sem motivo informado
**When** faço `POST .../recusa`
**Then** recebo `400` (FR-12)

**Given** uma recusa com motivo informado
**When** é processada
**Then** o Recurso permanece disponível, o sistema sugere o próximo Paciente elegível, o par (recursoId, pacienteId) recusado nunca é resugerido para aquele Recurso, mas o Paciente segue elegível para qualquer outro Recurso, com a mesma Prioridade Efetiva — sem penalização (FR-12, AD-5)
**And** tanto a confirmação quanto a recusa publicam um evento (`AlocacaoConfirmada` / `SugestaoRecusada`) via outbox na mesma transação do comando, alimentando o Log Auditável (FR-8, AD-3)

### Story 3.4: Liberação Automática de Recurso

As a Regulador (beneficiário indireto — processo automático),
I want que um Recurso alocado retorne ao pool após o tempo de atendimento simulado,
So that ele fique elegível para nova sugestão sem liberação manual.

**Acceptance Criteria:**

**Given** uma Alocação confirmada
**When** ela é criada
**Then** uma mensagem é agendada numa fila SQS standard dedicada com delay = duração do atendimento simulado (2–5 min por tipo, ≤15 min), carregando o `correlationId` da confirmação original (AD-6)

**Given** o delay expira
**When** o consumidor processa a mensagem
**Then** o Recurso volta ao pool com o mesmo `especificidadeRank`, um evento `RecursoLiberado` é publicado via outbox e um registro é gerado no Log Auditável, fechando o ciclo (FR-13)

**Given** uma redelivery da mesma mensagem de delay
**When** o consumidor processa novamente
**Then** o Recurso não é liberado duas vezes nem um `RecursoLiberado` duplicado é emitido — idempotente por `alocacaoId` (AD-6)
**And** não existe endpoint de liberação manual — `LiberarRecurso` só é acionado internamente pelo consumidor da fila de delay (FR-13, Non-Goal do PRD)

## Epic 4: Log Auditável e Explicabilidade

Auditor consulta o histórico completo e legível de toda decisão (score, sugestão, confirmação, recusa, liberação) para qualquer Paciente/Recurso.

### Story 4.1: Registro Automático de Decisões no Log Auditável

As a Auditor (beneficiário indireto — o registro é automático),
I want que toda decisão do sistema (Score calculado, Sugestão gerada, confirmação, recusa, Liberação) seja gravada automaticamente no Log Auditável,
So that eu tenha, mais tarde, uma trilha completa e confiável para investigar qualquer reclamação.

**Acceptance Criteria:**

**Given** um evento `ScoreCalculado`, `SugestaoGerada`, `AlocacaoConfirmada`, `SugestaoRecusada` ou `RecursoLiberado` publicado pelos serviços produtores
**When** `auditoria-service` o consome
**Then** grava um registro append-only com os fatores que levaram à decisão (incluindo o motivo, no caso de recusa), timestamp e IDs internos envolvidos (FR-8)

**Given** uma reentrega do mesmo evento pelo SQS (mesmo `eventId`)
**When** `auditoria-service` processa novamente
**Then** nenhum registro duplicado é criado — dedup por `eventId` (FR-8, AD-10)

**Given** que FR-6 recalcula a sugestão a cada consulta (`GET`)
**When** o Paciente sugerido para um Recurso não muda em relação ao último registro
**Then** nenhum novo `SugestaoGerada` é publicado nem registrado — só uma mudança real gera um novo registro (AD-10)
**And** nenhum registro de auditoria é alterado retroativamente — apenas novos registros são adicionados (FR-8, append-only)

### Story 4.2: Consulta de Auditoria por Paciente ou Recurso

As a Auditor,
I want consultar o histórico completo de decisões para um Paciente ou Recurso específico, com justificativa legível,
So that eu possa responder, com dados, por que um paciente foi atendido antes de outro.

**Acceptance Criteria:**

**Given** decisões já registradas para um Paciente
**When** faço `GET /v1/auditoria/pacientes/{id}` (ou por CPF)
**Then** recebo o histórico completo com a justificativa legível de cada decisão (FR-9)

**Given** uma consulta por CPF
**When** o sistema resolve o CPF para o ID interno via gRPC (`ResolveCpfParaId`)
**Then** a resposta identifica o Paciente pelo ID interno; se o CPF for exibido, aparece mascarado (`123.***.***-09`), nunca em texto claro (FR-9, AD-7)

**Given** dois Pacientes distintos
**When** comparo suas consultas de auditoria
**Then** consigo identificar, pelos fatores registrados, por que um foi atendido antes do outro (FR-9, UJ-3)

**Given** decisões já registradas para um Recurso
**When** faço `GET /v1/auditoria/recursos/{id}`
**Then** recebo o histórico completo (sugestões, confirmação/recusa, liberação) daquele Recurso (FR-9)
**And** 100% das decisões do dataset de demonstração são consultáveis e explicáveis por este endpoint (FR-9, SM-2)

## Epic 5: Camada Adaptadora — Carga de Dados Sintéticos

O `seed-adapter` carrega automaticamente, num único comando de deploy, um dataset sintético de unidades de saúde, leitos e especialistas — dando ao Regulador, ao Profissional de Triagem e ao Auditor um ambiente de demonstração pronto para uso, sem passos manuais.

### Story 5.1: Carga de Dados Sintéticos via Camada Adaptadora

As a operador do sistema (Regulador/Profissional de Triagem/Auditor, beneficiários indiretos),
I want que o job `seed-adapter` carregue automaticamente um dataset sintético completo no primeiro deploy,
So that eu tenha um ambiente de demonstração pronto para uso, sem inserir dados manualmente.

**Acceptance Criteria:**

**Given** o ambiente recém-provisionado (Epic 1) com `triagem-score-service` e `matching-alocacao-service` já disponíveis (Epics 2 e 3)
**When** o script de deploy dispara o job `seed-adapter`
**Then** ele se autentica em `auth-service` com um usuário técnico pré-cadastrado e usa o token obtido para chamar o gateway (AD-1, AD-14) — nenhum passo manual adicional é necessário (NFR-3)

**Given** o job `seed-adapter` chamando as APIs de `matching-alocacao-service`
**When** ele insere Recursos
**Then** usa upsert por `codigoRecurso` — reexecutar o job após um deploy falho não duplica o catálogo (FR-10, AD-1)

**Given** o job concluído
**When** consulto o sistema
**Then** encontro pelo menos 12–15 Pacientes sintéticos cobrindo ao menos 3 níveis de gravidade e 6–8 Recursos (leitos/especialistas) em pelo menos 2 tipos, com escassez deliberada (menos Recursos do que Pacientes elegíveis simultaneamente) (FR-10)

**Given** o dataset carregado
**When** consulto a fila
**Then** encontro pelo menos um par de Pacientes com Score empatado (demonstra desempate por Triagem mais antiga) e pelo menos um Paciente elegível simultaneamente para um Recurso genérico e um específico (demonstra desempate best-fit) (FR-10)

**Given** os timestamps de Triagem no seed serem retroativos
**When** consulto a fila logo após o seed
**Then** a Urgência Acumulada já aparece refletida, sem exigir espera em tempo real (FR-10, FR-7)

**Given** pelo menos um Recurso já alocado no momento do seed com tempo de atendimento simulado próximo do fim
**When** aguardo alguns minutos
**Then** observo a Liberação e a sugestão seguinte ocorrerem sem intervenção manual, dentro da janela de uma gravação de demo (FR-10, FR-13)
**And** a interface da Camada Adaptadora é desenhada de forma que uma integração real futura com SISREG/DATASUS possa substituí-la sem alterar a lógica de domínio de Score/Matching/Log (FR-10)
