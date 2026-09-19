---
reviewer: rubric-walker (Reviewer Gate, bmad-architecture)
target: ARCHITECTURE-SPINE.md (ConfirmaSUS, architecture-Fase5-2026-09-17)
date: 2026-09-17
---

# Review: ARCHITECTURE-SPINE.md — ConfirmaSUS

## Veredito

Espinha sólida e bem amarrada ao PRD (14/14 FRs cobertos, ADs enforceáveis na maioria dos casos, renomeações consistentes com o código brownfield real) — mas tem três lacunas concretas de nível arquitetural que, se não fechadas antes do `bmad-create-epics-and-stories`, deixam pontos reais de divergência para `bmad-build`: (1) nenhum mecanismo decidido para a transição `AGUARDANDO_JANELA → AGUARDANDO_CONFIRMACAO` (só a expiração tem poller, a abertura da janela não); (2) o "code sweep" do AD-1 lista o que manter/descartar em `Alocacao`/`Recurso` mas ignora toda a pipeline de infraestrutura de Score (`ScoreReplica`, `ScoreBootstrapService`, `TriagemScoreClient`, `ScoreCalculadoConsumerJob`, `LiberacaoAgendada*`) que fica órfã/quebrada sob o novo desenho; (3) o envelope operacional trata `infra-cdk` como "reaproveitado sem alteração" enquanto o Deferred, na mesma respiração, apresenta Postgres-em-container-vs-RDS como decisão ainda aberta — mas o CDK atual já implementa Postgres em container (decisão de facto já tomada, não é mais uma pergunta em aberto).

Nenhum desses três é um erro que invalida a espinha — são lacunas de cobertura que dois `bmad-build` diferentes preencheriam de formas incompatíveis se não forem fechadas agora.

## Achados

### [ALTA] Gap 1 — Transição de abertura da Janela de Confirmação sem mecanismo decidido

AD-4 define o estado `AGUARDANDO_JANELA → AGUARDANDO_CONFIRMACAO (ao abrir a Janela, dispara NotificacaoConfirmacaoPublicada, FR-3)`, mas **nenhum AD** especifica o mecanismo dessa transição — ao contrário de AD-5, que é dedicado inteiramente ao mecanismo simétrico de expiração (`AGUARDANDO_CONFIRMACAO → LIBERADO`, poller `@Scheduled` + escrita condicional).

Isso importa porque FR-3 exige idempotência explícita ("publicada exatamente uma vez por Agendamento por abertura de Janela... sob reprocessamento") — o mesmo tipo de garantia que AD-5 resolve com `UPDATE ... WHERE status = <estado_esperado>`. Sem um AD equivalente para a abertura, dois times/stories diferentes poderiam implementar isso de formas incompatíveis: um poller `@Scheduled` irmão do de AD-5 (checando `janelaAbreEm <= now()`), ou uma transição calculada preguiçosamente em tempo de leitura (o que quebraria a garantia de escrita condicional que AD-4 exige para todas as transições, e tornaria o disparo do evento `NotificacaoConfirmacaoPublicada` não-determinístico).

O próprio Structural Seed confirma a lacuna: a pasta `agendamento-confirmacao-service/infrastructure/scheduler/` é descrita como `# poller @Scheduled de expiração (AD-5)` — singular, só um poller, nenhuma menção a um poller de abertura de janela.

**Recomendação:** decidir explicitamente (provavelmente um AD-5b ou extensão do AD-5) que a abertura da Janela também é um poller `@Scheduled` simétrico, com a mesma escrita condicional (`UPDATE ... WHERE status = 'AGUARDANDO_JANELA'`), publicando `NotificacaoConfirmacaoPublicada` via outbox na mesma transação — ou registrar explicitamente por que isso é desnecessário (ex.: se o seed já carrega todo Agendamento de demo diretamente em `AGUARDANDO_CONFIRMACAO`, tornando a transição `AGUARDANDO_JANELA` irrelevante para o MVP — mas isso precisa estar escrito, não implícito).

### [ALTA] Gap 2 — Code sweep do AD-1 não cobre a pipeline de infraestrutura de Score que fica órfã

AD-1 lista o que manter e descartar da entidade `Alocacao`/`Recurso` em `matching-alocacao-service` (mantém `Alocacao/ConfirmarAlocacao/RecusarSugestao`, descarta "Sugestão de Matching/Prioridade Efetiva/tiers de especificidade"). Isso está correto para o agregado de domínio — mas o brownfield real tem uma pipeline inteira de infraestrutura acoplada ao conceito de Score que o pivô extingue e que **não é mencionada em lugar nenhum da espinha**:

