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
