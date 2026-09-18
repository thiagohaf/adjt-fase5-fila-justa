---
id: SPEC-confirmasus
companions:
  - glossary.md
  - user-journeys.md
  - ../../planning-artifacts/architecture/architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md
sources:
  - ../../planning-artifacts/prds/prd-Fase5-2026-09-16/prd.md
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# ConfirmaSUS — Confirmação Ativa de Consulta e Exame

## Why

O absenteísmo em consultas e exames do SUS (25%–38,6%, conforme Beltrame et al. 2019/2020) desperdiça vagas sem aviso e sem tempo de repassá-las a quem está na Lista de Espera — uma perda estimada em R$ 18,5 milhões em três anos só no Espírito Santo (2014–2016). Este é ao mesmo tempo **um mandato** e **uma visão**: o PRD anterior (FilaJusta) foi descontinuado porque a legislação vigente proíbe que um sistema automatizado decida triagem ou priorização clínica, forçando um pivô de produto; o ConfirmaSUS é a visão que nasce desse pivô — fechar o ciclo de ociosidade (notificação → confirmação ativa → liberação → sugestão de repasse → decisão humana → log auditável) sem nunca decidir sozinho quem deve ser priorizado. Afeta diretamente o Paciente com Agendamento, o Paciente em Lista de Espera, o Gestor de Agenda e o Auditor (ver `user-journeys.md`). Contexto imediato: entrega individual do Hackathon FIAP Pós-Tech Fase 5, avaliada por banca acadêmica — a demonstração do ciclo ponta a ponta via API é o que prova o produto.

## Capabilities

- **CAP-1**
  - **intent:** O sistema carrega, via seed sintético, Agendamentos (Paciente por CPF, Recurso, unidade, data/hora), simulando uma camada adaptadora sobre sistemas oficiais do SUS.
  - **success:** Todo Agendamento carregado tem Paciente/Recurso/horário definidos antes de qualquer notificação; o seed inclui cenários de Vaga Liberada com Lista de Espera não vazia (demonstra UJ-2/UJ-3).

- **CAP-2**
  - **intent:** O CPF recebido na carga do Agendamento é resolvido para um ID de Paciente interno, gerado ou reaproveitado.
  - **success:** Todo evento e consulta interna referencia apenas o ID de Paciente, nunca o CPF; CPF com formato/checksum inválido rejeita o registro de seed correspondente.

- **CAP-3**
  - **intent:** Ao entrar na Janela de Confirmação de um Agendamento, o sistema publica (mock) uma notificação pedindo a confirmação de presença do Paciente.
  - **success:** A notificação é publicada exatamente uma vez por Agendamento por abertura de Janela (idempotente sob reprocessamento); o envio mockado fica registrado no Log Auditável com timestamp.

- **CAP-4**
  - **intent:** Um Paciente confirma presença em um Agendamento dentro da Janela de Confirmação, via API.
  - **success:** Confirmação impede a liberação da vaga mesmo que o prazo expire depois; confirmação fora da Janela retorna erro explicando o motivo; confirmação duplicada é idempotente (sem registro duplicado).

- **CAP-5**
  - **intent:** Um Paciente recusa presença em um Agendamento dentro da Janela de Confirmação, via API.
  - **success:** Recusa dispara a liberação da vaga (CAP-7) imediatamente, sem esperar o fim da Janela de Confirmação.

- **CAP-6**
  - **intent:** Se a Janela de Confirmação expira sem Confirmação nem Recusa, o sistema marca o Agendamento como Não Confirmado automaticamente.
  - **success:** A transição ocorre sem intervenção manual; Não Confirmado dispara a liberação da vaga (CAP-7) com causa distinta de Recusa no Log Auditável.

- **CAP-7**
  - **intent:** Um Agendamento com Recusa ou Não Confirmado tem sua vaga marcada como Liberada.
  - **success:** Vaga Liberada não pode ser reconfirmada pelo Paciente original; a liberação dispara a consulta à Lista de Espera (CAP-8) e a geração da Sugestão de Repasse (CAP-9).

- **CAP-8**
  - **intent:** Qualquer usuário autenticado pode consultar a Lista de Espera de um tipo de Recurso, ordenada exclusivamente por ordem de chegada da solicitação.
  - **success:** A ordenação nunca reflete gravidade, especialidade percebida como mais urgente, ou qualquer proxy de julgamento clínico; lista vazia retorna "sem candidatos" explícito, nunca um erro.

- **CAP-9**
  - **intent:** Ao liberar uma vaga (CAP-7), o sistema gera automaticamente uma Sugestão de Repasse apontando o próximo Paciente da Lista de Espera daquele Recurso.
  - **success:** A geração publica uma notificação (mock) ao Gestor de Agenda; a Sugestão nunca cria, por si só, um Repasse Confirmado (exige CAP-10); se a Lista de Espera está vazia, a vaga fica Liberada sem sugestão pendente.

- **CAP-10**
  - **intent:** Um Gestor de Agenda confirma uma Sugestão de Repasse, criando um Repasse Confirmado.
  - **success:** Repasse Confirmado é definitivo (a vaga deixa de aparecer como Liberada); confirmar uma Sugestão já resolvida (por outro Gestor) retorna erro de conflito, nunca sobrescreve silenciosamente.

- **CAP-11**
  - **intent:** Um Gestor de Agenda recusa uma Sugestão de Repasse; o sistema gera uma nova Sugestão para o próximo Paciente da Lista de Espera, pulando os recusados.
  - **success:** Paciente cuja Sugestão foi recusada não volta a ser sugerido para a mesma Vaga Liberada; sem mais candidatos, a vaga permanece Liberada sem sugestão pendente.

