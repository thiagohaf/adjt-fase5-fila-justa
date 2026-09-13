# Deferred Work

<!-- Append-only. Cada entrada nasce de um split de escopo feito durante bmad-build (step-02, token-count check). Não editar entradas existentes; apenas acrescentar novas. -->

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Esqueleto Clean Architecture (domain/application/infrastructure) + Dockerfile + registro de task/service ECS para `triagem-score-service`, `matching-alocacao-service` e `auditoria-service`.
  evidence: A spec da Story 1.1 excedeu o alvo de 900–1600 tokens ao cobrir os 5 serviços; o escopo foi reduzido a `gateway-service` + `auth-service` (suficientes para testar os ACs de deploy, health-check público e bypass negado). Os outros 3 serviços seguem o mesmo padrão de esqueleto e podem ser criados como preparação da primeira story de cada epic que os toca (Epic 2 → `triagem-score-service`, Epic 3 → `matching-alocacao-service`, Epic 4 → `auditoria-service`), ou como uma story de infra rápida antes disso se o time preferir antecipar.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Workflow de GitHub Actions (`workflow_dispatch`, credenciais via OIDC) para automatizar `deploy`/`pause`/`destroy` do ambiente, com opção de destroy agendado como rede de segurança contra recurso órfão esquecido.
  evidence: Levantado pelo usuário durante a verificação end-to-end da Story 1.1 (rodada local e manual). Fora do escopo da spec 1.1 (que só previu scripts locais); vale como story/chore de tooling própria mais adiante, se o time decidir que a rotina manual local não é suficiente.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: README na raiz do repositório documentando pré-requisitos (Docker, AWS CLI, `jq`, CDK CLI/Node, Java 25) e como rodar `deploy.sh`/`pause.sh`/`destroy.sh`/`scripts/smoke-test.sh`.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 1.1. Real e útil para quem pegar o projeto depois, mas não bloqueia nenhum AC desta story; o `README.md` atual só tem o nome do projeto.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Preflight checks em `deploy.sh`/`pause.sh`/`destroy.sh`/`scripts/smoke-test.sh` validando `aws`, `cdk`, `jq` e o daemon do Docker antes de rodar, com mensagem clara em vez do erro genérico do comando interno.
  evidence: Achado pelo review adversarial (blind-hunter). Real (confirmado nesta sessão: o primeiro deploy real precisou que o Docker fosse iniciado manualmente), mas é robustez incremental, não um defeito que quebra algum AC desta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Implementar `correlationId` (UUID gerado no gateway por requisição, propagado em header) conforme convenção declarada em `epic-1-context.md`.
  evidence: Achado pelo review adversarial (blind-hunter): a convenção é citada como válida "desde este epic" mas não implementada nem registrada como adiada. Legítimo, porém não há nenhuma rota de domínio passando pelo gateway ainda nesta story (`routes: []` — só health-check) — só faz sentido a partir da Story 1.2, quando o roteamento de fato existir.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Implementar respostas de erro REST no formato RFC 7807 (`ProblemDetail`/exception handler) em `gateway-service`/`auth-service`, conforme convenção declarada em `epic-1-context.md`.
  evidence: Achado pelo review adversarial (blind-hunter). Legítimo, mas nenhum endpoint desta story produz erro de aplicação ainda (só `/actuator/health`); primeiro caso real de erro é o `401` de `POST /v1/auth/login` na Story 1.2.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Trocar o pattern manual de log "JSON" do Logback (`application.yml` de `gateway-service`/`auth-service`) por um encoder JSON de verdade (ex.: `logstash-logback-encoder`).
  evidence: Achado pelo review adversarial (blind-hunter): o pattern atual monta a string manualmente (`%m` sem escaping), então uma mensagem de log com aspas/barra invertida/quebra de linha produz JSON inválido. Baixo risco nesta story (serviços-esqueleto, log mínimo), mas vale corrigir antes de haver volume de log real.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-subida-ambiente-health-check-publico.md`
  summary: Workflow de CI (GitHub Actions ou similar) rodando `mvn test` e `cdk synth` em push/PR.
  evidence: Achado pelo review adversarial (blind-hunter). Distinto do item de CI de deploy/pause/destroy já registrado acima (esse é sobre build/test, não sobre provisionar AWS); hoje toda verificação da Story 1.1 é manual/local, sem pipeline automatizado.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: Validação de JWT no `gateway-service` (`GlobalFilter`, AD-8) para qualquer endpoint protegido, propagação de `correlationId` por header (convenção do epic-1-context, ainda não implementada) e formatação RFC 7807 do lado do gateway — cobre os ACs "chamo endpoint protegido com JWT válido/ausente/expirado/assinatura inválida" da Story 1.2.
  evidence: A spec completa da Story 1.2 excedeu o alvo de 900–1600 tokens (2969) cobrindo emissão E validação de JWT mais correlationId/RFC7807. Escopo reduzido ao lado de emissão (`auth-service`: login, Postgres/Flyway, JWT assinado, alcançável via rota pública simples no gateway) — suficiente para os ACs de login (AC-1/AC-2). A validação de JWT no gateway (AC-3/AC-4) é uma entrega independentemente revisável/testável em seguida — mesmo Epic 1, story própria ou continuação direta da 1.2.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: Role dedicado de banco (`auth_service`) com `REVOKE` explícito de grants cross-schema para o schema `auth` (AD-9 completo via script de init do container Postgres).
  evidence: `auth-service` é o primeiro serviço a conectar no Postgres; usa o secret admin (`PostgresSecret`) da Story 1.1 e o schema `auth` isola os dados, mas sem role/REVOKE dedicado ainda, porque nenhum outro serviço compartilha o cluster nesta story. Isolamento por role só importa de fato quando um 2º serviço (Epic 2) conectar — implementar antes disso seria projetar para um requisito hipotético.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: Comparação em tempo constante no login (`AutenticarUsuario`) — hoje um username inexistente retorna 401 mais rápido (sem custo de BCrypt) que uma senha errada (com custo de BCrypt), permitindo distinguir os dois casos por timing mesmo com corpo de resposta idêntico.
  evidence: Achado pelo review adversarial (blind-hunter). Real, mas autenticação de nível produção (incluindo mitigação de side-channel) é Non-Goal explícito do PRD para este MVP; usuários sintéticos pré-cadastrados, sem dado real em risco.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: Documentar (README ou profile `application-local.yml`) como rodar `auth-service` localmente fora da AWS — quais valores de `FILAJUSTA_JWT_SECRET` e datasource usar, já que `application.yml` aponta para o DNS interno do Service Connect (`postgres`).
  evidence: Achado pelo review adversarial (blind-hunter). Real e útil para quem pegar o projeto depois (mesma categoria do README já registrado na Story 1.1), mas não bloqueia nenhum AC desta story — os testes usam Testcontainers, não precisam do profile local.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: Mascarar/excluir o campo `password` de qualquer logging futuro de request body em `POST /v1/auth/login` (gateway ou auth-service).
  evidence: Achado pelo review adversarial (blind-hunter). Nenhum logging de corpo de requisição existe hoje (só o pattern manual de log da Story 1.1), então não há exposição real ainda — mas se logging de request/tracing for ligado depois sem essa salvaguarda, a senha em texto claro vaza para os logs.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: Rate limiting / proteção contra força bruta em `POST /v1/auth/login`.
  evidence: Achado pelo review adversarial (blind-hunter). Real, mas autenticação/autorização completas de nível produção são Non-Goal explícito do PRD (FR-11) para este MVP acadêmico — login mockado contra usuários sintéticos é suficiente nesta fase.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: Adicionar claims `iss`/`aud` ao JWT emitido por `auth-service` (hoje só `sub`/`role`/`iat`/`exp`), para a validação futura no gateway (deferida) já ter um issuer/audience esperado para comparar, sem precisar renegociar o formato do token depois.
  evidence: Achado pelo review adversarial (blind-hunter). Levantado como contexto útil para a entrega deferida de validação de JWT no gateway (já registrada acima) — não bloqueia AC-1/AC-2 desta story, mas evita retrabalho no formato do token quando a validação for implementada.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-autenticacao-usuario-auth-service.md`
  summary: `cloudMapNamespace != null` em `FilaJustaStack.java` (guarda contra `getDefaultCloudMapNamespace()` retornar null) nunca foi explicado em comentário nem testado — se esse branch for de fato tomado algum dia, a mesma corrida de propagação do Cloud Map documentada para `postgresService`/`authService` fica sem proteção, silenciosamente.
  evidence: Achado pelo review adversarial (blind-hunter/edge-case-hunter). Pré-existente da Story 1.1 (o mesmo padrão já protegia `postgresService`; esta story só replicou para `authService`) — surgiu incidentalmente nesta review, não foi introduzido por esta mudança.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-validacao-jwt-gateway.md`
  summary: Filtro dedicado de `correlationId` (UUID gerado no `gateway-service` quando ausente, propagado em header HTTP na requisição encaminhada e na resposta) conforme convenção do `epic-1-context.md`.
  evidence: A spec de validação de JWT no gateway (AC-3/AC-4 da Story 1.2) excedeu 1600 tokens (~1774) cobrindo JWT + correlationId + RFC7807 juntos. Escopo reduzido à validação de JWT + RFC7807 (o que os ACs da Story 1.2 de fato exigem — nenhum deles menciona correlationId). Já havia sido deferido uma vez na Story 1.1 por não existir roteamento ainda; agora existe, mas vale como entrega própria, pequena e testável em isolado (um `GlobalFilter` de maior precedência, independente do filtro de validação JWT). Nota (review adversarial desta story): `JwtAuthenticationFilter.getOrder()` foi ajustado para deixar espaço acima dele — o filtro de `correlationId` precisa mesmo rodar com precedência maior (antes) para que toda resposta, inclusive `401`, carregue o header.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-validacao-jwt-gateway.md`
  summary: `JwtAuthenticationFilter` não exime requisições `OPTIONS` (CORS preflight) da exigência de JWT — um preflight de navegador para qualquer rota protegida futura seria rejeitado com `401` antes mesmo da requisição real ser tentada.
  evidence: Achado pelo review adversarial (blind-hunter/edge-case-hunter). Sem impacto hoje: nenhuma rota protegida real existe ainda (Epic 2+) e o PRD declara o MVP backend-only (Swagger/Postman, sem frontend/CORS). Vale revisitar se/quando um cliente browser for introduzido.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-validacao-jwt-gateway.md`
  summary: `JwtAuthenticationFilter` não emite log/métrica quando rejeita uma requisição (`401` por token ausente/expirado/assinatura inválida) — sem trilha de auditoria para detectar tentativas de força bruta/probing no gateway.
  evidence: Achado pelo review adversarial (blind-hunter). Real, mas mesma categoria de hardening de observabilidade já deferida para o login do `auth-service` (rate limiting, mascaramento de senha em log futuro) — consistente adiar aqui também.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-validacao-jwt-gateway.md`
  summary: Nenhum teste exercita o segredo `JwtSecret` compartilhado ponta a ponta entre `auth-service` (emite) e `gateway-service` (valida) — cada suíte usa um segredo local hardcoded independente; um bug de wiring no CDK (ex.: os dois serviços recebendo segredos diferentes) não seria pego por nenhuma delas.
  evidence: Achado pelo review adversarial (verification-gap). Bloqueado por não existir ainda nenhuma rota de domínio protegida real para exercitar via `smoke-test.sh` (Epic 2+); revisitar quando a primeira rota protegida existir.

