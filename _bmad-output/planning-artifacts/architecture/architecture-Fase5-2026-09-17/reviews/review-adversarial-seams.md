---
title: Adversarial Seams Review — ARCHITECTURE-SPINE ConfirmaSUS
reviewed: architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md
date: 2026-09-17
reviewer: bmad-architecture Reviewer Gate (finalize_reviewers, lente adversarial de seams)
method: >
  Para cada AD, construir dois pares de unidades um nível abaixo (dois devs ou dois agentes
  implementando a mesma story/serviço) que obedecem a letra do AD e ainda assim produzem algo
  incompatível: formato de dado colidindo, duas entidades "donas" do mesmo dado, caminhos
  conflitantes de mutação de estado, schema de evento interpretado diferente nos dois lados,
  condição de corrida sem AD que a feche, nome usado de dois jeitos na própria spine.
---

# Adversarial Seams Review: ARCHITECTURE-SPINE ConfirmaSUS

## Overall Take

A spine é incomum em rigor — AD-4 nomeia explicitamente "escrita condicional" como o mecanismo de fechamento de corrida, AD-7 nomeia explicitamente `eventId` como chave de deduplicação, AD-8 é preciso sobre onde CPF pode e não pode existir. Isso torna mais fácil, não mais difícil, achar os buracos: os lugares onde a spine tem *o mesmo tipo* de problema mas **não** aplica o mesmo mecanismo de fechamento são visíveis por contraste. Os cinco achados abaixo seguem esse padrão — em quatro dos cinco casos, a spine já resolveu um problema estruturalmente idêntico em outro AD, e simplesmente não generalizou a solução para o segundo lugar onde o mesmo problema reaparece. O quinto (motivo/causa do Log Auditável) é o inverso: a spine fecha o vocabulário para 1 de 7 tipos de decisão auditável e deixa os outros 6 completamente abertos — cada um dos dois lados (produtor de evento, consumidor de auditoria) vai inventar sua própria convenção.

Nenhum destes é um problema de todo-o-sistema; todos são fecháveis com uma frase a mais em um AD existente ou um AD novo e pequeno. Mas cada um, sem fechamento, produz exatamente o tipo de incompatibilidade "os dois times implementaram a letra do AD e o sistema não funciona junto" que este gate existe para pegar.

## Findings

### [high] `ResolverOuCriarPaciente` (AD-1/AD-8): janela de deploy único vs. chamada em runtime, sem AD para o caminho síncrono cross-service

**Localização:** AD-1 (linha 42, "populado uma vez pelo seed-adapter, sem sincronização em runtime"; linha 41, upsert do `seed-adapter`), AD-8 (linha 98, "exclusivo para `liberacao-repasse-service` ao ingerir Lista de Espera sintética, via `seed-adapter`") vs. PRD FR-9 linha 176: *"Se a Lista de Espera está vazia no momento da liberação, a Vaga fica marcada como Liberada sem sugestão pendente, **revisitável quando alguém entrar na Lista depois**"* — e o próprio Deferred da spine (linha 289) só tira do escopo a *repescagem automática*, não a possibilidade de alguém entrar na Lista de Espera depois do deploy inicial.

**O par adversarial:** dois desenvolvedores implementando `RegistrarEntradaListaEspera` em `liberacao-repasse-service`, cada um lendo AD-1/AD-8 ao pé da letra:

- **Dev A** lê "exclusivo para `liberacao-repasse-service` ao ingerir Lista de Espera sintética (via `seed-adapter`)" como uma restrição de *quando* o gRPC pode ser chamado — só durante a carga do `seed-adapter`, uma janela única de deploy. Implementa o comando REST para aceitar apenas `pacienteId` já resolvido (nunca CPF), porque para ele "depois do deploy não existe mais CPF chegando nesse serviço".
- **Dev B** lê a mesma frase como uma restrição de *quem* pode chamar o gRPC (só `liberacao-repasse-service`, nunca outro cliente) — sem relação com timing. Como o PRD (linha 176) deixa claro que alguém pode "entrar na Lista depois" do deploy, e a única forma de identificar um Paciente novo é por CPF (AD-8), ele expõe `RegistrarEntradaListaEspera` via REST aceitando CPF no corpo e chama `ResolverOuCriarPaciente` via gRPC **em runtime, a qualquer momento**, síncrono, dentro do próprio request HTTP do Gestor.

