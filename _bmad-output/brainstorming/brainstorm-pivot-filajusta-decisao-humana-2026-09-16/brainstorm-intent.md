---
source: .memlog.md (sessão de brainstorming 2026-09-16)
status: decisão do usuário registrada
---

# Intent — Pivô FilaJusta → ConfirmaSUS

## 1. Contexto do pivô

Legislação vigente proíbe sistema automatizado decidir triagem ou priorização clínica: toda decisão sobre a saúde do paciente deve ser humana. O FilaJusta original (matching automático de leito/especialista com score de prioridade clínico) não pode manter esse cálculo autônomo, o que forçou o pivô de direção do MVP.

## 2. Direção escolhida — ConfirmaSUS

Ideia central: paciente recebe notificação (via fila de eventos) sobre consulta ou exame marcado e precisa confirmar presença via API; sem resposta em X horas, a vaga volta para a fila de espera por ordem de solicitação. Objetivo: reduzir no-show e fila fantasma sem tocar em nenhuma decisão clínica.

JTBD que sustentam a direção:
- Paciente com consulta marcada: "não perder minha vaga e não perder meu dia indo a uma consulta cancelada sem aviso."
- Paciente crônico (adjacente, complementar): "garantir que não esqueço de tomar o remédio certo na hora certa e avisar alguém se eu esquecer" — não incluído no escopo do MVP, mas é dor do mesmo tipo de usuário.

Reordenação da fila: troca-se o score clínico por ordem de chegada da solicitação + confirmação periódica de interesse (elimina no-show e fila fantasma sem decisão clínica). Reordenar a fila continua sendo ação explícita de um humano, com motivo registrado, nunca automática por acúmulo de urgência.

Reaproveitamento de infraestrutura de eventos: o padrão outbox + relay SNS + SQS, hoje usado para propagar score e alocação, é adaptado para propagar notificações de confirmação de consulta (mesmo mecanismo, novo conteúdo de evento). Notificação real (SMS/e-mail) pode ser mockada na primeira entrega — só loga e persiste o envio — para priorizar o fluxo de confirmação em si.

## 3. Reaproveitado vs. abandonado

**Mantido sem alteração:**
- auth-service, gateway-service e infra-cdk — 100% como estão.
- Padrão outbox + SNS + SQS + DLQ — mesma arquitetura, só muda o conteúdo dos eventos publicados.
- Padrão de fluxo de confirmação/recusa já implementado e testado (ConfirmarAlocacao/RecusarSugestao) — candidato a reaproveitamento quase sem alteração para o novo fluxo de confirmação de presença do paciente.

**Abandonado:**
- Matching automático de leito/especialista (matching-alocacao-service) — decisão explícita do usuário de abandonar essa direção.
- Cálculo automático de score de prioridade clínica (triagem-score-service).
- Reordenação automática de fila por urgência acumulada.

## 4. Diferencial a preservar

O log auditável explicável — diferencial do projeto original — continua tendo valor, adaptado ao novo domínio: em vez de registrar por que o sistema calculou um score, registra por que um paciente confirmou ou não confirmou presença, e quando. Serve à mesma necessidade de auditoria/prova (JTBD do auditor: "provar quem decidiu o que e quando, com motivo registrado").

## 5. Alternativas descartadas (registro)

- **Direção 1 — Matching com Aprovação Humana Obrigatória**: reaproveitaria quase 100% do código existente (Story 3.3 já exige confirmação humana), mas risco legal depende de leitura exata da legislação (se proíbe até score sugerido como rascunho).
- **Direção 3 — Central de Encaminhamento Digital**: ataca dor real de regulação manual por telefone/fax, zero decisão clínica, mas não foi a escolhida.
- **Direção 4 — Gestão de Insumos e Recursos com Alerta**: reaproveita domínio Recurso já implementado e é tema do edital, mas tem overlap pequeno com o diferencial de auditabilidade.
- **Direção 5 — Lembrete e Confirmação de Medicação**: reaproveita 100% da infra, mas é o domínio mais distante do que já foi construído.

Decisão do usuário: priorizar risco legal mais baixo sobre reaproveitamento máximo de código.

## 6. Perguntas em aberto / riscos

- Precisa modelar um domínio novo de "Consulta/Agendamento" que não existia na base de código anterior (alta novidade de domínio, ao contrário das direções que reaproveitavam o domínio de Recurso/leito).
- Definir o limiar X (horas) de espera de confirmação antes de liberar a vaga.
- Definir regra de reordenação da fila de espera por ordem de solicitação + confirmação periódica de interesse.
