---
title: ConfirmaSUS — Confirmação Ativa de Consulta e Exame
status: final
created: 2026-09-16
updated: 2026-09-16
---

# PRD: ConfirmaSUS — Confirmação Ativa de Consulta e Exame

## 0. Document Purpose

Este PRD traduz o Product Brief (`_bmad-output/planning-artifacts/briefs/brief-Fase5-2026-09-16/brief.md` + `addendum.md`) em requisitos funcionais e não-funcionais estáveis para as próximas fases BMAD (`bmad-architecture` → `bmad-create-epics-and-stories` → `bmad-build`). Contexto: entrega individual do Hackathon FIAP Pós-Tech — Arquitetura e Desenvolvimento Java, Fase 5 (Thiago Henrique Alves Ferreira, RM369442, turma 11ADJT), avaliada por banca segundo critérios de edital (Problema/Impacto 20%, Inovação 20%, Funcionalidade do MVP 30%, Apresentação 20%, Documentação 10% — ver §9).

Este documento **substitui** o PRD anterior (`prd-Fase5-2026-09-05`, FilaJusta — matching automático com score de prioridade clínica), abandonado após a descoberta de que legislação vigente proíbe que um sistema automatizado decida triagem ou priorização clínica. O PRD anterior é contexto histórico, não base a estender. Boa parte da infraestrutura construída naquela fase é reaproveitada aqui (ver §8); o domínio de negócio — o que o sistema decide e por quê — é novo.

O documento é organizado por Glossário → Features (com FRs aninhados e numerados globalmente) → NFRs/Constraints. Decisões técnicas de implementação (stack, padrão arquitetural) permanecem no `addendum.md` do brief e são insumo direto para `bmad-architecture`, não duplicadas aqui.

## 1. Vision

O absenteísmo em consultas e exames é uma das formas mais silenciosas de desperdício no SUS: entre 25% e 38,6% das consultas e exames especializados são perdidos por falta do paciente, sem aviso e sem tempo de repassar a vaga a quem está esperando (Beltrame et al., 2019/2020). Um único levantamento regional (Espírito Santo, 2014-2016) estimou R$ 18,5 milhões desperdiçados em três anos só nesse tipo de ociosidade.

O ConfirmaSUS é o motor de backend que fecha esse ciclo: notifica o paciente sobre seu agendamento, exige confirmação ativa dentro de um prazo e — se ela não vier — libera a vaga e sugere automaticamente o próximo paciente da lista de espera daquele recurso, por ordem de chegada da solicitação. Para ser honesto sobre os limites: o mecanismo de lembrete isoladamente não é novidade — o Ceará já reduziu absenteísmo em ~19% em 2025 com lembretes via WhatsApp, e revisões internacionais (Cochrane, sobre SMS) documentam ganhos de comparecimento na mesma faixa. O que essas iniciativas deixam em aberto, e o que o ConfirmaSUS entrega, é fechar o ciclo — transformar a vaga detectada como ociosa em uma oferta ativa e auditável para quem está esperando, sem nunca decidir sozinho quem deve ser priorizado. Toda decisão sobre repassar ou não uma vaga liberada é humana; o sistema só notifica, aguarda, sugere e registra.

Para este MVP de hackathon, a vitória é demonstrar o ciclo ponta a ponta — agendamento sintético → notificação → confirmação/recusa → liberação → sugestão de repasse → decisão humana → log auditável — como uma API funcional sobre dados sintéticos, reaproveitando quase integralmente a infraestrutura de autenticação, gateway e arquitetura orientada a eventos já construída na fase anterior do mesmo projeto. Além do hackathon, os passos naturais já identificados no brief são: reintroduzir o fluxo de agendamento (hoje fora de escopo — ver §5), trocar a notificação mockada por integração real com um canal externo (o precedente do Ceará mostra o caminho), e generalizar o padrão "sugestão + confirmação humana + log auditável" para outros recursos do SUS (leitos, equipamentos).

## 2. Target User

### 2.1 Jobs To Be Done