- source_spec: `_bmad-output/implementation-artifacts/spec-correlationid-gateway.md`
  summary: Adotar o `correlationId` (header `X-Correlation-Id` gerado/propagado pelo `CorrelationIdFilter`) no logging estruturado do `gateway-service`/`auth-service`, para de fato correlacionar logs de uma mesma requisição entre serviços — o problema original que motivou o filtro.
  evidence: Achado pelo review adversarial (blind-hunter). Explicitamente fora do escopo da spec `correlationId-gateway` ("Never: logging estruturado usando o correlationId"), mas sem isso o problema declarado no Intent da spec (correlacionar logs entre serviços) segue sem solução completa — vale como story/chore própria quando o volume de log justificar (mesma categoria já registrada para o encoder JSON de log real).

- source_spec: `_bmad-output/implementation-artifacts/spec-correlationid-gateway.md`
  summary: `CorrelationIdFilter` seta o header `X-Correlation-Id` na resposta ANTES de `chain.filter(...)`; se um serviço downstream futuro também devolver seu próprio header `X-Correlation-Id` na resposta, o `NettyRoutingFilter` do Spring Cloud Gateway pode copiar esse header do downstream por cima/ao lado do já setado (comportamento aditivo de `HttpHeaders`), gerando valores duplicados/conflitantes na resposta ao cliente.
  evidence: Achado pelo review adversarial (edge-case-hunter). Não testável nem reproduzível hoje — nenhum serviço de domínio existe ainda (Epic 2+) e o stub de teste usado (`CorrelationIdFilterTest`) só ecoa o header recebido no corpo JSON, nunca como header de resposta próprio. Revisitar quando o primeiro serviço downstream real responder com seus próprios headers.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: Deploy do `triagem-score-service` — rota `POST /v1/triagens` no `gateway-service` (`application.yml`, ao lado da rota de login) e `buildTriagemScoreService(...)` em `infra-cdk/.../FilaJustaStack.java` (mirror de `buildAuthService(...)`, Service Connect + secrets do DB).
  evidence: Spec original (domínio + persistência + endpoint + deploy) excedeu 1600 tokens (~2669, cl100k). Escopo reduzido ao que os ACs da Story 2.1 de fato exigem e o `mvn test` consegue verificar sozinho (sem precisar de ambiente AWS no ar); deploy fica como chore próprio pós-merge — é também a rota que, uma vez existindo, torna testável ao vivo o action item 2 da retrospectiva do Epic 1 (AC-3/AC-4 do gateway).

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: `triagem-score-service` não propaga/loga `X-Correlation-Id` (nenhum filtro, nenhum campo no `logging.pattern.console`) — logs desta chamada não são correlacionáveis com o resto da requisição.
  evidence: Achado pelo review adversarial (blind-hunter). Mesma categoria já deferida para `gateway-service`/`auth-service` no Epic 1 ("Adotar o correlationId no logging estruturado") — extensão natural do mesmo gap pré-existente, não introduzida por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: `TriagemExceptionHandler.handleErroInesperado` (fallback `500`) não loga a exceção capturada — um erro real de produção não deixa rastro para depuração.
  evidence: Achado pelo review adversarial (blind-hunter). Mesmo padrão já existe em `AuthExceptionHandler.handleErroInesperado` (Epic 1) — gap pré-existente replicado ao espelhar o padrão, não introduzido por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: Lista `sintomas` do `POST /v1/triagens` não tem limite de tamanho (nº de itens) nem de comprimento por item antes de ir para a coluna JSONB.
  evidence: Achado pelo review adversarial (blind-hunter). Nenhuma NFR ou spec define um limite — fica como hardening a revisitar se o volume/abuso justificar, mesma categoria de itens de hardening sem prazo já registrados para `auth-service`/`gateway-service`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: `triagem_score.pacientes.cpf` fica em texto claro no banco, sem hashing/criptografia em repouso nem colunas de auditoria (created/updated).
  evidence: Achado pelo review adversarial (blind-hunter). Consistente com o Non-Goal explícito do PRD §8 ("conformidade legal plena... fica fora do escopo deste MVP") — vale registrar para quando/se dado real de paciente for considerado.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: Nenhum contrato OpenAPI/springdoc é exposto para `POST /v1/triagens`.
  evidence: Achado pelo review adversarial (blind-hunter). Nenhuma spec/NFR exige isso; `auth-service` também não expõe (mesmo padrão pré-existente do Epic 1).

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: `TriagemExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` intercepta exceções do próprio framework (ex.: `HttpRequestMethodNotSupportedException` para método HTTP não suportado), sobrescrevendo o `405`/`415` default do Spring com `500` genérico.
  evidence: Achado pelo review adversarial (edge-case-hunter). Mesmo padrão já existe em `AuthExceptionHandler` (Epic 1, catch-all idêntico) — gap pré-existente replicado ao espelhar o padrão, não introduzido por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-triagem-score.md`
  summary: Nenhum módulo do repositório (incluindo `triagem-score-service`) configura JaCoCo (cobertura ≥90% na camada de domínio) nem PIT (teste de mutação), apesar de `epic-2-context.md` listar isso como requisito do épico.
  evidence: Achado na classificação desta revisão (não por um dos 3 layers). Gap pré-existente em todo o repositório, não introduzido por esta story — `gateway-service`/`auth-service` (Epic 1, já `done`) também não têm. Vale como chore de tooling próprio antes do fechamento do Epic 2, não bloqueante para esta story individual.

## Deferred from: code review of spec-2-2-consulta-triagem-score-fatores-contribuintes (2026-09-08)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-consulta-triagem-score-fatores-contribuintes.md`
  summary: `ConsultaTriagemRepositorioAdapter.paraDominio` reconstrói `SinaisVitais`/`GravidadePercebida`/`Score` a partir do dado persistido usando os construtores validadores do domínio (mesmos usados no `POST`). Se os limites de AD-11 (`LimitesSinaisVitais`) mudarem depois do registro, o enum `GravidadePercebida` evoluir (renomear/remover valor), ou um `score_valor` persistido sair de 0..100, a leitura lança as mesmas exceções de validação do `POST` (`SinalVitalInvalidoException`, `GravidadeInvalidaException`, `IllegalArgumentException`), e o `@RestControllerAdvice` compartilhado mapeia isso para `400` — rotulando um problema de integridade de dado/config como erro do cliente numa consulta `GET`.
  evidence: Achado pelo review adversarial (blind-hunter + edge-case-hunter). O Design Notes da spec 2.2 já documenta a suposição ("os dados já são válidos... entao esta reconstrucao nunca deveria lancar as excecoes de validacao do dominio"), mas nada no código se defende caso a suposição deixe de valer. Só reproduzível com drift de config/enum/dado — não bloqueia nenhum AC desta story; revisitar se/quando limites ou o enum de gravidade puderem mudar em produção.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-consulta-triagem-score-fatores-contribuintes.md`
  summary: `ConsultaTriagemRepositorioAdapter.ler(...)` desserializa `sintomas`/`score_fatores` (JSONB) sem tratar JSON nulo/malformado nem elemento nulo dentro do array (`List.of(...)` lança `NullPointerException` para elemento nulo).
  evidence: Achado pelo review adversarial (blind-hunter + edge-case-hunter). Hoje cai no handler genérico `Exception -> 500` já existente (que já responde RFC 7807), só falta contexto de diagnóstico específico — baixo risco enquanto o dado persistido continuar vindo só do próprio `POST /v1/triagens` (que já valida antes de gravar).

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-consulta-triagem-score-fatores-contribuintes.md`
  summary: Cobertura de teste de `GET /v1/triagens/{id}` não inclui casos de borda do mapeamento JSON do adapter (lista `sintomas` vazia, unicode em `fator`) nem ids negativos/zero/decimais no path — além dos 3 cenários exigidos pela I/O & Edge-Case Matrix da spec 2.2, que já estão cobertos.
  evidence: Achado pelo review adversarial (blind-hunter). Não bloqueia os 3 ACs da story (todos cobertos pela `ConsultarTriagemIntegrationTest`); vale robustecer a suíte quando houver tempo, mesma categoria de hardening incremental já registrada para outras stories.

## Deferred from: retrospectiva do Epic 2 (2026-09-08, `epic-2-retro-2026-09-08.md`)

- source_spec: `_bmad-output/implementation-artifacts/epic-2-context.md`
  summary: Implementar o relay/publisher real do evento `ScoreCalculado` em tópico SNS FIFO (`MessageGroupId = pacienteId`, DLQ com `maxReceiveCount = 5`, conforme Technical Decisions do epic-2-context.md). Hoje a Story 2.1 só grava o evento na tabela outbox — nenhum publisher/relay existe.
  evidence: Achado pela retrospectiva do Epic 2 (action item 1). Epic 3 (Matching/Alocação) e Epic 4 (Auditoria) dependem deste relay estar publicando corretamente para consumirem `ScoreCalculado` — sem ele, nenhum dos dois epics tem dado real para reagir, mesmo depois de implementados. Bloqueante de fato para o início de qualquer story de Epic 3/4 que dependa do evento (não bloqueou o fechamento do Epic 2 em si).
  status: "PROMOVIDO A STORY FORMAL em 2026-09-08 — decisão do usuário: implementar o relay antes de iniciar a Story 3.1, em vez de acoplar 3.1 diretamente ao `triagem-score-service`. Rastreado agora como Story 3.0 (`sprint-status.yaml`, `spec-3-0-relay-sns-score-calculado.md`); este item deixa de ser trabalho solto."