- `matching-alocacao-service/infrastructure/bootstrap/{ScoreBootstrapService,TriagemScoreClient,ScoreInternalDto}.java` — bootstrap de score via chamada a `triagem-score-service`.
- `matching-alocacao-service/domain/ScoreReplica.java` + `application/query/ScoreBootstrap.java` + `ScoreBootstrapIndisponivelException.java` — réplica local de score.
- `matching-alocacao-service/infrastructure/relay/{ScoreCalculadoConsumerJob,ScoreCalculadoSqsClientConfig}.java` — consumidor do evento `ScoreCalculado` publicado pelo antigo `triagem-score-service`.
- `matching-alocacao-service/domain/LiberacaoAgendada.java` + `application/command/LiberacaoAgendadaRepositorio.java` + `infrastructure/relay/{LiberacaoAgendadaRelayJob,LiberacaoAgendadaSqsClientConfig}.java` — o mecanismo antigo de delay via SQS que o memlog (linha 12) explicitamente diz que não serve mais ("SQS DelaySeconds tem teto de 15min, a Janela de Confirmação é ~48h") e que AD-3 confirma extinto ("a fila SQS standard de delay do spine antigo... não existe mais").
- Do lado do produtor: `triagem-score-service` inteiro descarta `Triagem/Score/GravidadePercebida/SinaisVitais/CalculadorDeScore` (AD-1 já cobre isso), o que por si só já quebra `TriagemScoreClient`/`ScoreBootstrapService` do lado consumidor — mas a espinha não fecha esse laço explicitamente.

Sem essa cobertura explícita, um `bmad-build` que seguir AD-1 ao pé da letra pode deixar essa pipeline (código morto ou, pior, uma chamada gRPC/HTTP/SQS para um endpoint que não existe mais em `agendamento-confirmacao-service`) sem tratamento — divergindo de outro `bmad-build` que a remove por bom senso. Isso é exatamente o tipo de "ponto real de divergência para o nível abaixo" que a espinha deveria fixar e não deixou de fora.

**Recomendação:** adicionar uma frase ao AD-1 (ou ao Deferred, com apontamento explícito) determinando que toda a pipeline de bootstrap/replicação de Score e o relay de `LiberacaoAgendada` (delay antigo) são removidos por completo de `liberacao-repasse-service`, sem substituto — não apenas os campos `especificidadeRank`/tiers do agregado `Recurso`.

### [MÉDIA] Gap 3 — Deferred contradiz a postura "infra-cdk sem alteração" sobre Postgres

O Deferred lista como pergunta em aberto: *"RDS gerenciado vs. Postgres em container no próprio ECS — decisão de custo fina, cabe a uma story de infraestrutura."* Mas o `infra-cdk` real (`FilaJustaStack.buildPostgresService`/`buildPostgresEfs`, linhas ~445-545 de `FilaJustaStack.java`) **já implementa** Postgres como container Fargate com EFS para persistência — essa decisão já foi tomada e está em produção de fato no brownfield, não é mais uma escolha em aberto.

Isso é uma inconsistência pequena mas real entre duas afirmações da própria espinha: o Design Paradigm/AD-1 tratam `infra-cdk` como reaproveitado (implicando que a topologia atual — incluindo Postgres em container — é o baseline), enquanto o Deferred apresenta o mesmo ponto como ainda indefinido. Isso pode levar uma story de infra a reabrir uma decisão de custo que na prática já está tomada e funcionando.

**Recomendação:** reescrever o item do Deferred para "Postgres roda em container Fargate com EFS (decisão já vigente em `infra-cdk`); revisitar para RDS gerenciado é um upgrade futuro fora de escopo do MVP" — em vez de apresentá-lo como pergunta aberta.

### [MÉDIA] Gap 4 — Envelope operacional: `infra-cdk`/`gateway-service` precisam de adições reais que a espinha não nomeia

O Design Paradigm afirma sem qualificação que `auth-service`, `gateway-service` e `infra-cdk` "são reaproveitados sem alteração". AD-1 é mais preciso ("sem alteração de nome nem de comportamento") — mas essa qualificação não está no Design Paradigm, e o texto solto pode ser lido por `bmad-build` como "zero trabalho de infra".