- **Paciente com consulta/exame marcado**: não perder a vaga por esquecimento, e se não puder comparecer, ter como avisar a tempo de ajudar outra pessoa.
- **Paciente em Lista de Espera**: ter uma chance real de ser chamado quando uma vaga é liberada, em vez de ela simplesmente ficar ociosa.
- **Gestor de Agenda / regulador**: ser avisado de Vagas Liberadas com um candidato já sugerido, sem depender de descobrir a ausência por acaso, e decidir o repasse com um clique.
- **Auditor / órgão de controle**: justificar, com dados, por que e quando uma Vaga Liberada foi repassada a determinado paciente.

### 2.2 Non-Users (v1)

- Pacientes não têm interface direta neste MVP (sem app/portal) — a "notificação" e a "confirmação via API" simulam, respectivamente, um canal externo (SMS/WhatsApp/e-mail, mockado) e a ação do paciente nesse canal.
- Unidades de saúde que fariam o agendamento original não são atores deste MVP — o Agendamento em si é dado de entrada sintético (seed), não um fluxo construído aqui (ver Non-Goals).

### 2.3 Key User Journeys

- **UJ-1. Paciente confirma presença e evita perder a vaga.**
  - **Persona + contexto:** Marina, paciente com exame de imagem marcado para daqui a três dias, que já esqueceu compromissos médicos antes.
  - **Entry state:** Agendamento já existe no sistema (seed); Marina ainda não foi notificada.
  - **Path:** Sistema publica notificação de confirmação dentro da Janela de Confirmação → Marina responde `POST` confirmando presença via API (simulando resposta a SMS/WhatsApp).
  - **Climax:** A confirmação é registrada antes do prazo, sem qualquer intervenção manual de um atendente.
  - **Resolution:** A vaga permanece com Marina; nada é liberado. Realiza FR-3, FR-4.

- **UJ-2. Paciente não responde e sua vaga chega a quem estava esperando.**
  - **Persona + contexto:** João, paciente com consulta marcada, que teve uma emergência de trabalho e nem viu a notificação.
  - **Entry state:** Notificação de confirmação foi enviada; Janela de Confirmação está correndo.
  - **Path:** Janela expira sem resposta → sistema marca como Não Confirmado e libera a vaga → sistema consulta a Lista de Espera daquele recurso e sugere automaticamente o próximo paciente por ordem de chegada da solicitação.
  - **Climax:** A vaga não fica "perdida" silenciosamente — uma sugestão de repasse já está pronta para o Gestor de Agenda no mesmo instante em que a vaga é liberada.
  - **Resolution:** Vaga ociosa vira uma oportunidade real para outro paciente, sem que ninguém precise notar a ausência manualmente. Realiza FR-6, FR-7, FR-8, FR-9.

- **UJ-3. Gestor de Agenda decide o repasse sem telefonema.**
  - **Persona + contexto:** Carla, gestora de agenda de uma UBS, historicamente dependente de ligações para saber quem faltou e quem chamar em seguida.
  - **Entry state:** Autenticado, consulta a API a partir do seu posto de trabalho.
  - **Path:** Consulta Vagas Liberadas com sugestão pendente → revisa o candidato sugerido pela Lista de Espera → confirma ou recusa a sugestão.
  - **Climax:** Recebe uma sugestão pronta (qual paciente, por ordem de chegada de qual solicitação) em vez de descobrir a vaga vazia "no escuro".
  - **Resolution:** A vaga é repassada ao paciente correto e a decisão fica registrada. Realiza FR-9, FR-10, FR-11.

- **UJ-4. Auditor investiga uma reclamação sobre repasse de vaga.**
  - **Persona + contexto:** Renato, auditor de um órgão de controle, recebe uma reclamação de que a vaga de um paciente foi "dada" a outro sem aviso.
  - **Entry state:** Autenticado, sem conhecimento prévio do caso.
  - **Path:** Consulta o log auditável do agendamento reclamado → vê a linha do tempo (notificação enviada, prazo, não-confirmação, liberação, sugestão, decisão do gestor) → confirma que o paciente reclamante não confirmou dentro do prazo.
  - **Climax:** Cada etapa tem timestamp e motivo explícitos — a resposta é defensável com dados, não "confie em nós".
  - **Resolution:** Reclamação respondida com evidência auditável, ou identificado um caso real de erro (ex.: notificação nunca enviada) a corrigir. Realiza FR-12, FR-13.