- source_spec: `_bmad-output/implementation-artifacts/epic-2-context.md`
  summary: Implementar os dois endpoints gRPC internos declarados no Technical Decisions do epic-2-context.md — `ResolveCpfParaId(cpf) -> pacienteId` e `ObterCpfMascarado(pacienteId) -> cpfMascarado`, protegidos por segredo compartilhado + isolamento de rede.
  evidence: Achado pela retrospectiva do Epic 2 (action item 2). Defensável por YAGNI — nenhum consumidor externo existe ainda, nenhuma story do `triagem-score-service` precisou resolver CPF↔ID por fora do próprio serviço. Decisão registrada na retro (2026-09-08): manter a declaração no epic-2-context.md e adiar a implementação até o primeiro consumidor real precisar (Epic 3 ou 4).

## Deferred from: code review da Story 3.0 (2026-09-08, `spec-3-0-relay-sns-score-calculado.md`)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-0-relay-sns-score-calculado.md`
  summary: `RelaySnsPublisherJob` não tem handling de "poison message" nem limite de tentativas — uma linha do outbox que nunca consegue ser publicada (ex.: payload que sempre falha ao serializar) fica pendente para sempre, sem quarentena nem sinal para o operador além do log repetido.
  evidence: Achado pelo blind-hunter review da Story 3.0. Combinado com o comportamento atual de "parar o lote inteiro no primeiro erro" (já corrigido para parar só o paciente afetado), uma linha realmente permanente ainda trava indefinidamente sem alarme. Implementar quarentena/retry-cap é escopo maior que esta story (single-goal); vale uma story/chore própria antes de operar em produção.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-0-relay-sns-score-calculado.md`
  summary: Tópico SNS FIFO `score-calculado.fifo` (`FilaJustaStack.java`) criado sem chave KMS — dados clínicos/identificação de paciente (`pacienteId`, score, sintomas) trafegam sem criptografia at-rest gerenciada por chave própria (SNS já criptografa em trânsito e com a chave gerenciada pela AWS por padrão, mas não há CMK dedicada).
  evidence: Achado pelo blind-hunter review da Story 3.0. Decisão de segurança/compliance que provavelmente afeta todos os tópicos/filas futuros de Epic 3/4 igualmente — melhor decidir uma vez, como padrão de infra, do que por tópico.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-0-relay-sns-score-calculado.md`
  summary: Nenhuma métrica/alarme (backlog do outbox, taxa de falha de publicação, "job travado") existe para o relay — um relay travado (ex.: pelo item de poison message acima) pode passar despercebido em produção só com log.
  evidence: Achado pelo blind-hunter review da Story 3.0. Observabilidade operacional não fazia parte do escopo fechado da spec 3.0 (single-goal: publicar o evento); cabe numa story de observabilidade mais ampla que cubra outros serviços também, não só o relay.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-0-relay-sns-score-calculado.md`
  summary: Nomes de recursos novos no CDK (`score-calculado.fifo`, `TriagemScoreServiceTaskRole`) são fixos, sem qualificador de ambiente — colidiriam se o mesmo stack for implantado mais de uma vez na mesma conta/região (ex.: staging + prod).
  evidence: Achado pelo blind-hunter review da Story 3.0. Mesmo padrão já usado pelos recursos existentes do Epic 1 em `FilaJustaStack.java` (nenhum tem qualificador de ambiente hoje) — não é uma regressão introduzida por esta story, é um gap sistêmico da stack toda; melhor resolver uma vez para todos os recursos do que só para os novos.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-0-relay-sns-score-calculado.md`
  summary: `EventoOutboxRepositorioAdapter` (métodos `buscarNaoPublicados`/`marcarComoPublicado`/`paraDominio`) não tem teste unitário dedicado — só é exercitado indiretamente via os testes de integração do job e do controller.
  evidence: Achado pelo blind-hunter review da Story 3.0. Comportamento já coberto na prática (90 testes verdes, incluindo o caminho feliz e a corrida entre instâncias), mas um teste focado no adapter tornaria regressões futuras mais fáceis de localizar.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-0-relay-sns-score-calculado.md`
  summary: O novo `CfnOutput` `ScoreCalculadoTopicArn` (`FilaJustaStack.java`) não tem asserção de teste dedicada, diferente do padrão já usado para os outputs existentes do stack.
  evidence: Achado pelo blind-hunter review da Story 3.0. Baixo risco (CDK falha o synth se o output referenciar algo inválido), mas fica como lacuna de cobertura de teste.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-0-relay-sns-score-calculado.md`
  summary: Configuração `filajusta.triagem.relay.*` é lida via `@Value` bruto em duas classes (`RelaySnsClientConfig`, `RelaySnsPublisherJob`) com valores-padrão duplicados contra os já definidos em `application.yml`, em vez de um único `@ConfigurationProperties`.
  evidence: Achado pelo blind-hunter review da Story 3.0. Puramente manutenibilidade — fácil de divergir conforme mais configs forem adicionadas ao relay, mas não é um bug hoje.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1b-replica-score-consumidor-sqs-fifo.md`
  summary: Nenhum `CfnOutput` expõe a URL/ARN da nova DLQ (`ScoreCalculadoConsumerDlq`) -- não há jeito descobrível (scripts/CDK outputs) de inspecionar ou redirecionar mensagens presas nela.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1b. Primeira DLQ de produção real do projeto; hoje só a URL da fila principal é exposta.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1b-replica-score-consumidor-sqs-fifo.md`
  summary: `maxReceiveCount=5` da nova DLQ foi herdado de um valor usado só em fixture de teste (`RelaySnsPublisherJobIntegrationTest`), sem taxa de falha transitória esperada documentada -- e `epic-3-context.md` não foi atualizado para promover isso de convenção a decisão registrada.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1b. Primeira vez que esse valor é aplicado a uma fila de produção real, não só a um teste.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1b-replica-score-consumidor-sqs-fifo.md`
  summary: A subscription SNS→SQS (`scoreCalculadoTopic.addSubscription`) não declara `deadLetterQueue` no nível da subscription -- falhas de entrega do SNS para a fila (ex.: problema de policy) ficam sem nenhuma captura, distinto da DLQ já existente do lado do consumidor.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1b. Primeira subscription real criada no tópico `score-calculado.fifo` (Story 3.0 só publicava, sem assinantes).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1b-replica-score-consumidor-sqs-fifo.md`
  summary: Nenhum alarme/monitoramento (ex.: CloudWatch em `ApproximateNumberOfMessagesVisible`) proposto para a nova DLQ -- mensagens podem se acumular silenciosamente sem ninguém ser avisado, já que o deploy ECS segue adiado.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1b. Mesma categoria de gap já registrada para o publisher da Story 3.0 (métricas/alarmes de backlog do outbox).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1b-replica-score-consumidor-sqs-fifo.md`
  summary: `ScoreReplica` (value object de domínio) não implementa `equals()`/`hashCode()`/`toString()` -- todo teste compara campo a campo via getters, sem igualdade por valor nem representação útil em log/debug.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1b. Nenhum consumidor real hoje precisa de igualdade por valor; adicionar agora seria antecipar uso não comprovado.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1b-replica-score-consumidor-sqs-fifo.md`
  summary: O teste de integração ponta a ponta via LocalStack só exercita o cenário "Consumo normal" da I/O Matrix -- os outros 3 (redelivery, fora de ordem, malformada) só são provados no nível de unidade mockada ou do adapter Postgres isolado, nunca no caminho completo SQS→consumidor→banco.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1b. Cada cenário já tem cobertura real (unidade+integração), então não é um gap de verificação (nenhum cenário está descoberto) -- é um aprofundamento de confiança no caminho ponta a ponta específico.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1b-replica-score-consumidor-sqs-fifo.md`
  summary: Nenhum teste de concorrência real prova que o `INSERT ... ON CONFLICT ... WHERE` atômico de fato resolve corrida entre múltiplas instâncias do consumidor -- é justamente a razão declarada (achado do code review da própria story) para escolher SQL nativo em vez de comparação no lado Java.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1b. Teste de concorrência real (múltiplas threads/conexões disputando o mesmo pacienteId) é valioso mas arriscado de escrever de forma não-flaky sob pressão de tempo -- melhor como item próprio, com mais cuidado de design, do que um patch apressado.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: Nenhum marcador distingue "réplica já bootstrapada com sucesso, mas legitimamente vazia" de "réplica nunca bootstrapada" -- se `triagem-score-service` realmente não tiver nenhum Score ainda, todo `GET /v1/fila` refaz a chamada HTTP síncrona de bootstrap.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1c. Chamada é idempotente e barata hoje, mas desnecessária em todo request enquanto a fila estiver genuinamente vazia.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: `TriagemScoreClient` não tem retry/backoff para falha transitória -- uma única tentativa falha já derruba `GET /v1/fila` inteiro como `503`, já que o bootstrap está no caminho quente da primeira consulta.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1c. Mesma categoria de gap de resiliência já registrada para o consumidor SQS da Story 3.1b.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: `prioridadeEfetiva` é serializada em `GET /v1/fila` como `double` bruto sem arredondamento nem contrato de precisão documentado para os consumidores da API.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1c.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: `filajusta.aging.k`/`teto` são injetados via `@Value` solto num `@Bean` factory method em vez de um `@ConfigurationProperties` record -- inconsistente com o estilo mais estruturado usado na config do relay.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1c. Nit de consistência de estilo, sem bug funcional associado.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: A ordenação decrescente por Prioridade Efetiva com múltiplos pacientes nunca é verificada através do endpoint HTTP real (`FilaController`) -- só no nível de caso de uso com dependências mockadas.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1c. Mesma categoria de gap de profundidade E2E já registrada na Story 3.1b.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: Nenhum comentário reconhece a varredura completa + ordenação em memória de `GET /v1/fila` (sem paginação, decisão deliberada da spec) como um limite conhecido a revisitar conforme a fila cresce.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1c.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: Nenhum teste isola os diferentes modos de falha de `TriagemScoreClient` (timeout, 4xx, JSON malformado, conexão recusada) apesar do javadoc afirmar que todos colapsam uniformemente para `503` -- só o caso de HTTP 500 é coberto.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1c. A afirmação do javadoc nunca é provada por teste caso a caso.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1c-consulta-fila-priorizada-bootstrap.md`
  summary: O fix do Patch 1 (`@Transactional` no método inteiro de `ScoreBootstrapService#bootstrapar()`) mantém a transação de banco aberta durante toda a chamada HTTP síncrona a `GET /internal/scores` -- aceitável hoje (chamada limitada a 10s, só ocorre em boot a frio), mas revisitar se o payload de bootstrap crescer muito ou a frequência de boot a frio aumentar.
  evidence: Levantado pelo próprio subagente de implementação ao aplicar o Patch 1 do code review da Story 3.1c (achado real de bootstrap parcial deixando a réplica permanentemente incompleta).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1a-listar-scores-atuais.md`
  summary: Considerar índice em `eventos_outbox.event_type` (ou composto com `occurred_at`) se o volume/latência de `GET /internal/scores` (bootstrap de `matching-alocacao-service`) virar um problema real.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1a. `ListarScoresAtuais` lê toda a tabela via `findByEventTypeOrderByOccurredAtAsc` sem paginação (decisão deliberada da spec); nenhum NFR de performance existe hoje para justificar otimizar agora.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1a-listar-scores-atuais.md`
  summary: `ScoreAtualResponse`/`ScoreResponse`/`FatorContribuinteResponse` duplicam a mesma forma de DTO já presente em `RegistrarTriagemResponse` e `ConsultarTriagemResponse` -- considerar extrair um DTO de resposta compartilhado se um 4º consumidor aparecer.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1a. Duplicação real mas de baixo risco hoje (3 ocorrências); consolidar agora seria abstração prematura pelo padrão do projeto.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1a-listar-scores-atuais.md`
  summary: `ScoresAtuaisRepositorioAdapter` duplica a lógica de desserialização do `payload` do outbox já existente em `EventoOutboxRepositorioAdapter#paraDominio` -- considerar extrair um helper compartilhado de parsing.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1a. Refactor de reuso, não bloqueia nenhum AC; risco de tocar código já testado da Story 2.1/3.0 sem necessidade imediata.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1a-listar-scores-atuais.md`
  summary: Sem logging/observabilidade (contagem de linhas, tempo de execução) em torno da leitura em massa de `GET /internal/scores`, usada como bootstrap síncrono a frio de `matching-alocacao-service`.
  evidence: Achado pelo review adversarial (bmad-build step-04, blind-hunter) sobre o diff da Story 3.1a. Útil para diagnosticar boot a frio lento/vazio em produção, mas não bloqueia os ACs desta story (ainda sem deploy real).