Na prática, verificado no código:
- `infra-cdk/src/main/java/.../FilaJustaStack.java` hoje só provisiona `FargateService` para `gateway-service`, `auth-service` e Postgres — **não existe** nenhum `SecurityGroup` nem `FargateService` para os 3 serviços de domínio (`agendamento-confirmacao-service`, `liberacao-repasse-service`, `auditoria-service`), nem os SGs `SGAC`/`SGLR`/`SGAU` do próprio diagrama de rede da espinha (linha ~194-199 do ARCHITECTURE-SPINE.md).
- `gateway-service/src/main/resources/application.yml` hoje só roteia `POST /v1/auth/login` — nenhuma rota para os serviços de domínio existe ainda (mencionado como trabalho deferido em outro artefato, `AC-3/AC-4`, não citado por esta espinha).
- Os tópicos/filas SNS/SQS atuais (`ScoreCalculadoTopic`, `MatchingAlocacaoEventosTopic`, `LiberacaoAgendadaQueue`) têm nomes/conteúdo do domínio antigo; os novos eventos da AD-3 (`VagaLiberada`, `SugestaoRepasseGerada` etc.) exigem recursos SNS/SQS FIFO novos, nomeados de forma diferente da AD-3 (`MessageGroupId`/`MessageDeduplicationId` corretos, filas FIFO em vez das standard atuais).

Nada disso invalida a espinha — é claramente trabalho de implementação, adequado a `bmad-build`. O problema é que a espinha não sinaliza esse volume de trabalho de infra em nenhum lugar (nem no Deferred), o que é justamente o tipo de lacuna que o item "envelope operacional/ambiental" deste checklist pede para não deixar passar. `bmad-build` planejando a partir só desta espinha pode subestimar a fatia de infra do épico.

**Recomendação:** acrescentar ao Deferred algo como "Provisionamento CDK dos 3 serviços de domínio (FargateService + SGs SGAC/SGLR/SGAU + tópicos/filas SNS/SQS FIFO novos por AD-3) — `infra-cdk` mantém padrão/comportamento mas precisa de recursos novos, não é zero-trabalho." Isso não muda nenhuma decisão de arquitetura, só evita a leitura errada de "sem alteração" = "sem trabalho".

### [BAIXA] Gap 5 — AD-6 não nomeia a entidade que rastreia pares recusados

AD-6 exige que `RecusarSugestaoRepasse` "exclui o par (`recursoId`, `pacienteId`) de futuras sugestões para aquela Vaga" — isso requer persistir os pares recusados em algum lugar. O brownfield já tem esse padrão pronto (`SugestaoRecusadaJpaEntity`/`SugestaoRecusadaRepositorio`/`SugestaoRecusadaConsultaRepositorioAdapter` em `matching-alocacao-service`), e AD-1 já renomeia consistentemente `Alocacao→RepasseConfirmado`, `ConfirmarAlocacao→ConfirmarRepasse`, `RecusarSugestao→RecusarSugestaoRepasse` — mas não menciona o análogo para `SugestaoRecusada`, e o esboço de pastas do Structural Seed (`liberacao-repasse-service/domain/`) lista só `Recurso, ListaEsperaEntrada, SugestaoRepasse, RepasseConfirmado`, sem a entidade de rastreio de recusados.

Severidade baixa porque o padrão de renomeação já estabelecido em AD-1 é inequívoco o suficiente para que qualquer implementador razoável infira `SugestaoRecusada → SugestaoRepasseRecusada` (ou nome equivalente) sem ambiguidade real — mas vale fechar explicitamente, já que os outros três nomes irmãos foram fixados um a um.

**Recomendação:** adicionar à lista de renomeações do AD-1 (ou ao esboço de pastas) a entidade que rastreia pares recusados, com nome explícito.

### [BAIXA] Gap 6 — AD-2 não declara mecanismo de enforcement, ao contrário de AD-10

AD-10 é explícito: "enforcement mecânico via regra ArchUnit por módulo de serviço, como check obrigatório de CI." AD-2 (a regra mais fundamental do desenho — `domain/` nunca importa framework, CQRS lógico) não tem equivalente — fica implícito que é convenção de code review, não gate mecânico. Não há ArchUnit hoje no repositório (confirmado — nenhuma referência a `com.tngtech.archunit` em nenhum módulo), então isso não é regressão, é uma oportunidade perdida de fechar o mesmo tipo de lacuna que AD-10 fechou para si mesma.

**Recomendação:** opcional — se o rigor de AD-10 (ArchUnit + CI) vale a pena para isolamento de schema, provavelmente vale o mesmo para a regra mais citada do documento (pureza de `domain/`). Considerar estender o mesmo enforcement a AD-2 ou justificar explicitamente por que não.

## Checklist — avaliação item a item