## 3. Glossário

- **Paciente** — pessoa com um Agendamento, identificada externamente por CPF (sintético, no MVP) apenas na fronteira de ingestão e internamente por um ID de Paciente gerado pelo sistema (ver §8 Constraints/LGPD).
- **Agendamento** — consulta ou exame já marcado para um Paciente em um Recurso (unidade + especialidade/tipo), carregado via seed sintético (FR-1). Marcar o Agendamento em si **não** é uma capacidade deste sistema (ver Non-Goals) — ele entra pronto.
- **Janela de Confirmação** — período antes do Agendamento durante o qual o Paciente pode confirmar ou recusar presença. `[ASSUMPTION: 48h antes do Agendamento — ver §11]`
- **Confirmação** — ação explícita do Paciente informando que comparecerá.
- **Recusa** — ação explícita do Paciente informando que não comparecerá, dita antes do fim da Janela de Confirmação.
- **Não Confirmado** — estado atribuído automaticamente pelo sistema quando a Janela de Confirmação expira sem Confirmação nem Recusa. Equivalente à Recusa para efeito de liberação da vaga (FR-7), mas registrado com causa distinta no Log Auditável.
- **Vaga Liberada** — o Agendamento cuja ocupação deixou de estar garantida (por Recusa ou por ficar Não Confirmado), disponível para repasse.
- **Lista de Espera** — fila de Pacientes aguardando aquele tipo de Recurso, ordenada exclusivamente por ordem de chegada da solicitação — nunca por critério clínico ou de gravidade.
- **Sugestão de Repasse** — recomendação, gerada automaticamente pelo sistema, de qual Paciente da Lista de Espera deveria ocupar uma Vaga Liberada. Ainda não é uma decisão definitiva.
- **Repasse Confirmado** — atribuição definitiva da Vaga Liberada a um Paciente da Lista de Espera, criada quando um Gestor de Agenda confirma uma Sugestão de Repasse (FR-10). Só existe Repasse Confirmado após essa confirmação.
- **Log Auditável** — registro explicável de toda notificação, Confirmação, Recusa, expiração, liberação, Sugestão de Repasse e decisão de repasse, incluindo motivo e timestamp.
- **Gestor de Agenda** — usuário direto da API responsável por decidir o repasse de uma Vaga Liberada.
- **Auditor** — usuário (direto ou por meio de um atendente) que consulta o Log Auditável para justificar decisões passadas.

**Nota de escopo:** este sistema **não** calcula nenhum "Score de Prioridade Clínica" nem faz "Matching automático" por gravidade — esses conceitos pertenciam ao produto anterior (FilaJusta) e estão permanentemente fora de escopo aqui, por restrição legal (ver §8).

## 4. Features

### 4.1 Agendamento Sintético e Identificação do Paciente

**Descrição:** Ponto de entrada de dados via seed sintético, simulando agendamentos já existentes em sistemas oficiais (SISREG/DATASUS) sem exigir integração real.

#### FR-1: Carga de Agendamentos Sintéticos

O sistema carrega, via seed, um conjunto de Agendamentos sintéticos (Paciente por CPF, Recurso/especialidade, unidade, data/hora do Agendamento), simulando uma Camada Adaptadora sobre sistemas oficiais do SUS.

**Consequences (testable):**
- Cada Agendamento carregado tem Paciente, Recurso e horário definidos antes de qualquer notificação ser possível.
- O seed inclui deliberadamente cenários de Vaga Liberada com Lista de Espera não vazia, para que UJ-2/UJ-3 sejam demonstráveis. `[ASSUMPTION: composição-alvo numérica do seed fica para bmad-architecture, análoga ao FR-10 do PRD anterior]`