## Deferred from: bmad-build step-02 checkpoint da Story 3.2 (2026-09-10, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2a-numero-sequencial-triagem-scores.md`
  summary: Implementar em `matching-alocacao-service` a propagação de `numeroSequencialTriagem` até `ScoreReplica` (bootstrap + consumidor SQS), o domínio/persistência/upsert interno de `Recurso`, e `GET /v1/recursos/{id}/sugestao` com o algoritmo de tiers de desempate (AD-5).
  evidence: Spec único da Story 3.2 cruzava `triagem-score-service` e `matching-alocacao-service` e introduzia um domínio novo (`Recurso`) do zero — ~2700-2800 tokens (alvo 900-1600), mesmo padrão que gerou o split da Story 3.1. Decisão do usuário no checkpoint de token count do `bmad-build` (step-02): dividir em 3-2a (`triagem-score-service`, spec acima) e 3-2b (este item).
  status: "PROMOVIDO A STORY FORMAL em 2026-09-10 — decisão do usuário: seguir o mesmo padrão de cascata da Story 3.1 (3-1a→3-1b→3-1c). Rastreado agora como Story 3-2b em `sprint-status.yaml`; este item deixa de ser trabalho solto assim que seu spec (`spec-3-2b-sugestao-matching-recurso-desempates.md`) for criado."

## Deferred from: bmad-build step-02 checkpoint da Story 3-2b (2026-09-11, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: Criar em `matching-alocacao-service` o domínio, persistência e endpoint interno de upsert (`POST /internal/recursos` por `codigoRecurso`) do agregado `Recurso` (`recursoId`, `codigoRecurso`, `especificidadeRank`, `disponivel`).
  evidence: Spec único da Story 3-2b cruzava propagação de `numeroSequencialTriagem`, domínio `Recurso` novo (persistência + upsert) e o endpoint de sugestão com algoritmo de tiers — ~3327 tokens (alvo 900-1600), mesmo padrão que gerou os splits anteriores (Story 3.1 e Story 3.2 original). Decisão do usuário no checkpoint de token count do `bmad-build` (step-02): dividir em cascata 3-2b1 (propagação, spec acima) → 3-2b2 (este item) → 3-2b3 (sugestão, item seguinte).
  status: "PROMOVIDO A STORY FORMAL em 2026-09-11 — decisão do usuário: seguir o mesmo padrão de cascata das Stories 3.1 e 3.2. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-2b2-*.md`) for criado."

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: Implementar `GET /v1/recursos/{id}/sugestao` em `matching-alocacao-service` com o algoritmo de tiers de desempate (AD-5): fila global de Pacientes por Prioridade Efetiva (desempate `occurredAt` asc, depois `numeroSequencialTriagem` asc); cada tier de `especificidadeRank` estritamente mais genérico que o do Recurso R, com ≥1 Recurso disponível, consome 1 posição do topo da fila; R recebe `fila[N]`, N = quantidade desses tiers; recursos do mesmo tier nunca se bloqueiam entre si.
  evidence: Mesmo split acima (checkpoint de token count do `bmad-build` step-02, 2026-09-11). Depende de 3-2b1 (`numeroSequencialTriagem` disponível na fila priorizada) e 3-2b2 (domínio/persistência de `Recurso` existir) estarem implementados primeiro.
  status: "PROMOVIDO A STORY FORMAL em 2026-09-11 — mesma decisão de cascata. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-2b3-*.md`) for criado."

## Deferred from: bmad-build step-04 code review da Story 3-2b1 (2026-09-11)