1. **Fixa os pontos reais de divergência para `bmad-build`, sem deixar nenhum de fora?** Quase — ver Gaps 1, 2, 5 acima. A maioria dos pontos de divergência real (nomes de entidade/evento/comando, máquina de estados, propriedade de dados, fronteira de CPF, autenticação) está bem fixada. Os três gaps são reais, mas pontuais, não sistêmicos.
2. **Rule de cada AD é enforceable e previne o que o Prevents diz?** Sim na maioria (AD-3/4/5/6/8/9/10/11/13 têm mecanismo concreto — escrita condicional, security group, schema+REVOKE, gRPC+segredo). Exceção: AD-2 (ver Gap 6) — Rule é mais convenção do que gate mecânico, mas o Prevents ainda é atendido na prática pela estrutura de pastas imposta.
3. **Deferred permitiria duas unidades divergirem de forma que quebra o sistema?** Não — os itens do Deferred são calibração fina (cadência de poller), fora de escopo explícito do PRD (RBAC, LGPD, canal real), ou detalhe de implementação sem risco sistêmico (schema exato, Flyway vs. Liquibase). Única ressalva: o item Postgres/RDS (Gap 3) é redundante/contraditório, não perigoso.
4. **Tecnologia nomeada está corrente/verificada ou marcada `[ASSUMPTION]`?** Sim — a seção Stack se auto-marca `[ASSUMPTION]` explicitamente, pedindo reconfirmação antes do primeiro build, e as versões (Spring Boot 4.1.1, Spring Cloud 2025.1.2, Java 25) batem exatamente com o `pom.xml` raiz já commitado (mesma nota "verificado 2026-09-06").
5. **Ratifica (não contradiz) o código brownfield existente?** Sim, com uma lacuna: os nomes/renomeações centrais (`Paciente`/`Cpf`/`ResolverOuCriarPaciente`, `Alocacao→RepasseConfirmado`, `ConfirmarAlocacao→ConfirmarRepasse`, `RecusarSugestao→RecusarSugestaoRepasse`) batem exatamente com as classes reais em `matching-alocacao-service/` e `triagem-score-service/` (verificado por listagem direta dos pacotes `domain`/`application/command`). A estrutura de pastas proposta no Structural Seed é compatível com o layout Maven multi-módulo real (serviços são módulos irmãos no `pom.xml` raiz, não pacotes dentro de um módulo — a árvore `confirma-sus/<service>/domain|application|infrastructure` do spine espelha exatamente o layout físico atual, não inventa nesting incompatível). A lacuna é a pipeline de Score/delay órfã (Gap 2), não coberta pelo sweep.
6. **Cobre as 14 capacidades do PRD?** Sim — tabela Capability → Architecture Map lista FR-1 a FR-14 uma a uma, todas mapeadas a um serviço e a pelo menos um AD.
7. **Envelope operacional/ambiental decidido, deferido ou em aberto — não "espinha só de domínio"?** Parcialmente. AD-11 (rede), AD-10 (persistência/schema/backup), AD-12 (runtime) e os três diagramas do Structural Seed cobrem bem o envelope de deployment. As lacunas são as dos Gaps 3 e 4: o Deferred contradiz o brownfield já decidido (Postgres em container) e a espinha não sinaliza que `infra-cdk`/`gateway-service` precisam de recursos novos reais (SGs, FargateServices, tópicos/filas, rotas) apesar de "sem alteração" — não é ausência de decisão arquitetural, é ausência de visibilidade sobre o tamanho do trabalho operacional que a decisão implica.

## Verificação de brownfield (comandos executados)

- `find matching-alocacao-service/.../matching` e `find triagem-score-service/.../triagem` — listagem completa de `domain/`, `application/command`, `application/query`, `infrastructure/*` dos dois serviços.
- `pom.xml` raiz — confirma módulos Maven irmãos (`gateway-service`, `auth-service`, `infra-cdk`, `triagem-score-service`, `matching-alocacao-service`), versões Spring Boot/Cloud/Java idênticas às citadas na espinha.
- `infra-cdk/src/main/java/com/filajusta/infra/FilaJustaStack.java` — confirma topologia de rede real (VPC subnet pública única, `natGateways(0)`, `assignPublicIp(true)`, security groups por serviço) bate com AD-11; confirma ausência de `FargateService`/`SecurityGroup` para os 3 serviços de domínio; confirma Postgres já roda em container Fargate + EFS.
- `gateway-service/src/main/resources/application.yml` — confirma que hoje só a rota de login é pública/roteada.
- `.memlog.md` da sessão de distilação — usado para confirmar que as decisões `[ADOPTED]` citadas na espinha (renomear-e-podar em vez de reescrever, poller em vez de SQS delay, `auditoria-service` retomando AD-10 antigo, composição do seed) de fato vieram de decisão do usuário registrada, não inventadas pela espinha.

## Arquivo revisado

`_bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md`