#### FR-2: Resolução de Paciente por CPF

O CPF recebido na carga do Agendamento é resolvido para um ID de Paciente interno, gerado ou reaproveitado pelo sistema.

**Consequences (testable):**
- A partir da criação do Agendamento, todo evento e consulta interna referencia o ID de Paciente — nunca o CPF (ver §8 Constraints/LGPD).
- CPF com formato/checksum inválido causa rejeição do registro de seed correspondente.

### 4.2 Notificação e Confirmação de Presença

**Descrição:** O ciclo central do produto — pedir confirmação ativa ao Paciente e tratar a ausência de resposta como um sinal, não como um silêncio. Realiza UJ-1, UJ-2.

#### FR-3: Notificação de Confirmação

Ao entrar na Janela de Confirmação de um Agendamento, o sistema publica uma notificação pedindo a confirmação de presença do Paciente (mock de canal externo: a notificação é registrada e logada, não enviada de fato por SMS/WhatsApp/e-mail).

**Consequences (testable):**
- A notificação é publicada exatamente uma vez por Agendamento por abertura de Janela de Confirmação (idempotência sob reprocessamento).
- O envio (mockado) fica registrado no Log Auditável com timestamp (FR-12).

**Out of Scope:** Integração real com provedor de SMS/WhatsApp/e-mail (ver Non-Goals).

#### FR-4: Confirmação de Presença

Um Paciente confirma presença em um Agendamento dentro da Janela de Confirmação, via API.

**Consequences (testable):**
- Confirmação registrada impede a liberação da vaga por essa Janela de Confirmação, mesmo que o prazo expire depois.
- Confirmação fora da Janela (antes de aberta, ou depois de expirada e já liberada) retorna erro explicando o motivo, em vez de aceitar silenciosamente.
- Confirmação duplicada para o mesmo Agendamento é idempotente (não gera dois registros de decisão).

#### FR-5: Recusa Ativa

Um Paciente recusa presença em um Agendamento dentro da Janela de Confirmação, via API.

**Consequences (testable):**
- Recusa dispara a liberação da vaga (FR-7) imediatamente, sem esperar o fim da Janela de Confirmação.
- Recusa após já confirmado exige uma capability explícita de "desfazer confirmação" — `[ASSUMPTION: fora de escopo do MVP; uma vez confirmado, o Paciente não pode recusar depois — ver Non-Goals]`.

#### FR-6: Expiração por Não-Resposta

Se a Janela de Confirmação expira sem Confirmação nem Recusa, o sistema marca o Agendamento como Não Confirmado automaticamente.

**Consequences (testable):**
- A transição para Não Confirmado ocorre sem intervenção manual, mesmo que nenhum operador esteja olhando.
- Não Confirmado dispara a liberação da vaga (FR-7), com causa distinta de Recusa no Log Auditável (FR-12).

### 4.3 Liberação e Repasse de Vaga

**Descrição:** O que acontece com uma vaga que deixou de estar garantida — sempre com decisão humana no repasse. Realiza UJ-2, UJ-3.

#### FR-7: Liberação da Vaga

Um Agendamento com Recusa ou Não Confirmado tem sua Vaga marcada como Liberada.

**Consequences (testable):**
- Vaga Liberada não pode ser "reconfirmada" pelo Paciente original (ela já não é mais dele — ver FR-4/FR-5 sobre janelas fechadas).
- A liberação é o evento que dispara a consulta à Lista de Espera (FR-8) e a geração da Sugestão de Repasse (FR-9).

#### FR-8: Consulta da Lista de Espera

Qualquer usuário autenticado pode consultar a Lista de Espera de um tipo de Recurso, ordenada exclusivamente por ordem de chegada da solicitação.

**Consequences (testable):**
- A ordenação nunca reflete gravidade, especialidade percebida como mais urgente, ou qualquer proxy de julgamento clínico — apenas timestamp de entrada na lista.
- Lista vazia retorna explicitamente "sem candidatos", nunca um erro.

