# PRD Quality Review — ConfirmaSUS (prd-Fase5-2026-09-16)

## Overall verdict

Este é um PRD forte, calibrado corretamente para o contexto (MVP de hackathon acadêmico, backend-only, entrega solo, avaliado por banca, brownfield sobre projeto anterior). A tese central (fechar o ciclo de ociosidade sem decisão clínica automatizada) é rastreável do Brief até FRs, Success Metrics e Constraints, com um counter-metric (SM-C1) que protege exatamente a restrição legal que motivou o pivô. A done-ness clarity é o ponto mais forte — quase todo FR tem consequências testáveis, incluindo casos de borda e conflito (dupla confirmação, corrida entre gestores, lista vazia). Os achados abaixo são majoritariamente mecânicos (um `[ASSUMPTION]` não indexado, dois protagonistas de UJ sem nome) — nada bloqueante para avançar a `bmad-architecture`.

## Decision-readiness — strong

Decisões estão marcadas como decisões, não como "considerações": §5 Non-Goals é assertivo e datado ("Decisão do usuário, 2026-09-16"), e §11 separa explicitamente "Decisões Fechadas" de "Assumptions Abertas" — uma estrutura que evita o vício comum de disfarçar pendência como fato consumado. Trade-offs são nomeados com o que foi abandonado, não só o que foi escolhido: FR-5 diz explicitamente que uma vez confirmado o Paciente não pode mais recusar (renúncia deliberada a uma capability de "desfazer"); FR-14 admite sem rodeio que não há RBAC aplicado nesta fase. Não há "toda decisão balanceia tudo" — o documento tem um eixo (decisão humana obrigatória) que subordina outras escolhas a ele.

Open Questions (§10) está vazia por design, com a pendência real capturada em §11 em vez de ficar como pergunta retórica — isso é o comportamento correto, não uma lacuna.

### Findings
- **low** Recusa pós-confirmação: decisão vs. assumption (FR-5, §5) — A regra "uma vez confirmado, não pode recusar depois" aparece como `[ASSUMPTION: fora de escopo do MVP...]` em FR-5, mas também como Non-Goal definitivo em §5 ("'Desfazer' uma Confirmação já registrada"). Não há conflito de conteúdo, mas o mesmo fato ora soa como decisão fechada, ora como assumption aberta. *Fix:* decidir se isso é uma Decisão Fechada (mover para §11 "Decisões Fechadas") ou uma Assumption Aberta (indexá-la em "Assumptions Abertas") — hoje não está em nenhuma das duas listas de §11 (ver Mechanical notes).

## Substance over theater — strong

Nenhuma persona é decorativa: as 4 personas (Paciente, Paciente em lista de espera, Gestor de Agenda, Auditor) mapeiam 1:1 para as 4 UJs, que por sua vez realizam FRs específicos — não há "quinta persona" de preenchimento. A seção "What Makes This Different" do Brief (herdada no Vision do PRD) é incomumente honesta para o gênero: admite textualmente que o lembrete isolado "não é novidade" e já existe em produção no Ceará, e localiza a inovação real (fechar o ciclo + decisão humana + auditoria) — isso é o oposto de innovation theater.

Os NFRs em §7 evitam o boilerplate clássico ("deve ser escalável/seguro/confiável"): cada um tem um mecanismo concreto amarrado a ele — continuidade sob falha nomeia o serviço específico (Log Auditável) e a semântica de retomada; a barra de testes tem números (≥90% JaCoCo, PIT, BDD/Gherkin) em vez de "boa cobertura de testes".

### Findings
- **low** "Contratos versionados" é o NFR mais genérico do §7 — não define um mecanismo (semver? contrato de evento com schema registry? tolerância a campo novo?), diferente dos vizinhos que são concretos. *Fix:* se for relevante para a arquitetura, adicionar uma frase sobre como a versão é carregada no evento (ex.: campo `version` no payload outbox) — pode perfeitamente ficar para `bmad-architecture` dado que o PRD já sinaliza que decisões de implementação vão para lá.

## Strategic coherence — strong