Ambos obedecem a letra de AD-1/AD-8. O sistema resultante diverge: no cenário do Dev A, a PRD linha 176 é inatingível sem um mecanismo B não previsto em lugar nenhum da spine; no cenário do Dev B, existe uma dependência síncrona cross-service em runtime (não só no deploy) que **nenhum AD cobre**: AD-3 (async/outbox) explicitmente não se aplica a chamadas gRPC; AD-8 e AD-11 fixam *que* o gRPC é liberado por security group, mas nenhum AD define timeout, retry, circuit breaker ou o que a API REST de `liberacao-repasse-service` retorna ao Gestor se `agendamento-confirmacao-service` estiver fora do ar no meio desse request — nem se o comando falha (perdendo o registro de entrada na fila) ou fica pendurado. Isso é exatamente o tipo de "NFR Continuidade sob falha parcial" (binds de AD-3/AD-7) que a spine promete alhures e não promete aqui.

**Fix sugerido:** um AD (ou extensão de AD-8) decidindo explicitamente: (a) se toda ingestão de Lista de Espera pós-deploy passa OBRIGATORIAMENTE pelo `seed-adapter` re-executado (nenhum endpoint REST síncrono de runtime existe — a PRD linha 176 vira "revisitável só num próximo ciclo de seed", e isso deveria estar em Deferred/Non-Goals, não implícito); OU (b) se existe de fato um caminho runtime, definir o modo de falha do gRPC síncrono (timeout, o que a API REST retorna em `UNAVAILABLE`, se o registro de Lista de Espera é rejeitado ou fica em estado pendente aguardando retry).

---

### [high] "Motivo"/"causa" do Log Auditável (AD-7/FR-12): vocabulário fechado para 1 de 7 tipos de decisão

**Localização:** AD-4 linha 74 fixa `motivoLiberacao ∈ {RECUSA, NAO_CONFIRMADO}` — mas esse enum só cobre a transição do `Agendamento` para `LIBERADO`. AD-7 (linha 92) e FR-12 do PRD (linha 200: *"é registrada no Log Auditável com timestamp e **motivo**"*) exigem motivo/causa para **sete** tipos de decisão: notificação, Confirmação, Recusa, Não Confirmado, Liberação, Sugestão de Repasse, confirmação/recusa de repasse. A Consistency Conventions (linha 135) define o envelope genérico do evento (`{eventId, eventType, occurredAt, version, correlationId, payload}`) e diz que "cada `eventType` tem schema companion (JSON Schema) versionado junto ao produtor" — ou seja, o `payload` de cada um dos sete eventos, incluindo se e como carrega um campo de motivo, está inteiramente deferido, sem nenhum AD amarrando o nome do campo nem o vocabulário de valores.

**O par adversarial:** o time que implementa o producer (`agendamento-confirmacao-service`/`liberacao-repasse-service`, outbox) e o time que implementa o consumer (`auditoria-service`, `RegistrarDecisaoAuditavel`) trabalham cada um a partir da letra da spine, sem visão do payload um do outro:

- **Produtor** (Dev/Agente A): para `ConfirmacaoRegistrada` e `SugestaoRepasseGerada` — eventos que não têm "causa" no sentido de rejeição, só "isto aconteceu" — não inclui campo de motivo algum no payload (motivo = `null`, ou o campo nem existe no JSON Schema desses dois `eventType`). Para `AgendamentoNaoConfirmado`, reaproveita literalmente `motivoLiberacao` (nome do campo do AD-4) no payload.
- **Consumidor** (`auditoria-service`, Dev/Agente B): projeta `LogAuditavel.motivo` como `NOT NULL`, porque FR-12/AD-7 diz "toda... decisão... registrada... com motivo" sem exceção — e espera um campo chamado `motivo` (não `motivoLiberacao`) em **todo** payload consumido, com um vocabulário próprio que ele inventa por tipo de evento (ex.: `motivo = "PACIENTE_CONFIRMOU"` para `ConfirmacaoRegistrada`, `motivo = "PROXIMO_DA_FILA"` para `SugestaoRepasseGerada`) — porque nada na spine lhe diz que esses dois eventos legitimamente não têm causa.

Resultado: para 6 dos 7 tipos de evento, o nome do campo diverge (`motivoLiberacao` vs `motivo`) e/ou sua obrigatoriedade diverge (produtor omite, consumidor exige NOT NULL) e/ou o vocabulário é inventado independentemente dos dois lados — sem nenhuma colisão visível até a integração, porque cada `eventType` tem seu próprio JSON Schema versionado isoladamente (Consistency Conventions, linha 135) e a spine nunca exige que os sete schemas concordem entre si sobre esse campo.