#### FR-9: Sugestão de Repasse

Ao liberar uma vaga (FR-7), o sistema gera automaticamente uma Sugestão de Repasse apontando o próximo Paciente da Lista de Espera daquele Recurso.

**Consequences (testable):**
- A geração da Sugestão de Repasse publica um evento que notifica o Gestor de Agenda responsável por aquele Recurso, usando o mesmo mecanismo de notificação mockada do FR-3 (registrado e logado, não enviado por canal externo real). Assim, o Gestor não depende de consultar a API periodicamente para saber que há uma sugestão pendente — a consulta sob demanda (FR-8) continua existindo, como complemento a esta notificação.
- A Sugestão de Repasse nunca cria, por si só, um Repasse Confirmado — é sempre necessário FR-10.
- Se a Lista de Espera está vazia no momento da liberação, a Vaga fica marcada como Liberada sem sugestão pendente, revisitável quando alguém entrar na Lista depois. `[ASSUMPTION: sem repescagem automática retroativa no MVP]`

#### FR-10: Confirmação do Repasse

Um Gestor de Agenda confirma uma Sugestão de Repasse, criando um Repasse Confirmado.

**Consequences (testable):**
- Repasse Confirmado é definitivo: a Vaga deixa de aparecer como Liberada.
- Tentar confirmar uma Sugestão de Repasse já resolvida (confirmada ou recusada por outro Gestor) retorna erro de conflito, não sobrescreve silenciosamente.

#### FR-11: Recusa do Repasse

Um Gestor de Agenda recusa uma Sugestão de Repasse; o sistema gera uma nova Sugestão de Repasse para o próximo Paciente da Lista de Espera, pulando o(s) recusado(s).

**Consequences (testable):**
- Paciente cuja Sugestão foi recusada não volta a ser sugerido para a mesma Vaga Liberada.
- Se não houver mais candidatos na Lista de Espera após a recusa, a Vaga permanece Liberada sem sugestão pendente (mesma consequência do FR-9).

### 4.4 Auditoria

**Descrição:** O diferencial herdado do produto anterior, adaptado a um domínio administrativo em vez de clínico. Realiza UJ-4.

#### FR-12: Registro em Log Auditável

Toda notificação enviada, Confirmação, Recusa, transição para Não Confirmado, Liberação, Sugestão de Repasse e decisão de repasse (confirmação ou recusa) é registrada no Log Auditável com timestamp e motivo.

**Consequences (testable):**
- Cada entrada do Log Auditável é imutável após criada (append-only).
- Nenhuma decisão listada acima ocorre sem gerar uma entrada correspondente — a ausência de entrada para uma decisão é, por definição, um bug.

#### FR-13: Consulta de Auditoria

Um Auditor pode consultar o histórico completo do Log Auditável de um Paciente ou de um Agendamento específico.

**Consequences (testable):**
- A consulta retorna a linha do tempo completa (todas as entradas do FR-12 relacionadas), ordenada cronologicamente.
- Paciente ou Agendamento sem nenhum evento retorna explicitamente "sem histórico", nunca um erro.

### 4.5 Autenticação

**Descrição:** Reaproveitada sem alteração da fase anterior do projeto.

#### FR-14: Autenticação via auth-service/gateway-service

Um usuário sintético pré-cadastrado (representando Paciente/canal de resposta, Gestor de Agenda ou Auditor) autentica-se via `POST /v1/auth/login` contra o `auth-service` já implementado, recebendo um JWT (HS256) validado pelo `gateway-service` — único ponto de entrada do sistema.

**Consequences (testable):**
- Requisições sem token válido para endpoints protegidos retornam `401` (comportamento já implementado e testado — não há trabalho novo aqui).
- Não há distinção de papéis (RBAC) tecnicamente aplicada nesta fase — qualquer token válido acessa qualquer endpoint, igual ao precedente da fase anterior. `[ASSUMPTION herdada do PRD anterior]`