A tese é única e declarada logo no Vision: transformar vaga ociosa detectada em oferta ativa e auditável, sem decidir prioridade clínica. Cada Feature (4.1–4.5) é um elo de uma corrente causal única (seed → notificação → confirmação/expiração → liberação → sugestão → decisão humana → log), não uma lista de capabilities soltas. As Success Metrics validam essa tese diretamente (SM-1 automação da liberação/sugestão, SM-2 explicabilidade de 100% das decisões) em vez de medir atividade genérica. O counter-metric SM-C1 é o ponto mais forte deste PRD: ele contrabalança exatamente o risco que motivou o pivô (reintrodução implícita de um proxy de gravidade clínica), amarrando Success Metrics de volta à Constraint legal de §8.

Sem achados — nenhuma revisão necessária aqui.

## Done-ness clarity — strong

Esta é a dimensão mais bem executada do documento. Praticamente todo FR (FR-1 a FR-14) tem 2–3 "Consequences (testable)" verificáveis, e várias cobrem explicitamente casos de borda e concorrência que PRDs costumam pular: FR-4 trata confirmação fora da janela e confirmação duplicada (idempotência); FR-10 trata conflito de duas tentativas de confirmar a mesma Sugestão de Repasse ("retorna erro de conflito, não sobrescreve silenciosamente"); FR-8/FR-9/FR-13 tratam explicitamente o caso de lista/histórico vazio como resposta explícita, não erro. FR-12 chega a definir o critério de completude do próprio log ("a ausência de entrada para uma decisão é, por definição, um bug") — isso é uma acceptance criteria de fato, não uma consequência decorativa.

Não há nenhuma ocorrência de linguagem vaga do tipo "trata graciosamente", "performance razoável" ou "amigável ao usuário" em nenhuma FR ou NFR.

Sem achados — nenhuma revisão necessária aqui.

## Scope honesty — strong

§5 Non-Goals faz trabalho real (10 itens, cada um com motivo, não uma lista genérica de "não vamos fazer X"). Os `[ASSUMPTION: ...]` estão espalhados nos pontos certos (janela de confirmação, composição do seed, quantidade de candidatos sugeridos, RBAC) e majoritariamente indexados em §11. A postura sobre LGPD em §8 é honesta ao ponto de dizer "a LGPD provavelmente não se aplica a este MVP em sentido estrito" em vez de fingir conformidade — exatamente o padrão que a calibração do usuário para este contexto pede, e coerente com o mesmo padrão herdado do PRD anterior.

Densidade de itens em aberto (6 assumptions abertas + 0 open questions bloqueantes) é proporcional às apostas de um MVP de hackathon — não é um PRD "pronto para luz verde de produção" fingindo estar mais fechado do que está, nem excessivamente hesitante para o que precisa decidir agora.

Nota: o documento não usa o marcador `[NOTE FOR PM]` em nenhum lugar; os pontos que esse marcador cobriria (tensões não resolvidas, decisões adiadas) estão de fato capturados — só que via "Assumptions Abertas" em §11 em vez do marcador inline. Funcionalmente equivalente, não é uma lacuna de conteúdo, apenas uma variação de convenção.

### Findings
- **medium** `[ASSUMPTION]` de FR-5 não aparece em §11 — Ver Mechanical notes: a assumption "recusa após confirmação fica fora de escopo" (FR-5) não está em "Decisões Fechadas" nem em "Assumptions Abertas". *Fix:* adicionar uma linha em §11 apontando para FR-5, ou promovê-la para "Decisões Fechadas" já que na prática §5 Non-Goals já a trata como decidida.

## Downstream usability — strong

O Glossário (§3) cobre todos os substantivos de domínio usados nas FRs (Paciente, Agendamento, Janela de Confirmação, Vaga Liberada, Lista de Espera, Sugestão de Repasse, Repasse Confirmado, Log Auditável) e o uso é consistente em capitalização e forma ao longo do documento — não encontrei drift de terminologia. IDs são contíguos e únicos: FR-1–FR-14, UJ-1–UJ-4, SM-1–SM-3 + SM-C1. As referências cruzadas testadas todas resolvem: "Realiza FR-x" em cada UJ aponta para FRs existentes; "Validates FR-x" em cada SM idem; `(ver §8 Constraints/LGPD)`, `(ver §11)`, `(ver Non-Goals)` apontam para seções que existem e contêm o conteúdo prometido.