## Deferred from: bmad-build step-04 code review da Story 3-2b2 (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b2-dominio-persistencia-upsert-recurso.md`
  summary: Testes de integração com Testcontainers em `matching-alocacao-service` usam a tag flutuante `postgres:18` (sem minor/patch/digest fixado) em vez de uma versão pinada.
  evidence: Achado do blind-hunter sobre o diff da Story 3.2b2; padrão pré-existente, copiado de `ScoreReplicaRepositorioAdapterIntegrationTest.java` (Story 3.1b/3.2b1) — não introduzido por esta story, apenas replicado. Risco baixo hoje, mas uma atualização upstream da imagem pode alterar comportamento/reprodutibilidade dos testes silenciosamente.
  status: aberto

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: `ScoreReplica`/migration `V2` não validam que `numeroSequencialTriagem` seja positivo (construtor aceita zero/negativo sem lançar exceção; coluna sem `CHECK`), diferente de `pacienteId`/`score` no mesmo construtor.
  evidence: Achado convergente (blind-hunter + edge-case-hunter) sobre o diff da Story 3.2b1. Risco baixo hoje -- `triagemId` sempre vem de `TriagemJpaEntity.id` (`IDENTITY`, sempre positivo) -- mas nada no código impede um valor inválido de entrar no desempate residual.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: Nenhum logging/métrica é emitido no caminho de degradação para `numeroSequencialTriagem = null` (ausência ou formato inválido de `triagemId`/campo do bootstrap).
  evidence: Achado pelo blind-hunter sobre o diff da Story 3.2b1. Sem isso não há como detectar uma regressão sistêmica no payload de `ScoreCalculado` (ex.: produtor para de mandar `triagemId`) sem inspecionar linhas individuais no banco.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: Assimetria não documentada entre produtor e consumidor -- `ScoreAtual` (`triagem-score-service`) exige `numeroSequencialTriagem` via `Objects.requireNonNull`, mas `ScoreInternalDto`/`ScoreReplica`/`ScoreCalculadoConsumerJob` (`matching-alocacao-service`) tratam a ausência como caso permanente de primeira classe.
  evidence: Achado pelo blind-hunter sobre o diff da Story 3.2b1. Não fica claro se a nulabilidade defensiva é para um produtor futuro/alternativo ou complexidade evitável dado o contrato real de hoje.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: `AtualizarScoreReplica.atualizar(...)` cresceu para 5 parâmetros posicionais de mesmo tipo/boxed (`pacienteId, score, occurredAt, eventId, numeroSequencialTriagem`) ao longo das Stories 3.1/3.2a/3.2b1, sem proteção do compilador contra troca de ordem nos dois call sites (bootstrap, consumidor SQS).
  evidence: Achado pelo blind-hunter sobre o diff da Story 3.2b1. Candidato a um pequeno objeto de parâmetro/valor se a assinatura continuar crescendo.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: `GET /v1/fila` (`FilaItemResponse`) não expõe `numeroSequencialTriagem`, mesmo já fluindo por todo domínio/query e decidindo o ranking -- confirmar se é decisão deliberada (fator interno de ordenação) ou descuido.
  evidence: Achado convergente (blind-hunter + verification-gap) sobre o diff da Story 3.2b1. Sem isso não há como um chamador (ou debug de "por que este paciente está nesta posição") ver o fator que acabou de entrar no algoritmo de ranking.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: A cadeia de desempate de `ConsultarFilaPriorizada` (Prioridade Efetiva desc -> `occurredAt` asc -> `numeroSequencialTriagem` asc, nulls-last) ainda não tem uma chave final determinística -- dois itens empatados nos três critérios (plausível quando ambos têm `numeroSequencialTriagem = null`) caem na ordem não determinística do stream, reintroduzindo em escopo mais estreito o problema de flutuação de ordem que o Patch 5 (Story 3.1c) resolveu.
  evidence: Achado pelo blind-hunter sobre o diff da Story 3.2b1.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: Nenhum teste de contrato fixa a equivalência do nome do campo JSON entre `ScoreAtualResponse.numeroSequencialTriagem` (`triagem-score-service`) e `ScoreInternalDto.numeroSequencialTriagem` (`matching-alocacao-service`) além de ambos os lados coincidentemente usarem o mesmo identificador Java.
  evidence: Achado pelo blind-hunter sobre o diff da Story 3.2b1. Um rename em um dos lados degradaria silenciosamente para `null` em todo lugar, sem nenhum teste falhar (ausência já é tratada como estado válido).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: O caminho de bootstrap (`ScoreInternalDto`) depende da desserialização padrão do Jackson para `numeroSequencialTriagem` (`Long`) -- um valor JSON malformado/não-inteiro falharia o parse do lote inteiro, diferente do consumidor SQS, que degrada graciosamente via `extrairNumeroSequencialTriagem` (Patch 2 do code review desta story).
  evidence: Achado pelo edge-case-hunter sobre o diff da Story 3.2b1. Assimetria de tolerância entre as duas origens de escrita do mesmo campo.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b1-numero-sequencial-triagem-score-replica.md`
  summary: Nenhuma nota operacional/runbook documenta que `numero_sequencial_triagem IS NULL` no banco é um estado esperado e benigno vs. sintoma de uma integração upstream quebrada.
  evidence: Achado pelo blind-hunter sobre o diff da Story 3.2b1. Um engenheiro de plantão encontrando nulls hoje só tem os comentários de código como guia.

## Deferred from: bmad-build step-04 code review da Story 3.2b3 (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b3-consulta-sugestao-matching-tiers.md`
  summary: `ConsultarSugestaoRecurso.consultar` faz 3 leituras independentes (`buscarPorId`, `contarTiersMaisGenericosDisponiveis`, `ConsultarFilaPriorizada.consultar()`), cada uma em sua própria transação `readOnly` no nível do adapter, sem um snapshot único consistente entre elas.
  evidence: Achado convergente (blind-hunter + edge-case-hunter) sobre o diff da Story 3.2b3. Uma escrita concorrente (Recurso muda de disponível, ou a fila muda) entre as 3 chamadas pode gerar uma sugestão levemente inconsistente -- risco aceito por design (a spec já declara "sempre recalculada nesta consulta, sem cache, sem reserva de Paciente" -- staleness eventual já é tolerada pelo modelo), mas vale documentar explicitamente ou avaliar uma única transação.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b3-consulta-sugestao-matching-tiers.md`
  summary: Nenhum logging/métrica é emitido em `ConsultarSugestaoRecurso`/`RecursoSugestaoController` distinguindo operacionalmente "sugestão encontrada" vs. "sem sugestão" (fila esgotada ou recurso indisponível) vs. "recurso inexistente".
  evidence: Achado pelo blind-hunter sobre o diff da Story 3.2b3. Sem isso não há como medir, por exemplo, com que frequência um Recurso fica ocioso sem Paciente elegível -- útil para operação de um endpoint central de suporte a decisão de matching. Mesmo padrão do item já deferido para a Story 3.2b1 (linha 289 acima).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2b3-consulta-sugestao-matching-tiers.md`
  summary: Nenhum teste exercita `GET /v1/recursos/{id}/sugestao` com a réplica de Score vazia e o bootstrap síncrono falhando -- o `503` RFC 7807 nesse cenário depende do fallthrough implícito entre `RecursosExceptionHandler` (sem handler para `ScoreBootstrapIndisponivelException`) e `FilaExceptionHandler` (que já trata isso para `GET /v1/fila`, provado por `FilaBootstrapIntegrationTest`).
  evidence: Achado pelo verification-gap sobre o diff da Story 3.2b3. Risco baixo -- a resolução de `@ExceptionHandler` do Spring é por tipo de exceção em todos os beans `@ControllerAdvice`, não por controller de origem, então o mecanismo já provado para `/v1/fila` cobre esta rota também -- mas nenhum teste prova isso diretamente para a nova rota.

## Deferred from: bmad-build step-02 checkpoint da Story 3.3 (2026-09-11, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: Implementar `POST /v1/recursos/{id}/alocacoes` (confirmação) em `matching-alocacao-service`: domínio `Alocacao`, comando `ConfirmarAlocacao` (dois `409` via índices únicos parciais -- recurso já ativo e paciente já ativo em outra Alocação), exclusão de Pacientes já alocados da fila priorizada (`ConsultarFilaPriorizada`), evento `AlocacaoConfirmada` publicado via outbox na mesma transação.
  evidence: Spec único da Story 3.3 cruzava infraestrutura outbox nova do serviço, confirmação e recusa (+ rastreamento pendente de `SugestaoGerada`, AD-10) -- ~1760-3000 tokens (alvo 900-1600), mesmo padrão que gerou os splits das Stories 3.1, 3.2 e 3.2b. Decisão do usuário no checkpoint de token count do `bmad-build` (step-02): dividir em cascata 3-3a (infraestrutura outbox, spec acima) → 3-3b (este item) → 3-3c (recusa + SugestaoGerada, item seguinte).
  status: "PROMOVIDO A STORY FORMAL em 2026-09-11 -- decisão do usuário: seguir o mesmo padrão de cascata das Stories 3.1/3.2/3.2b. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-3b-*.md`) for criado."

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: Implementar `POST /v1/recursos/{id}/alocacoes/recusa` em `matching-alocacao-service`: comando `RecusarSugestao` (motivo obrigatório, `400` se ausente/em branco), persistência do par `(recursoId, pacienteId)` recusado (nunca resugerido para aquele Recurso, Paciente segue elegível para outros), evento `SugestaoRecusada` via outbox; e o rastreamento pendente desde a Story 3.2 (tabela de apoio `ultima_sugestao_registrada`, AD-10) em `ConsultarSugestaoRecurso`, emitindo `SugestaoGerada` quando o Paciente sugerido para um Recurso muda.
  evidence: Mesmo split acima (checkpoint de token count do `bmad-build` step-02, 2026-09-11). Depende de 3-3a (infraestrutura outbox própria do serviço) e 3-3b (domínio `Alocacao`, exclusão de Pacientes alocados da fila) estarem implementados primeiro.
  status: "PROMOVIDO A STORY FORMAL em 2026-09-11 -- mesma decisão de cascata. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-3c-*.md`) for criado."