- **CAP-12**
  - **intent:** Toda notificação enviada, Confirmação, Recusa, transição para Não Confirmado, Liberação, Sugestão de Repasse e decisão de repasse é registrada no Log Auditável com timestamp e motivo.
  - **success:** Cada entrada do Log Auditável é imutável após criada (append-only); a ausência de entrada para uma decisão listada é, por definição, um bug.

- **CAP-13**
  - **intent:** Um Auditor consulta o histórico completo do Log Auditável de um Paciente ou de um Agendamento específico.
  - **success:** A consulta retorna a linha do tempo completa ordenada cronologicamente; Paciente ou Agendamento sem nenhum evento retorna "sem histórico" explícito, nunca um erro.

- **CAP-14**
  - **intent:** Um usuário sintético pré-cadastrado autentica-se via `auth-service`, recebendo um JWT validado pelo `gateway-service` — único ponto de entrada do sistema.
  - **success:** Requisição sem token válido para endpoint protegido retorna `401`; sem RBAC aplicado nesta fase (qualquer token válido acessa qualquer endpoint).

## Constraints

- Lista de Espera ordenada exclusivamente por ordem de chegada da solicitação — nunca critério clínico ou de gravidade; guardrail legal permanente (motivo do pivô do produto), mesmo indiretamente.
- Todo repasse de vaga exige confirmação humana explícita (CAP-10/CAP-11) — o sistema nunca finaliza um repasse sozinho, mesmo quando a Sugestão é inequívoca.
- `auth-service`, `gateway-service` e `infra-cdk` são reaproveitados sem alteração de comportamento de aplicação.
- O padrão outbox + relay SNS FIFO + SQS FIFO + DLQ (arquitetura orientada a eventos já construída) é reaproveitado para propagar notificações e decisões — não é um novo mecanismo de propagação.
- CPF não transita além da fronteira de ingestão (`agendamento-confirmacao-service`) — é resolvido para ID interno; notificação, Log Auditável e eventos referenciam exclusivamente o ID de Paciente.
- Dataset 100% sintético — nenhum dado real de Paciente ou Agendamento circula em nenhum ambiente.
- Infraestrutura deve privilegiar baixo custo — Free Tier onde possível, recursos pausáveis/destruíveis fora da janela de demo, padrão `deploy/pause/destroy` já validado.
- Barra de testes: cobertura de linha ≥90% (JaCoCo) na camada de domínio de cada microsserviço, complementada por teste de mutação (PIT) na mesma camada, testes de integração cobrindo os contratos entre serviços, e testes de aceitação BDD (Cucumber-JVM) cobrindo o ciclo único ponta a ponta.
- Continuidade sob falha parcial: o fluxo de Confirmação/Recusa continua aceitando respostas do Paciente mesmo se `auditoria-service` estiver temporariamente indisponível — a propagação para auditoria é processada quando ele voltar, nunca perdida.
- Contratos versionados: schema de evento versionado e path de endpoint REST versionado (`/v1/`), evitando quebras silenciosas entre notificação, repasse e auditoria.
- Segredos fora do código: credenciais e configuração sensível não ficam versionadas no repositório.
- Reprodutibilidade: o sistema inteiro (serviços + seed de dados) sobe com um único comando, sem passos manuais.

## Non-goals

- Fluxo de marcar o Agendamento em si (Paciente escolher unidade/data/horário) — o MVP assume Agendamento pré-existente via seed.
- Qualquer score ou decisão clínica automatizada, e qualquer critério de ordenação que não seja ordem de chegada da solicitação — permanentemente fora de escopo, por restrição legal, não apenas para este MVP.
- Repasse de vaga automático sem confirmação humana explícita.
- Integração real com canais de notificação (SMS/WhatsApp/e-mail) — a notificação é sempre mockada nesta fase.
- Interoperabilidade real com SISREG, SIH/SUS, e-SUS APS, DATASUS ou qualquer sistema oficial do SUS.
- Frontend/interface de usuário de qualquer tipo — entrega backend-only, demonstrável via Swagger/Postman.
- "Desfazer" uma Confirmação já registrada, ou uma Recusa já registrada.
- Repescagem automática retroativa quando um Paciente entra na Lista de Espera depois de uma Vaga já ter ficado Liberada sem sugestão.
- Autenticação e autorização completas de nível produção (RBAC aplicado, SSO, OAuth real) além do que já existe (`auth-service` com JWT real).
- Conformidade legal plena com a LGPD — fora de escopo deste MVP acadêmico.

## Success signal

O ciclo ponta a ponta (Agendamento sintético → notificação → confirmação/recusa/expiração → liberação → sugestão de repasse → decisão humana → log auditável) roda de forma demonstrável via Swagger/Postman, sem reprocessamento manual: liberação de vaga e geração da Sugestão de Repasse ocorrem automaticamente ao expirar a Janela de Confirmação ou ao registrar uma Recusa (SM-1); 100% das decisões registradas no Log Auditável são explicáveis — motivo e timestamp consultáveis — no dataset de demonstração (SM-2); nenhuma Vaga Liberada do dataset fica sem oferta de repasse pendente quando a Lista de Espera correspondente não está vazia (SM-3). Counter-metric — nunca otimizar: a ordem da Lista de Espera não é alterada, em nenhum cenário de demo, por um fator que pareça clínico ou de gravidade (SM-C1).