**Fix sugerido:** um AD (extensão de AD-7, ou novo) fixando: nome canônico do campo de causa no envelope/payload (ex.: `motivo`, presente em todos os 7 `eventType`s — inclusive com valor fixo tipo `"CONFIRMACAO_PACIENTE"` para os que não têm rejeição, em vez de omitir o campo), e um vocabulário fechado (enum) por tipo de evento, com `motivoLiberacao` do AD-4 explicitamente mapeado como *um dos valores possíveis* do campo canônico para o evento `AgendamentoNaoConfirmado`/`RecusaRegistrada`/liberação — não um campo paralelo e desacoplado.

---

### [medium-high] Máquina de estados da `SugestaoRepasse` (AD-6): promete `409` mas não herda a "escrita condicional" obrigatória de AD-4

**Localização:** AD-4 linha 74 é explícito e enfático: *"Toda transição de estado [do `Agendamento`] é uma escrita condicional (`UPDATE ... WHERE status = <estado_esperado>`) — **nunca** uma leitura-depois-escrita sem guarda"*. AD-6 linha 86 descreve a mesma forma de problema para `SugestaoRepasse` — um segundo agregado com estado (`PENDENTE` implícito → `CONFIRMADA` via `ConfirmarRepasse` ou "resolvida" via `RecusarSugestaoRepasse`) e a mesma garantia de exclusividade exigida ("tentar confirmar uma Sugestão já resolvida retorna `409`") — mas **não repete o mandato do mecanismo**. AD-6 nunca usa as palavras "escrita condicional" nem referencia AD-4 por analogia.

**O par adversarial:** dois devs implementando `ConfirmarRepasse` e `RecusarSugestaoRepasse` em `liberacao-repasse-service`, ambos satisfazendo a letra de AD-6 ("tentar confirmar uma Sugestão já resolvida retorna 409"):

- **Dev A** (leu AD-4 e aplicou por analogia): implementa ambos os comandos como `UPDATE sugestao_repasse SET status = 'CONFIRMADA' WHERE id = ? AND status = 'PENDENTE'`, checa `rowsAffected == 0` → retorna `409`. Corretude sob concorrência garantida pelo banco.
- **Dev B** (implementou literalmente só o que AD-6 pede, sem inventar um mecanismo que a regra não nomeia): implementa `SELECT ... WHERE id = ?`, checa `status == PENDENTE` em código de aplicação, e só então faz `UPDATE`. Sob uma race real — dois Gestores (ou o mesmo Gestor com duplo clique) chamando `ConfirmarRepasse` e `RecusarSugestaoRepasse` quase simultaneamente na mesma `SugestaoRepasse` — ambos os `SELECT`s podem observar `PENDENTE` antes de qualquer `UPDATE` commitar, e **os dois comandos terminam com sucesso**: cria-se um `RepasseConfirmado` **e** avança-se para a próxima Sugestão da fila para a mesma Vaga — violando diretamente o invariante que AD-6 quer proteger ("tentar confirmar uma Sugestão já resolvida retorna 409") sem que nenhuma linha de AD-6 tenha sido tecnicamente desobedecida (o `409` só é prometido "se" a implementação detectar o conflito — e a implementação B, honestamente, não detecta).

Diferente do Agendamento (fechado por AD-4), aqui não há AD nenhum dizendo "use o mesmo padrão". É a assimetria mais concreta da spine: o mesmo tipo de corrida, resolvido explicitamente em um lugar e silenciosamente reaberto no outro.

**Fix sugerido:** estender AD-6 (ou criar um AD-6b curto) com uma frase equivalente à de AD-4: "toda transição de estado de `SugestaoRepasse` é uma escrita condicional (`UPDATE ... WHERE status = 'PENDENTE'`), nunca leitura-depois-escrita — mesma disciplina de AD-4."

---

### [medium] Idempotência de consumidor: AD-7 fecha para `auditoria-service`, mas nem AD-3 nem AD-6 fecham para `liberacao-repasse-service` consumindo `VagaLiberada`

**Localização:** AD-7 linha 92 é explícito: *"Cada registro guarda o `eventId` de origem como chave de deduplicação... reentregas nunca duplicam uma decisão já registrada"*. AD-3 linha 67 só garante dedupe **no transporte** (`MessageDeduplicationId = eventId`, janela padrão SQS FIFO de 5 minutos) — não dedupe **na aplicação** consumidora além dessa janela (ex.: reentrega horas depois via redrive manual de uma DLQ, linha 67: "investigada manualmente"). AD-6 (consumo de `VagaLiberada` por `liberacao-repasse-service` para gerar `SugestaoRepasse`) não menciona `eventId` nem deduplicação nenhuma — diferente de AD-7, que menciona explicitamente.

**O par adversarial:** dois agentes implementando o `sqs-consumer` de `VagaLiberada` em `liberacao-repasse-service` (AD-6):