**Out of Scope:** Qualquer alteração ao `auth-service`/`gateway-service` — são reaproveitados como estão.

## 5. Non-Goals (Explicit)

- Fluxo de marcar o Agendamento em si (Paciente escolher unidade/data/horário) — o MVP assume Agendamento pré-existente via seed. Decisão do usuário, 2026-09-16.
- Qualquer score ou decisão clínica automatizada, e qualquer critério de ordenação que não seja ordem de chegada da solicitação — permanentemente fora de escopo, por restrição legal.
- Repasse de vaga automático sem confirmação humana explícita (FR-10) — o sistema nunca decide sozinho quem recebe a vaga.
- Integração real com canais de notificação (SMS/WhatsApp/e-mail) — a notificação é sempre mockada nesta fase (FR-3).
- Interoperabilidade real com SISREG, SIH/SUS, e-SUS APS, DATASUS ou qualquer sistema oficial do SUS.
- Frontend / interface de usuário de qualquer tipo — entrega backend-only, demonstrável via Swagger/Postman.
- "Desfazer" uma Confirmação já registrada (ver FR-5) ou uma Recusa já registrada.
- Repescagem automática retroativa quando um Paciente entra na Lista de Espera depois de uma Vaga já ter ficado Liberada sem sugestão (ver FR-9).
- Autenticação e autorização completas de nível produção (RBAC aplicado, SSO, OAuth real) — o que já existe (auth-service com JWT real) é suficiente e não será estendido nesta fase.
- Conformidade legal plena com a LGPD — mesma postura honesta do PRD anterior (ver §8).

## 6. MVP Scope

### 6.1 In Scope

- Ciclo único ponta a ponta: Agendamento sintético → Notificação → Confirmação/Recusa/Expiração → Liberação → Sugestão de Repasse → confirmação/recusa humana → Log Auditável (FR-1 a FR-13).
- Autenticação real via `auth-service`/`gateway-service` já implementados, reaproveitados sem alteração (FR-14).
- CPF como chave de identificação do Paciente na fronteira de ingestão; dados de Agendamento mockados via seed sintético.
- Reaproveitamento da arquitetura orientada a eventos já construída (outbox + relay SNS + SQS + DLQ) para propagar notificações e decisões.
- Persistência simples, com dataset pequeno o suficiente para a demonstração em vídeo rodar sem falhas.
- Empacotamento reproduzível — subir o sistema inteiro com um único comando (herdado, `infra-cdk`).
- Demonstração via Swagger/coleção Postman.

### 6.2 Out of Scope for MVP

- Tudo listado em §5 Non-Goals.

## 7. Cross-Cutting NFRs

- **Continuidade sob falha parcial:** o fluxo de Confirmação/Recusa continua aceitando respostas do Paciente mesmo se o serviço de Log Auditável estiver temporariamente indisponível — a propagação para auditoria (FR-12) é processada quando ele voltar, não perdida.
- **Observabilidade mínima:** logs estruturados e health-check por serviço, com `X-Correlation-Id` propagado ponta a ponta (herdado do `gateway-service`).
- **Reprodutibilidade:** todo o sistema (serviços + seed de dados) sobe com um único comando, sem passos manuais.
- **Contratos versionados:** comunicação entre serviços usa contratos versionados — versionamento de schema nos eventos publicados via SNS/SQS e versionamento de path nos endpoints REST — evitando quebras silenciosas entre notificação, repasse e auditoria.
- **Testes automatizados:** cobertura de linha ≥90% (JaCoCo) na camada de domínio de cada microsserviço, complementada por teste de mutação (PIT) na mesma camada, testes de integração cobrindo os contratos entre serviços, e testes de aceitação em BDD (Gherkin/Cucumber-JVM) cobrindo o ciclo único ponta a ponta descrito em §6.1 — mesma decisão firme herdada da fase anterior, por melhor prática de mercado.
- **Segredos fora do código:** credenciais e segredos de configuração não ficam versionados no repositório (herdado).

## 8. Constraints and Guardrails

