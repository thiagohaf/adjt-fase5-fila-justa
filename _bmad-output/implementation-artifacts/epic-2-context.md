# Epic 2 Context: Triagem Estruturada e Score de Prioridade Clínica

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Um Profissional de Triagem registra os dados clínicos estruturados de um Paciente (CPF, sintomas, sinais vitais, gravidade percebida) via API e recebe, na mesma resposta, o Score de Prioridade Clínica já calculado e detalhado por fator contribuinte — sem cálculo manual nem segunda chamada. O CPF é resolvido para um ID de Paciente interno na fronteira de ingestão e nunca propaga além desse ponto, mantendo o restante do sistema (Matching, Log Auditável) livre de dado pessoal identificador. Este epic é o ponto de entrada de todo o fluxo de priorização do FilaJusta: sem ele, não há dado para a fila (Epic 3) nem decisão para auditar (Epic 4).

## Stories

- Story 2.1: Registro de Triagem com Score de Prioridade Calculado
- Story 2.2: Consulta de Triagem com Score e Fatores Contribuintes

## Requirements & Constraints

- CPF é validado por formato/checksum antes de qualquer cálculo; inválido retorna `400`. Duas triagens para o mesmo CPF sempre reutilizam o mesmo Paciente/ID interno (idempotência) — nunca criam um segundo registro. Primeiro CPF visto cria um Paciente mínimo implicitamente (CPF + dados demográficos mockados), sem cadastro separado.
- Sinais vitais obrigatórios (todos) devem estar dentro de faixas fisiológicas plausíveis, checadas antes de qualquer cálculo de Score: FC 40–200 bpm, PAS 60–260 mmHg, PAD 30–150 mmHg (e PAS > PAD), SpO2 50–100%, FR 5–60 irpm, Temp 30–42°C. Ausência ou violação retorna `400` indicando o campo inválido.
- O Score é calculado de forma síncrona e retornado no mesmo `201` da Triagem (nunca exige polling); é persistido junto com o detalhamento dos fatores contribuintes (não só o valor final). Faixa do Score: `[0, 100]`.
- O algoritmo de Score é determinístico (mesmos inputs + mesma versão → mesmo Score) e a versão do algoritmo é registrada junto ao Score para rastreabilidade.
- A propagação do Score para Matching/Auditoria nunca bloqueia nem atrasa a resposta da Triagem — continua funcionando mesmo com esses serviços temporariamente indisponíveis (a mensagem espera na fila até serem consumidas).
- CPF nunca aparece em texto claro fora deste serviço; nenhum registro ou evento gerado por uma Triagem carrega o CPF — apenas o ID interno do Paciente.
- Cobertura de linha ≥90% (JaCoCo) na camada de domínio, complementada por teste de mutação (PIT), testes de integração de contrato e cenários de aceitação BDD cobrindo este trecho do fluxo ponta a ponta.

## Technical Decisions

- Serviço dono: `triagem-score-service` (Clean Architecture — `domain/` sem framework → `application/command|query/` → `infrastructure/`). CQRS lógico: `RegistrarTriagem`/`ResolverOuCriarPaciente` em `command`; `ConsultarTriagem`/`ListarScoresAtuais` (bootstrap de réplica para Epic 3) em `query`. Mesmo banco, sem read-model separado.
- Persistência: schema próprio `triagem_score` no cluster PostgreSQL 18 compartilhado, usuário de banco isolado, sem acesso cross-schema.
- Propagação assíncrona: evento `ScoreCalculado` gravado em tabela outbox na mesma transação local do comando; `eventId` (UUID v4) gerado nesse momento e nunca regenerado em retry. Relay publica em tópico SNS FIFO; `MessageGroupId = pacienteId`; DLQ com `maxReceiveCount = 5`. Envelope de evento: `{eventId, eventType, occurredAt, version, correlationId, payload}`; schema companion JSON Schema versionado; mudanças só aditivas.
- Fronteira de CPF: validado em `triagem-score-service` antes de qualquer resolução/cálculo. Único acesso externo a dado derivado de CPF é via dois endpoints gRPC internos protegidos por segredo compartilhado + isolamento de rede: `ResolveCpfParaId(cpf) -> pacienteId` e `ObterCpfMascarado(pacienteId) -> cpfMascarado`. Máscara: `123.***.***-09`.
- Convenções: eventos em PascalCase passado (`ScoreCalculado`); IDs internos = UUID v4; datas ISO-8601 UTC; erros REST em RFC 7807; contrato versionado (`/v1/`); `correlationId` gerado no gateway e propagado no header HTTP e no envelope do evento.
- Faixas fisiológicas e demais valores calibráveis ficam centralizados em `application.yml` do serviço (`filajusta.triagem.limites.*`), não espalhados no código.
- Autenticação: endpoints protegidos exigem JWT válido emitido por `auth-service` e validado no `gateway-service` (Epic 1) — este epic não implementa autenticação, apenas depende dela.

## Cross-Story Dependencies

- Story 2.2 (consulta) depende de Story 2.1 (registro) já existir — não há triagem para consultar sem o fluxo de registro implementado antes.
- Epic 3 (Matching/Alocação) consome o evento `ScoreCalculado` publicado por este epic para manter sua réplica local de Score — depende deste epic estar publicando o evento corretamente, mas não bloqueia a entrega deste epic.
- Epic 4 (Auditoria) também consome `ScoreCalculado` para registrar a decisão no Log Auditável.
- Depende de Epic 1 (gateway + `auth-service`) para autenticação — os endpoints deste epic são protegidos e exigem token válido.