### Findings
- **low** Protagonistas sem nome em UJ-3 e UJ-4 (§2.3) — UJ-1 (Marina) e UJ-2 (João) têm protagonista nomeado com contexto; UJ-3 usa "Regulador de agenda de uma UBS" e UJ-4 usa "Auditor / órgão de controle" — papéis genéricos, sem nome. Dado o contexto (papéis institucionais, não pacientes individuais), isso é defensável, mas quebra a consistência do padrão adotado nas duas primeiras UJs. *Fix:* opcional — dar um nome/contexto mínimo (ex.: "Renata, reguladora da UBS X") só se isso ajudar a demo/vídeo; não é bloqueante para arquitetura ou stories.

## Shape fit — strong

O PRD acerta a calibração em vários eixos simultâneos que normalmente entram em tensão: (1) é backend-only mas mantém UJs porque há múltiplos atores reais com FRs distintos — não é UJ-theater; (2) é uma atualização motivada por restrição legal, e a rastreabilidade da constraint é not-negotiable aqui: §0 (motivo do pivô) → §3 (nota de escopo do glossário) → §8 (Legal) → §5 (Non-Goals) → SM-C1 (counter-metric) formam uma cadeia fechada sobre a mesma restrição, sem reafirmações soltas; (3) é brownfield explícito, e o documento separa com clareza o que é herdado sem alteração (auth-service, gateway-service, infra-cdk, padrão outbox) do que é novo (domínio de negócio) — inclusive nomeando o serviço que fica órfão (`triagem-score-service`) e adiando sua decisão para arquitetura em vez de ignorá-lo; (4) apesar de ser entrega solo/hackathon, a rigidez de NFRs (90% cobertura, PIT, BDD) é justificada pelos próprios critérios de edital citados em §0/§9 — não é rigor postiço para um "hobby project", é rigor amarrado a critério de nota real.

Sem achados — nenhuma revisão necessária aqui.

## Mechanical notes

- **Glossary drift**: nenhum encontrado. Termos de domínio usados com capitalização e forma consistentes em todas as seções revisadas.
- **ID continuity**: FR-1–FR-14 contíguos sem lacunas; UJ-1–UJ-4 contíguos; SM-1–SM-3 + SM-C1 (counter-metric rotulado à parte, correto). Nenhuma duplicata encontrada.
- **Assumptions Index roundtrip**: quase completo, uma lacuna real —
  - Indexados corretamente: janela de confirmação (§3/FR-3), composição do seed (FR-1), quantidade de candidatos sugeridos (FR-9), destino de `ConfirmarAlocacao`/`RecusarSugestao` (§8), destino do `triagem-score-service` (§8), RBAC (FR-14).
  - **Não indexado**: o `[ASSUMPTION: fora de escopo do MVP; uma vez confirmado, o Paciente não pode recusar depois]` em FR-5 não aparece em nenhuma das duas listas de §11. Reverso (toda entrada de "Decisões Fechadas" aparece inline): confirmado, sem gaps.
- **UJ protagonist naming**: UJ-1 (Marina) e UJ-2 (João) nomeados com contexto; UJ-3 e UJ-4 usam papel genérico sem nome próprio (ver finding em Downstream usability).
- **Referência cruzada com nuance**: em §5, `"Desfazer" uma Confirmação já registrada (ver FR-5)` — o ponteiro é tecnicamente correto (a assumption sobre "desfazer confirmação" está de fato no corpo de FR-5, cujo título é "Recusa Ativa"), mas pode confundir quem espera que o ponteiro aponte para a FR de Confirmação (FR-4). Não é um link quebrado, apenas potencialmente contraintuitivo.
- **Seções obrigatórias para o porte declarado**: presentes e proporcionais — Glossário, Non-Goals, Assumptions Index, Constraints/Guardrails (incluindo Legal, Cost, Privacy), Success Metrics com counter-metric. Nenhuma seção de escala empresarial (i18n, compliance formal, RBAC de produção) foi forçada, e todas as ausências desse tipo estão declaradas honestamente em §5/§8 em vez de omitidas silenciosamente — correto para o contexto de MVP acadêmico.