**Legal (motivo do pivô — a restrição mais importante deste documento)**
- O sistema **nunca** calcula, sugere ou aplica um critério de gravidade/prioridade clínica para decidir quem recebe uma Vaga Liberada. A Lista de Espera é ordenada exclusivamente por ordem de chegada da solicitação (FR-8). Qualquer feature futura que reintroduza, ainda que indiretamente, um proxy de julgamento clínico automatizado está fora de escopo permanentemente, não apenas para este MVP.
- Toda decisão de repasse de vaga é humana e explícita (FR-10/FR-11) — o sistema nunca finaliza um repasse sozinho, mesmo quando a Sugestão de Repasse é inequívoca.

**Reaproveitamento de infraestrutura (herdado da fase anterior)**
- `auth-service`, `gateway-service` e `infra-cdk` são reaproveitados sem alteração (ver FR-14, §6.1).
- O padrão outbox + relay SNS + SQS + DLQ, hoje usado para propagar score e alocação no domínio anterior, é adaptado para propagar notificações de confirmação e decisões de repasse — mesma arquitetura, novo conteúdo de evento.
- O padrão de confirmação/recusa de sugestão já implementado e testado (`ConfirmarAlocacao`/`RecusarSugestao` do domínio anterior) é o candidato natural de reaproveitamento para FR-10/FR-11 — decisão concreta de reuso de código fica para `bmad-architecture`.
- O `triagem-score-service` (cálculo de score de prioridade clínica) não tem função neste produto — nenhuma FR deste documento depende dele. Seu descomissionamento, arquivamento ou reaproveitamento de infraestrutura (ex.: seu banco/schema) fica para `bmad-architecture`.

**Cost**
- O projeto é pago do próprio bolso do aluno — toda decisão de infraestrutura deve privilegiar baixo custo e fácil desligamento (Free Tier onde possível, recursos pausáveis/destruíveis fora da janela de demo), mantendo o padrão `deploy/pause/destroy` já validado.

**Privacy / Dados (LGPD by design)**
- Todos os dados de Pacientes e Agendamentos usados no MVP são sintéticos — nenhum dado real circula em nenhum ambiente.
- O CPF é tratado como dado pessoal identificador e **não transita** além da fronteira de ingestão (FR-2): é usado somente na resolução do Paciente, e imediatamente convertido em ID interno. Notificação (FR-3), Log Auditável (FR-12) e qualquer evento interno referenciam exclusivamente esse ID.
- **Nota diferente do PRD anterior:** o dado tratado aqui (agendamento, confirmação/recusa) não é, por si só, dado clínico sensível (não há sintomas, diagnóstico ou gravidade) — mas o tipo de Recurso/especialidade do Agendamento pode indiretamente revelar uma condição de saúde (ex.: "oncologia"). Por isso, o mesmo princípio de minimização do PRD anterior se aplica por boa prática, ainda que a base legal seja mais fraca que a de um dado clínico direto.
- **Escopo honesto sobre LGPD:** como todo o dataset é sintético, não há titular de dados real e a LGPD provavelmente não se aplica a este MVP em sentido estrito. Conformidade legal plena fica fora do escopo deste MVP.

## 9. Success Metrics

*Nota — critérios de avaliação do edital (contexto acadêmico, não métricas de produto): Problema/Impacto 20%, Inovação 20% (fechar o ciclo de ociosidade com repasse auditável, não o lembrete em si), Funcionalidade do MVP 30% (ciclo ponta a ponta confiável via Swagger/Postman), Apresentação 20%, Documentação 10%. As Success Metrics abaixo validam a Funcionalidade do MVP e a Inovação.*

**Primary**
- **SM-1**: Liberação de vaga e geração da Sugestão de Repasse ocorrem automaticamente ao expirar a Janela de Confirmação ou ao registrar uma Recusa, sem exigir reprocessamento manual. Validates FR-6, FR-7, FR-9.
- **SM-2**: 100% das decisões (notificação, Confirmação/Recusa, liberação, Sugestão de Repasse, confirmação/recusa do repasse) registradas no Log Auditável são explicáveis (motivo e timestamp consultáveis) nos casos do dataset de demonstração. Validates FR-12, FR-13.