- **Agente A**, por analogia com AD-7 (mesmo padrão que já viu no design de `auditoria-service`), grava o `eventId` já processado numa tabela de idempotência antes de gerar a `SugestaoRepasse`, e ignora reentregas.
- **Agente B**, seguindo só a letra de AD-6 (que não menciona `eventId` nenhuma vez), implementa o consumo direto: recebe `VagaLiberada`, consulta a Lista de Espera, gera `SugestaoRepasse`. Sob uma reentrega fora da janela de 5 min do SQS FIFO (ex.: mensagem reprocessada após um redrive de DLQ por outro motivo, ou uma segunda task ECS do próprio `liberacao-repasse-service` processando a mesma fila — a spine não diz que há só uma), o Agente B gera uma **segunda** `SugestaoRepasse` para a mesma Vaga já liberada, incompatível com AD-6's garantia implícita de uma sugestão ativa por Vaga.

**Fix sugerido:** estender AD-3 (que já é o lugar certo, sendo o AD de transporte de eventos) com uma frase genérica aplicável a *todo* consumidor, não só `auditoria-service`: "todo consumidor mantém idempotência por `eventId` além da janela de dedupe do SQS FIFO — o padrão de AD-7 é obrigatório para qualquer serviço que consome eventos de domínio, não uma particularidade da auditoria."

---

### [medium] Catálogo de `Recurso` (AD-1): dono canônico definido, mas nenhum comando/endpoint cria a linha canônica — bootstrap fica implícito e sem ordem garantida

**Localização:** AD-1 linha 42 fixa `liberacao-repasse-service` como dono canônico do catálogo `Recurso`, e diz que o `seed-adapter` faz "upsert idempotente tanto de Agendamentos... quanto de entradas de Lista de Espera" (linha 41) — mas nunca menciona upsert de `Recurso` em si. A árvore de diretórios (linha 236-244) lista `application/command/` de `liberacao-repasse-service` como `RegistrarEntradaListaEspera, GerarSugestaoRepasse, ConfirmarRepasse, RecusarSugestaoRepasse` — sem nenhum `CriarRecurso`/`RegistrarRecurso`. `AD-13`, em contraste, é explícito sobre como sua tabela equivalente (usuários sintéticos) é populada: "tabela de usuários sintéticos pré-cadastrados **por migration** (Flyway)".

**O par adversarial:** dois desenvolvedores implementando o bootstrap de dados do MVP:

- **Dev A** lê o silêncio de AD-1 sobre como `Recurso` é criado como "óbvio, é igual ao AD-13": pré-cadastra o catálogo de `Recurso` via migration Flyway do schema `liberacao_repasse`, e o `seed-adapter` nunca cria `Recurso`, só referencia `recursoId`s que já existem.
- **Dev B** lê "o `seed-adapter` faz upsert idempotente" (linha 41) como cobrindo *toda* a carga de dados do domínio, incluindo o catálogo, e implementa um endpoint REST `POST /v1/recursos` (upsert) exposto por `liberacao-repasse-service`, chamado pelo `seed-adapter` **antes** de upsertar Agendamentos/Lista de Espera — sem qualquer AD garantindo essa ordem (o `seed-adapter` roda como job único; nada impede, na ausência de uma sequência definida, uma implementação que tenta criar o `Agendamento` referenciando `recursoId` antes do `Recurso` existir, ou que tenta o rótulo denormalizado antes do catálogo canônico estar populado).

Se A e B coexistirem (ex.: um dev fez `liberacao-repasse-service`, outro fez `seed-adapter`, cada um assumindo que o outro cobre isso), o catálogo simplesmente **não existe** em nenhum dos dois: `seed-adapter` do Dev B tentando `POST /v1/recursos` contra um serviço que nunca expôs esse endpoint (Dev A não o construiu, porque assumiu migration).

**Fix sugerido:** uma frase em AD-1 ou AD-10 fixando explicitamente o mecanismo de bootstrap do catálogo `Recurso` (migration Flyway, análogo a AD-13, é a opção mais consistente com "catálogo estático no MVP" já declarado) e, se o `seed-adapter` depende da existência prévia do catálogo, uma nota de ordem de execução (catálogo antes de Agendamentos/Lista de Espera).

## Summary

- High: 2
- Medium-high: 1
- Medium: 2
- **Total: 5**

Todos os cinco são fecháveis sem redesenho — extensão de uma frase em AD existente (AD-3, AD-6, AD-7) ou uma decisão de bootstrap faltante (AD-1). Nenhum aponta para um problema de paradigma; todos apontam para o mesmo padrão: um mecanismo de fechamento de corrida/idempotência/vocabulário que a spine já aplicou uma vez em um lugar análogo, e não generalizou para o segundo lugar onde o mesmo problema estrutural reaparece.