## Deferred from: bmad-build step-04 code review da Story 3-3a (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: `RelaySnsPublisherJob.publicarPendentes()` roda o lote inteiro numa única `@Transactional`, e `EventoOutboxRepositorioAdapter.marcarComoPublicado` (chamado de dentro dela) é `@Transactional` (REQUIRED) na própria interface -- se essa chamada lançar (ex. falha transitória de banco após o ack do SNS), o interceptor do Spring marca a transação como rollback-only ANTES do `catch` em `publicarEMarcar` engolir a exceção; quando `publicarPendentes()` retorna normalmente, o commit falha com `UnexpectedRollbackException` e reverte TODAS as linhas já marcadas como publicadas no mesmo lote -- mesmo as que já foram entregues ao SNS com sucesso -- contrariando o comentário que documenta a intenção ("nao ha por que interromper o lote").
  evidence: Achado pelo blind-hunter, verificado lendo o código (`RelaySnsPublisherJob.java:160-178`, `EventoOutboxRepositorioAdapter.java:69-70`) -- comportamento real do Spring `@Transactional` em chamada participante. Confirmado como pré-existente e idêntico (byte a byte) em `triagem-score-service/.../RelaySnsPublisherJob.java`/`EventoOutboxRepositorioAdapter.java` desde a Story 3.0 -- não introduzido por esta story, só fielmente replicado. Severidade alta (pode gerar republicação/duplicação de eventos já entregues), recomendável corrigir nos dois serviços juntos (ex. `marcarComoPublicado` com `Propagation.REQUIRES_NEW`).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: `publicarPendentes()` mantém locks de linha (`FOR UPDATE SKIP LOCKED`) e uma conexão de banco presos durante até `batch-size` (default 50) chamadas síncronas de rede ao SNS dentro da mesma transação -- sob latência/instabilidade do SNS, isso pode reter uma conexão do pool por tempo não limitado e bloquear outras instâncias do job.
  evidence: Achado pelo blind-hunter, verificado no código (`RelaySnsPublisherJob.java:100-127`). Pré-existente e idêntico em `triagem-score-service` desde a Story 3.0 -- não introduzido por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: Não há caminho de quarentena/poison-message para uma linha de `eventos_outbox` permanentemente inválida (payload sem `recursoId`, ou JSON malformado que já aborta a leitura do LOTE INTEIRO em `EventoOutboxRepositorioAdapter.buscarNaoPublicados`, não só da linha ruim) -- ela é retentada a cada ciclo do poller para sempre, sem DLQ equivalente à que o consumidor SQS de `ScoreCalculado` já tem (`maxReceiveCount=5`).
  evidence: Achado convergente (blind-hunter + edge-case-hunter), verificado no código (`EventoOutboxRepositorioAdapter.java:62-65`, `.map(this::paraDominio)` sem try/catch por elemento; `RelaySnsPublisherJob.java:182-191`). Pré-existente e idêntico em `triagem-score-service` desde a Story 3.0 -- não introduzido por esta story. Hoje sem risco ativo (nenhum produtor real grava linhas ainda), mas relevante antes de 3.3b/3.3c terem produtores reais.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: `EventoOutboxRepositorio.buscarNaoPublicados` só protege de fato contra corrida entre instâncias do job quando chamado de dentro de uma transação já aberta (hoje sempre o caso, via `RelaySnsPublisherJob.publicarPendentes`) -- não há `@Transactional(propagation = MANDATORY)` impondo isso si um chamador futuro esquecer a transação, o `FOR UPDATE SKIP LOCKED` perde o efeito silenciosamente.
  evidence: Achado pelo edge-case-hunter, risco já documentado em comentário no próprio código (`EventoOutboxRepositorioAdapter.java:57-61`). Pré-existente e idêntico em `triagem-score-service` desde a Story 3.0 -- não introduzido por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: `domain/EventoOutbox` não valida `eventType` em branco nem o tamanho de `eventType`/`correlationId` contra as colunas do banco (`VARCHAR(64)`/`VARCHAR(128)`), e a migration não tem `CHECK` constraints (`version >= 1`, `correlation_id <> ''`) como rede de segurança contra um INSERT fora do caminho do domínio Java.
  evidence: Achado pelo edge-case-hunter, verificado no código (`EventoOutbox.java`) e na migration (`V4__create_eventos_outbox.sql`). Confirmado como pré-existente e idêntico em `triagem-score-service/.../domain/EventoOutbox.java` e suas migrations `V1`/`V2` desde as Stories 2.1/3.0 -- não introduzido por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: `filajusta.matching.outbox-relay.enabled: true` por padrão com `topic-arn` vazio faz qualquer `mvn spring-boot:run`/subida local direta (fora dos testes, que sobrescrevem a propriedade) falhar rápido com `IllegalStateException` -- não há `docker-compose`/`application-local.yml` no repo fornecendo um valor seguro para desenvolvimento local.
  evidence: Achado pelo blind-hunter. Confirmado como pré-existente e idêntico em `triagem-score-service` (mesmo default `enabled: true` + `topic-arn` vazio) desde a Story 3.0 -- não introduzido por esta story, e nunca antes registrado em deferred-work.md.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: Nenhum dos dois serviços (`triagem-score-service`/`matching-alocacao-service`) tem um teste dedicado provando que desligar o kill switch do PRÓPRIO relay outbox (`*.relay.enabled=false`/`*.outbox-relay.enabled=false`) realmente impede a criação dos beans `SnsClient`/`RelaySnsPublisherJob` via `@ConditionalOnProperty` -- só existe esse tipo de teste para o kill switch do consumidor SQS de `ScoreCalculado` (`ScoreCalculadoConsumerJobConditionalOnPropertyDisabledTest`), que é um mecanismo diferente.
  evidence: Achado pelo blind-hunter, confirmado por busca no código -- nenhum arquivo `RelaySnsPublisherJobConditionalOnPropertyDisabledTest`-like existe em nenhum dos dois serviços. Pré-existente desde a Story 3.0 de `triagem-score-service` -- não introduzido por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3a-infraestrutura-outbox-matching-alocacao.md`
  summary: Nenhuma métrica/gauge (idade da linha pendente mais antiga, contagem de pendentes) nem alarme CloudWatch está associado ao novo tópico `matching-alocacao-eventos.fifo` ou aos caminhos de falha do relay -- mesma classe de gap já deferida para a Story 3.2b3 (linha ~322 acima), agora também no lado de publicação.
  evidence: Achado pelo blind-hunter. Consistente com o padrão já aceito de adiar observabilidade neste projeto (ver itens já deferidos para 3.2b1/3.2b3).

## Deferred from: bmad-build step-02 checkpoint da Story 3-3b (2026-09-11, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b1-confirmacao-alocacao.md`
  summary: Excluir Pacientes com Alocação ativa da fila priorizada (`ConsultarFilaPriorizada`, `GET /v1/fila`) -- novo porto de leitura `AlocacaoConsultaRepositorio`/adapter, filtro antes da ordenação.
  evidence: Spec da Story 3-3b (confirmação de sugestão de matching) cruzava domínio `Alocacao` novo + os 2 índices únicos parciais de `409` + `marcarIndisponivel` + evento `AlocacaoConfirmada` via outbox + endpoint REST + exception handler + a exclusão da fila -- ~2300-2750 tokens dependendo do método de contagem (alvo 900-1600), mesmo padrão que gerou os splits das Stories 3.1, 3.2, 3.2b e 3-3a. Decisão do usuário no checkpoint de token count do `bmad-build` (step-02): dividir em cascata 3-3b1 (confirmação/criação de Alocação, spec acima) → 3-3b2 (este item). Depende de 3-3b1 (domínio `Alocacao` e sua persistência) estar implementado primeiro.
  status: "PROMOVIDO A STORY FORMAL em 2026-09-11 -- decisão do usuário: seguir o mesmo padrão de cascata das Stories 3.1/3.2/3.2b/3-3a. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-3b2-*.md`) for criado."

## Deferred from: bmad-build step-04 code review da Story 3-3b1 (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b1-confirmacao-alocacao.md`
  summary: `matching_alocacao.alocacao.recurso_id` (`V5__create_alocacao.sql`) não tem `FOREIGN KEY` para `matching_alocacao.recurso.recurso_id` -- nada no banco impede um `recursoId` órfão/inexistente de ser inserido fora do caminho normal da aplicação (ex. backfill, bug futuro).
  evidence: Achado pelo blind-hunter. `matching_alocacao.recurso` vive no mesmo schema/serviço (ao contrário de `paciente_id`, que referencia dado de outro serviço via réplica) -- tecnicamente viável adicionar a FK. Risco baixo hoje (o único caminho de escrita, `ConfirmarAlocacao`, sempre valida a existência do Recurso antes do INSERT).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b1-confirmacao-alocacao.md`
  summary: `matching_alocacao.alocacao.status` é `TEXT NOT NULL` sem `CHECK (status = 'ATIVA')` -- nada no banco impede um valor inválido escrito por um caminho futuro fora do domínio Java.
  evidence: Achado pelo blind-hunter. Mesma classe de gap já deferida para `eventos_outbox`/`Recurso` (linha ~365 acima) -- consistente com o padrão já aceito de não usar `CHECK` constraints neste projeto.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b1-confirmacao-alocacao.md`
  summary: A AC "duas confirmações concorrentes para o mesmo `recursoId`: exatamente uma recebe `201` e a outra `409`" só é provada com chamadas sequenciais (`AlocacaoRepositorioAdapterIntegrationTest`/`AlocacaoControllerIntegrationTest`) -- nenhum teste multi-thread real exercita a corrida via 2 threads simultâneas contra o mesmo `recursoId`.
  evidence: Achado pelo blind-hunter. A garantia real vem do índice único parcial do Postgres (mecanismo já bem estabelecido), não de lógica da aplicação -- um teste multi-thread validaria majoritariamente o próprio Postgres, não código deste serviço. Risco baixo, mas registrado para eventual reforço.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b1-confirmacao-alocacao.md`
  summary: Nenhum teste cobre o branch de fallback de `AlocacaoRepositorioAdapter#traduzir` -- uma `DataIntegrityViolationException` cujo nome de constraint não é `ux_alocacao_recurso_ativa` nem `ux_alocacao_paciente_ativa` propaga sem tradução (comportamento documentado), mas não testado.
  evidence: Achado pelo blind-hunter. Branch defensivo de baixa probabilidade (só dispara se uma constraint nova/diferente for adicionada à tabela no futuro) -- mesma classe de gaps de cobertura já aceita em outras stories deste épico (3.2b1/3.2b3).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b1-confirmacao-alocacao.md`
  summary: `X-Correlation-Id` só valida comprimento (`> 128` caracteres); caracteres de controle/quebra de linha no header fluem sem sanitização para o payload do outbox e para logs.
  evidence: Achado pelo blind-hunter. Pré-existente e idêntico ao `CorrelationIdInvalidoException` de `triagem-score-service` (Story 3.0) -- mesma validação, mesma lacuna, não introduzida por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b1-confirmacao-alocacao.md`
  summary: Nenhum teste de integração prova que uma falha em `RecursoRepositorio#marcarIndisponivel` ou `EventoOutboxRepositorio#salvar` (depois do INSERT de `Alocacao` já ter sido aceito) reverte a transação inteira -- exatamente o cenário que o `@Transactional` único de `ConfirmarAlocacao` existe para proteger.
  evidence: Achado pelo blind-hunter e pelo verification-gap (via `ConfirmarAlocacao`, ordenação das 3 escritas). Mecanismo padrão do Spring (`@Transactional` reverte em `RuntimeException` não capturada) já usado e confiado em toda a base -- sem teste dedicado, mas sem motivo concreto para desconfiar do comportamento padrão.

## Deferred from: bmad-build step-02 checkpoint da Story 3-3b2 (2026-09-11, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b2-exclusao-pacientes-alocados-fila.md`
  summary: Aplicar o filtro de exclusão em `ConsultarFilaPriorizada.consultar()` usando o novo `AlocacaoConsultaRepositorio` -- a mudança de comportamento real de `GET /v1/fila` (Paciente com Alocação ativa deixa de aparecer), com teste unitário de `ConsultarFilaPriorizadaTest` e cenário E2E em `FilaBootstrapIntegrationTest`.
  evidence: Spec original de 3-3b2 (~2067 tokens estimados, alvo 900-1600) cruzava a criação do porto de leitura `AlocacaoConsultaRepositorio`/adapter/query JPA nova (infraestrutura pura, sem mudança de comportamento visível) com a aplicação do filtro no caso de uso da fila (mudança de comportamento real). Decisão do usuário no checkpoint de token count do `bmad-build` (step-02): dividir em cascata 3-3b2a (porto de leitura + adapter + teste de integração, spec acima) → 3-3b2b (este item). Depende de 3-3b2a (o porto `AlocacaoConsultaRepositorio` e sua implementação) estar implementado primeiro.
  status: "PROMOVIDO A STORY FORMAL em 2026-09-12 -- 3-3b2a concluída (PR #32 mergeado); este item virou `spec-3-3b2b-exclusao-pacientes-alocados-fila.md`. Este item deixa de ser trabalho solto."

## Deferred from: bmad-build step-04 code review da Story 3-3b2a (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b2a-porto-leitura-alocacoes-ativas.md`
  summary: `matching-alocacao-service` inteiro está fora do pipeline de CI -- `.github/workflows/ci.yml` só roda `mvn -B -pl gateway-service,auth-service,infra-cdk -am test`, excluindo o módulo do reactor.
  evidence: Achado pelo verification-gap. Pré-existente (introduzido no commit `50f224d`, antes desta story) e não causado por esta mudança -- afeta igualmente todos os testes já existentes do módulo (ex.: 3-3a/3-3b1). Hoje a única rede de segurança automática para o novo comportamento desta story (e todo o resto do serviço) é execução manual/local de `mvn -pl matching-alocacao-service -am verify`.