**Secondary**
- **SM-3**: Nenhuma Vaga Liberada no dataset de demonstração fica sem oferta de repasse pendente quando a Lista de Espera correspondente não está vazia. Validates FR-9.

**Counter-metrics (do not optimize)**
- **SM-C1**: A ordem da Lista de Espera não deve, em nenhum cenário de demo, ser alterada por um fator que pareça clínico ou de gravidade — apenas ordem de chegada da solicitação (FR-8). Guardrail contra reintrodução implícita de um proxy de score. Counterbalances toda a inovação deste produto.

`[ASSUMPTION: valores acima herdados do brief como ilustrativos; a composição-alvo do dataset de demo (§4.1 FR-1) fica para bmad-architecture]`

## 10. Open Questions

Nenhuma pendência bloqueante para esta fase. Decisões técnicas conscientemente deferidas para `bmad-architecture` — ver "Assumptions Abertas" em §11.

## 11. Assumptions Index

### Decisões Fechadas (log de rastreabilidade)

*Já confirmadas nesta fase — listadas aqui apenas para rastreabilidade, não são pendências.*

- §0/§8 — Pivô motivado por restrição legal (proibição de decisão automatizada de triagem/priorização clínica); PRD anterior tratado como contexto histórico, não estendido. Entre as direções cogitadas na sessão de brainstorming, a escolhida (ConfirmaSUS) foi a que minimiza risco legal residual, não a que maximiza reaproveitamento de código — a alternativa descartada mais próxima reaproveitaria ~100% do domínio anterior, mas dependia de uma leitura mais permissiva da legislação.
- §5 — Fluxo de marcar o Agendamento (agendamento em si) fora de escopo do MVP; sistema assume Agendamento pré-existente via seed. Decisão do usuário, 2026-09-16.
- §4.3 FR-10/FR-11 — Repasse de vaga exige confirmação humana explícita, nunca automático — mesmo padrão do PRD anterior (lá para Alocação, aqui para Repasse), agora motivado por exigência legal e não só por design.
- §4.5 FR-14 — Autenticação real via `auth-service`/`gateway-service` já implementados é reaproveitada sem alteração; nenhuma mudança de escopo de autenticação nesta fase.
- §7 NFRs — Barra de testes (≥90% JaCoCo domínio + PIT + integração + BDD) mantida igual ao precedente da fase anterior, por ser decisão de melhor prática de mercado independente do domínio.

### Assumptions Abertas (a confirmar em `bmad-architecture`)

- §4.2 FR-5 — "Desfazer" uma Confirmação já registrada para então Recusar está fora de escopo do MVP (uma vez confirmado, o Paciente não pode recusar depois); revisitável se a demo precisar desse cenário.
- §3/§4.2 FR-3 — Duração exata da Janela de Confirmação (assumida 48h antes do Agendamento, inspirada no padrão do Ceará) não fechada numericamente.
- §4.1 FR-1 — Composição-alvo numérica do seed de demonstração (quantos Agendamentos, quantos com Vaga Liberada + Lista de Espera não vazia) não definida.
- §4.3 FR-9 — Quantidade de candidatos sugeridos por vez (um por vez vs. lista curta de candidatos) não definida — assumido um por vez, análogo ao padrão de Sugestão de Matching do domínio anterior.
- §8 — Decisão concreta sobre reaproveitar literalmente o código de `ConfirmarAlocacao`/`RecusarSugestao` (renomeando o domínio) vs. escrever um novo serviço equivalente do zero não fechada — é uma decisão de arquitetura, não de produto.
- §8 — Destino do `triagem-score-service` (descomissionado, arquivado, ou reaproveitada só a infraestrutura) não fechado.
- §4.5 FR-14 — Se a banca exigir isolamento por papel (RBAC) entre Gestor de Agenda e canal de resposta do Paciente, essa postura é revisitável — mesma ressalva do PRD anterior.