## Deferred from: bmad-build step-04 code review da Story 3-3b2b (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3b2b-exclusao-pacientes-alocados-fila.md`
  summary: `ConsultarFilaPriorizada.consultar()` lê `AlocacaoConsultaRepositorio#pacientesComAlocacaoAtiva()` e `FilaRepositorio#listarTodas()` em duas consultas separadas, fora de uma transação/snapshot compartilhado -- uma Alocação confirmada ou liberada entre as duas leituras pode gerar uma resposta transitoriamente inconsistente (paciente aparece indevidamente ou some por um instante).
  evidence: Achado de forma convergente pelo blind-hunter e pelo edge-case-hunter (independentemente). Consistente com o design geral do épico, que já tolera leituras não transacionais entre bounded contexts (a fila é "sempre recomputada sob demanda", "não reserva o Paciente", e o mesmo Paciente pode aparecer sugerido para mais de um Recurso até uma confirmação consumi-lo, resolvido via constraint única + `409`). Risco baixo e da mesma classe já aceita no resto do serviço, mas nunca havia duas leituras live combinadas numa única resposta antes desta story -- vale reavaliar se a fila crescer em criticidade.

## Deferred from: bmad-build step-02 checkpoint da Story 3-3c (2026-09-12, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c-recusa-sugestao-gerada.md`
  summary: Aplicar em `ConsultarSugestaoRecurso` o pulo de pares `(recursoId, pacienteId)` já recusados ao decidir a sugestão, e o rastreamento AD-10 (`ultima_sugestao_registrada` + publicação de `SugestaoGerada` via outbox quando o Paciente sugerido para um Recurso muda).
  evidence: Spec original da Story 3-3c (~2913 tokens, alvo 900-1600) cruzava o comando `RecusarSugestao` (persistência do par recusado + evento `SugestaoRecusada`, escrita nova e isolada) com uma mudança de comportamento real em `ConsultarSugestaoRecurso` (algoritmo de sugestão + tabela de apoio AD-10) -- mesmo padrão que gerou os splits das Stories 3.1/3.2/3.2b/3-3a/3-3b/3-3b2. Decisão do usuário no checkpoint de token count do `bmad-build` (step-02): dividir em cascata 3-3c1 (comando `RecusarSugestao`, spec renomeada para `spec-3-3c1-recusa-sugestao-comando.md`) → 3-3c2 (este item). Depende de 3-3c1 (persistência do par recusado, porta `SugestaoRecusadaRepositorio`) estar implementado primeiro.
  status: "PROMOVIDO A STORY FORMAL em 2026-09-12 -- decisão do usuário: seguir o mesmo padrão de cascata das Stories anteriores. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-3c2-*.md`) for criado."

## Deferred from: bmad-build step-04 code review da Story 3-3c1 (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c1-recusa-sugestao-comando.md`
  summary: Nenhum teste prova que `{id}` não-UUID em `POST /v1/recursos/{id}/alocacoes/recusa` retorna `400`, apesar do Javadoc de `AlocacaoController` afirmar explicitamente "mesmo tratamento... de id não-UUID" do endpoint de confirmação.
  evidence: Achado pelo blind-hunter. Mesma categoria de gap já aceita no épico (ex. Story 3.2b3, `ScoreBootstrapIndisponivelException`/fallthrough do `@ExceptionHandler`): a resolução de exceção do Spring é por tipo em todos os beans `@ControllerAdvice`, não por controller de origem, então o mecanismo já provado para `POST /v1/recursos/{id}/alocacoes` cobre esta rota também -- mas nenhum teste prova isso diretamente para o endpoint novo.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c1-recusa-sugestao-comando.md`
  summary: Nenhum teste de integração HTTP prova `correlationId` acima de 128 caracteres retornando `400` no endpoint de recusa -- a cobertura desse cenário existe só a nível de teste unitário do caso de uso (`RecusarSugestaoTest`), nunca ponta a ponta via HTTP/Postgres real para esta rota específica.
  evidence: Achado pelo blind-hunter. Mesmo padrão de "mecanismo já provado, não testado por rota" do item acima -- `correlationIdAcimaDoLimiteRetorna400` já prova o caminho completo para o endpoint de confirmação.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c1-recusa-sugestao-comando.md`
  summary: O campo `motivo` de `RecusarSugestaoRequest` não tem limite de tamanho (`@Size`) na validação Bean Validation, permitindo payloads arbitrariamente grandes persistidos em `sugestao_recusada` e propagados no payload JSONB do evento de outbox.
  evidence: Achado de forma convergente pelo blind-hunter e pelo edge-case-hunter (independentemente). Mesma categoria de hardening sem limite de tamanho já deferida para `sintomas` em `POST /v1/triagens` (Story 2.1, ver entrada acima) -- nenhuma NFR define um limite; fica como hardening a revisitar se o volume/abuso justificar.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c1-recusa-sugestao-comando.md`
  summary: `matching_alocacao.sugestao_recusada.recurso_id` (`V6__create_sugestao_recusada.sql`) não tem `FOREIGN KEY` para `matching_alocacao.recurso.recurso_id` -- nada no banco impede um `recursoId` órfão/inexistente de ser inserido fora do caminho normal da aplicação.
  evidence: Achado de forma convergente pelo blind-hunter e pelo edge-case-hunter (independentemente). Mesma classe de gap já deferida para `matching_alocacao.alocacao.recurso_id` na Story 3-3b1 (ver entrada acima) -- consistente com o padrão já aceito de não usar FK entre essas tabelas neste projeto; risco baixo hoje (o único caminho de escrita, `RecusarSugestao`, sempre valida a existência do Recurso antes do INSERT).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c1-recusa-sugestao-comando.md`
  summary: `SugestaoRecusadaJpaRepository#upsert` é `@Modifying` sem `clearAutomatically`/`flushAutomatically` -- se uma chamada futura, na mesma transação, ler a entidade via JPA depois do upsert nativo, pode obter um estado desatualizado do contexto de persistência.
  evidence: Achado pelo blind-hunter. Risco baixo hoje -- nenhum caminho de código lê `SugestaoRecusadaJpaEntity` via JPA na mesma transação após o upsert (a leitura de verificação nos testes é via `jdbcTemplate` cru) -- mas vale revisitar se um consumidor futuro (ex. Story 3-3c2) precisar reler a entidade pela mesma via.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c1-recusa-sugestao-comando.md`
  summary: Nenhum campo de texto livre da aplicação (`motivo`, `correlationId`, `codigoRecurso`, etc.) sanitiza bytes NUL (` `) antes de gravar em colunas `TEXT`/`VARCHAR` do Postgres -- um JSON de entrada com ` ` escapado desserializa normalmente via Jackson, mas o INSERT subsequente falha no Postgres (que não aceita NUL em texto), surgindo como `500` não tratado.
  evidence: Achado pelo edge-case-hunter, generalizado após inspeção: gap sistêmico pré-existente em toda a base (não introduzido por esta story especificamente, replicado em qualquer campo de texto livre novo, incluindo `motivo` desta story) -- nunca tratado em nenhuma story anterior.

## Deferred from: bmad-build step-02 checkpoint da Story 3-3c2 (2026-09-12, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2-recusa-sugestao-ad10.md`
  summary: Rastreamento AD-10 em `ConsultarSugestaoRecurso` -- tabela de apoio nova `ultima_sugestao_registrada` (schema `matching_alocacao`, PK `recurso_id`) guardando a ultima sugestao registrada por Recurso, e publicacao de `SugestaoGerada` via outbox (mesmo molde de `RecusarSugestao`/`ConfirmarAlocacao`) somente quando o Paciente sugerido para aquele Recurso muda em relacao ao ultimo registro.
  evidence: Spec original da Story 3-3c2 (~3113 tokens, alvo 900-1600) cruzava o pulo de pacientes recusados (leitura pura, porta `SugestaoRecusadaConsultaRepositorio` sobre a tabela `sugestao_recusada` da 3-3c1) com uma mudanca de comportamento mais ampla em `ConsultarSugestaoRecurso` (nova tabela de apoio, `@Transactional`, novo evento de outbox, rewiring do bean) -- mesmo padrao que gerou os splits das Stories 3.1/3.2/3.2b/3-3a/3-3b/3-3b2/3-3c. Decisao do usuario no checkpoint de token count do `bmad-build` (step-02): dividir em cascata 3-3c2a (pulo de recusados, spec renomeada para `spec-3-3c2a-pulo-recusados-sugestao.md`) -> 3-3c2b (este item). Depende de 3-3c2a (o loop de selecao de `pacienteIdSugerido` que o rastreamento AD-10 vai observar) estar implementado primeiro.
  status: "PROMOVIDO A STORY FORMAL em 2026-09-12 -- decisao do usuario: seguir o mesmo padrao de cascata das Stories anteriores. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-3c2b-*.md`) for criado."

## Deferred from: bmad-build step-04 code review da Story 3-3c2a (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2a-pulo-recusados-sugestao.md`
  summary: `matching_alocacao.sugestao_recusada` (criada na Story 3-3c1) nao tem estrategia de retencao/limpeza -- a tabela so cresce ao longo da vida operacional de cada Recurso, sem archival ou expurgo.
  evidence: Achado pelo blind-hunter. Pre-existente a esta story (a tabela e o padrao de escrita foram introduzidos na 3-3c1) -- 3-3c2a apenas passou a LER essa tabela, sem alterar seu padrao de crescimento. Nenhuma NFR define um limite ou politica de retencao; fica como hardening a revisitar se o volume justificar.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2a-pulo-recusados-sugestao.md`
  summary: `ConsultarSugestaoRecurso.consultar()` agora combina 3 leituras nao-transacionais (fila global, contagem de tiers, recusados) numa unica resposta -- uma recusa registrada entre a leitura de `filaGlobal` e a leitura de `recusadosPara` pode nao ser considerada na mesma consulta (paciente recem-recusado ainda aparece sugerido).
  evidence: Achado pelo edge-case-hunter. Mesma classe de risco ja aceita no epico (janela de corrida entre leitura do Set de alocados e a replica de score em `ConsultarFilaPriorizada`, Story 3-3b2b/3.2b3 -- ver entradas anteriores deste arquivo) -- esta story estende o mesmo padrao ja tolerado (leituras live combinadas sem transacao unica) para uma terceira fonte. Risco baixo e da mesma classe ja aceita; vale reavaliar se a fila crescer em criticidade.

## Deferred from: bmad-build step-02 checkpoint da Story 3-3c2b (2026-09-12, token count)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b-rastreamento-ad10-sugestao-gerada.md`
  summary: Aplicar o rastreamento AD-10 em `ConsultarSugestaoRecurso` -- `@Transactional`, comparacao do `pacienteIdSugerido` calculado com o ultimo registrado via `UltimaSugestaoRegistradaRepositorio` (porta desta story), upsert quando muda (e o novo valor nao e `null`), e publicacao de `SugestaoGerada` via outbox (mesmo molde de `RecusarSugestao`/`ConfirmarAlocacao`, `correlationId` via `UUID.randomUUID()`), incluindo o wiring do bean `consultarSugestaoRecurso` e os testes (unitario + E2E) que provam a mudanca de comportamento.
  evidence: Spec original da Story 3-3c2b (~2428 tokens medidos via tiktoken cl100k_base sobre o corpo, alvo 900-1600) cruzava a criacao da infraestrutura pura de apoio (tabela `ultima_sugestao_registrada`, porta `UltimaSugestaoRegistradaRepositorio`, entity/JPA repository/adapter, teste de integracao do adapter -- sem mudanca de comportamento visivel) com a aplicacao real do rastreamento em `ConsultarSugestaoRecurso` (mudanca de comportamento real: nova escrita dentro do fluxo de leitura, novo evento publicado) -- mesmo padrao que gerou os splits das Stories 3.1/3.2/3.2b/3-3a/3-3b/3-3b2/3-3c/3-3c2. Decisao do usuario no checkpoint de token count do `bmad-build` (step-02): dividir em cascata 3-3c2b1 (infraestrutura pura, spec renomeada para `spec-3-3c2b1-porto-ultima-sugestao-registrada.md`) -> 3-3c2b2 (este item). Depende de 3-3c2b1 (a porta `UltimaSugestaoRegistradaRepositorio` e sua implementacao) estar implementado primeiro.
  status: "PROMOVIDO A STORY FORMAL em 2026-09-12 -- decisao do usuario: seguir o mesmo padrao de cascata das Stories anteriores. Este item deixa de ser trabalho solto assim que seu spec (`spec-3-3c2b2-*.md`) for criado."

## Deferred from: bmad-build step-04 code review da Story 3-3c2b1 (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b1-porto-ultima-sugestao-registrada.md`
  summary: `matching_alocacao.ultima_sugestao_registrada.recurso_id` (`V7__create_ultima_sugestao_registrada.sql`) nao tem `FOREIGN KEY` para `matching_alocacao.recurso.recurso_id` -- nada no banco impede um `recursoId` orfao/inexistente, e a tabela nao tem nenhuma operacao de exclusao (um Recurso removido deixa linha residual para sempre).
  evidence: Achado pelo blind-hunter. Mesma classe de gap ja deferida para `matching_alocacao.sugestao_recusada` (Story 3-3c1) e `matching_alocacao.alocacao` (Story 3-3b1) -- consistente com o padrao ja aceito de nao usar FK entre essas tabelas neste projeto.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b1-porto-ultima-sugestao-registrada.md`
  summary: `UltimaSugestaoRegistradaJpaRepository#upsert` e `@Modifying` sem `clearAutomatically`/`flushAutomatically` -- se uma chamada futura, na mesma transacao, ler a entidade via JPA depois do upsert nativo, pode obter um estado desatualizado do contexto de persistencia.
  evidence: Achado pelo edge-case-hunter. Mesma classe ja deferida para `SugestaoRecusadaJpaRepository#upsert` (Story 3-3c1), que ja antecipava esta story ("vale revisitar se um consumidor futuro, ex. Story 3-3c2, precisar reler a entidade pela mesma via"). Risco nao se materializa no uso planejado da 3-3c2b2 (`ConsultarSugestaoRecurso` sempre LE `pacienteIdRegistrado` antes de decidir se chama `registrar`, nunca le de novo depois de escrever, na mesma transacao) -- mas vale revisitar se essa ordem mudar no futuro.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b1-porto-ultima-sugestao-registrada.md`
  summary: Nenhum teste verifica o valor de `registrado_em` persistido/atualizado pelo upsert -- os testes de integracao so verificam `pacienteIdRegistrado` (via `Optional<Long>`) e a contagem de linhas, nunca o timestamp em si.
  evidence: Achado pelo blind-hunter. Baixo risco (o upsert nativo atribui `:registradoEm` diretamente, sem logica condicional que possa corromper o valor) -- mas a coluna existe sem nenhuma prova automatizada de que e escrita/atualizada corretamente.

## Deferred from: bmad-build step-04 code review da Story 3-3c2b2 (2026-09-13, 2 rodadas)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: A entrada acima (linhas 496-497) sobre `UltimaSugestaoRegistradaJpaRepository#upsert` sem `clearAutomatically` ficou desatualizada -- a Story 3-3c2b2 (apos o loopback de concorrencia) removeu a pre-leitura de `pacienteIdRegistrado` em `ConsultarSugestaoRecurso` (agora chama `registrar` direto, sem ler antes, para fechar a corrida de eventos duplicados). O risco de leitura desatualizada pos-upsert continua nao se materializando no uso atual (nenhum consumidor le a entidade via JPA na mesma transacao apos o upsert), mas pelo motivo oposto ao registrado antes.
  evidence: Nao modificar a entrada original (preserva o historico da decisao da 3-3c2b1); esta entrada substitui a premissa desatualizada para quem ler o arquivo de cima para baixo.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: `GET /v1/recursos/{id}/sugestao` deixou de ser resiliente a falhas de escrita -- se `UltimaSugestaoRegistradaRepositorio#registrar` ou `EventoOutboxRepositorio#salvar` lancarem (ex.: erro transitorio de conexao), a transacao inteira faz rollback e a requisicao GET retorna erro, mesmo que o calculo da sugestao em si tenha funcionado.
  evidence: Achado convergente do blind-hunter e do edge-case-hunter (independentemente). Consequencia direta e ja aceita do design do AD-10 (escrever dentro do fluxo de um GET, decisao tomada e revalidada em 3 specs consecutivas: 3-3c2b, 3-3c2b1, 3-3c2b2) -- nao ha requisito de resiliencia/retry nas Boundaries de nenhuma dessas specs.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: Upsert em `ultima_sugestao_registrada` para um `recursoId` "quente" (muito consultado concorrentemente) agora disputa lock de linha do Postgres -- risco de contencao que nao existia neste endpoint quando ele era leitura pura.
  evidence: Achado convergente do blind-hunter e do edge-case-hunter (independentemente). Risco baixo no volume esperado (consultas de sugestao de recurso, nao trafego de alta frequencia); mesma classe de tradeoff ja aceita ao aprovar o design AD-10 nesta e nas 2 specs anteriores da cascata.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: O compare-and-set atomico (`WHERE paciente_id <> excluded.paciente_id`) fecha a duplicacao de evento quando 2 requisicoes concorrentes calculam o MESMO `pacienteIdSugerido`, mas nao garante ordem quando calculam valores DIFERENTES (A e B) -- a que vence a corrida no Postgres pode nao ser a que leu o estado mais recente da fila.
  evidence: Achado pelo blind-hunter. Auto-corrige na proxima consulta (o calculo e sempre refeito do zero, sem cache -- Boundaries da spec 3.2b3), entao o registro fica no maximo 1 consulta atrasado; mesma classe de janela estreita ja aceita no epico (ex. Story 3-3c2a, leituras nao-transacionais combinadas).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: Os novos testes E2E de `RecursoSugestaoControllerIntegrationTest` (transicao A->B, repeticao, fila esgotada, bootstrap a frio) truncam `score_replica` antes/depois de si mesmos para isolamento, enquanto os testes HAPPY_PATH/pulo-de-recusados pre-existentes da mesma classe dependem da tabela acumulada (nunca truncada) e de scores sempre crescentes -- acoplamento fragil a mudanca de ordem/paralelizacao do JUnit.
  evidence: Achado pelo blind-hunter. Padrao de acoplamento ja pre-existente nesta classe de teste (introduzido em Story 3.2b3, mantido em 3-3c2a) -- os 4 novos testes apenas adicionam mitigacao (truncate before/after) sem alterar o padrao de fundo.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: Nenhum teste prova que, se `EventoOutboxRepositorio#salvar` lancar excecao APOS `UltimaSugestaoRegistradaRepositorio#registrar` ja ter sido chamado na mesma transacao, o rollback desfaz tambem a escrita em `ultima_sugestao_registrada`.
  evidence: Achado pelo blind-hunter. Mesma classe de atomicidade nao testada ja aceita para `RecusarSugestao`/`ConfirmarAlocacao` (nenhuma story anterior deste epico testa rollback de outbox); a garantia vem do `@Transactional` do Spring/JPA, nao de logica propria.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: Nenhum log estruturado no novo caminho de registro/publicacao de `SugestaoGerada` dentro de `ConsultarSugestaoRecurso` -- dificulta diagnosticar em producao duplicacoes, falhas de publicacao ou volume inesperado de eventos.
  evidence: Achado pelo blind-hunter. Nenhuma story anterior deste epico adiciona logging estruturado em caminhos de escrita/outbox -- gap sistemico pre-existente, nao introduzido especificamente por esta story.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-3c2b2-rastreamento-ad10-sugestao-gerada.md`
  summary: `ConsultarSugestaoRecurso.consultar()` (`@Transactional`, nao `readOnly`) agora envolve a chamada HTTP sincrona de bootstrap de `ConsultarFilaPriorizada#consultar` (quando a replica esta vazia) dentro de uma transacao JPA/Postgres, sem timeout explicito -- uma dependencia externa lenta/travada poderia manter uma conexao do pool aberta indefinidamente.
  evidence: Achado pelo edge-case-hunter (nao convergente com outros reviewers desta rodada). Risco tangencial ao fix de concorrencia desta story -- o `@Transactional` nao-`readOnly` em si ja tinha sido aprovado na spec original (antes do loopback), sem nenhum reviewer sinalizar esse ponto na 1a rodada.
