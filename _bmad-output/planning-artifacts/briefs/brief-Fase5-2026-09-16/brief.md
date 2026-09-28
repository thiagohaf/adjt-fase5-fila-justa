---
title: ConfirmaSUS — Confirmação Ativa de Consulta e Exame
status: final
created: 2026-09-16
updated: 2026-09-16
---

# Product Brief: ConfirmaSUS — Confirmação Ativa de Consulta e Exame [ASSUMPTION: nome de produto sugerido; troque livremente]

> Contexto acadêmico: Hackathon FIAP Pós-Tech — Arquitetura e Desenvolvimento Java, Fase 5. Tema do edital: "Inovação para otimização de atendimento no SUS". Entrega solo — Thiago Henrique Alves Ferreira, RM369442, turma 11ADJT. Entregável exigido: MVP backend-only (sem frontend obrigatório), demonstrável via Swagger/Postman.
>
> Este brief substitui o brief anterior (FilaJusta — Motor de Priorização e Alocação Inteligente, `brief-Fase5-2026-08-30`), abandonado após a descoberta de que a legislação vigente proíbe que um sistema automatizado decida triagem ou priorização clínica — toda decisão sobre a saúde do paciente deve ser humana. O pivô foi conduzido via sessão de brainstorming (`_bmad-output/brainstorming/brainstorm-pivot-filajusta-decisao-humana-2026-09-16/`), que gerou 41 ideias e convergiu nesta direção.

## Executive Summary

O absenteísmo em consultas e exames é uma das formas mais silenciosas de desperdício no SUS: vagas reservadas, profissionais escalados e equipamentos preparados ficam ociosos porque o paciente simplesmente não avisou que não viria — e ninguém mais é chamado a tempo de ocupar aquele lugar. O **ConfirmaSUS** é um motor de backend que fecha esse ciclo: notifica o paciente sobre sua consulta ou exame já agendado, exige uma confirmação ativa dentro de um prazo, e — se não houver resposta — libera a vaga e sugere o próximo paciente da lista de espera para um humano decidir o repasse. Nenhuma decisão sobre a saúde do paciente é automatizada; o sistema só notifica, aguarda confirmação, sugere e registra.

Iniciativas de lembrete simples (SMS, WhatsApp) já existem e comprovadamente funcionam — o governo do Ceará reduziu o absenteísmo em cerca de 19% em 2025 enviando mensagens via WhatsApp antes da consulta. O ConfirmaSUS vai um passo além: fecha o ciclo que essas iniciativas deixam aberto, transformando a vaga ociosa detectada em uma **oferta ativa e auditável** para quem está esperando, sem nunca decidir sozinho quem deve ser priorizado.

Para o hackathon, a aposta é demonstrar esse ciclo completo — agendamento sintético → notificação → confirmação/recusa do paciente → liberação da vaga → sugestão de repasse → decisão humana → registro auditável — como uma API funcional, alimentada por dados sintéticos, reaproveitando quase integralmente a infraestrutura já construída na fase anterior do projeto (autenticação, gateway, arquitetura orientada a eventos).

## The Problem

O absenteísmo em consultas e exames do SUS é alto e bem documentado: estudos regionais apontam médias de 25% a 38,6% dependendo da especialidade e região (Beltrame, Oliveira, Santos & Santos Neto, 2019/2020), acima da média mundial estimada em 23%. Um levantamento na Região Metropolitana do Espírito Santo (2014-2016) estimou R$ 18,5 milhões desperdiçados em três anos só com consultas e exames especializados não comparecidos — vagas que nenhum outro paciente ocupou a tempo.

As causas mais citadas na literatura são banais e evitáveis: esquecimento da data/horário, falha de comunicação entre serviço e paciente, melhora do sintoma antes da data marcada e conflitos de transporte ou trabalho. Um estudo espanhol estima que mais da metade dos casos de absenteísmo seriam evitáveis com a intervenção certa.

Quem sente essa dor: o **paciente** que perde a vaga sem lembrete e sem chance de repassá-la a alguém que precisa; o **paciente da lista de espera** que segue esperando enquanto vagas já liberadas ficam ociosas por falta de aviso; o **gestor de agenda/regulador**, que só descobre a ausência quando já é tarde para remanejar; e o **sistema como um todo**, que paga por capacidade instalada (profissional, sala, equipamento) não utilizada.

## The Solution

O ConfirmaSUS é uma API backend organizada em torno de um ciclo único e demonstrável:

1. **Consulta/exame pré-agendado** — dados sintéticos simulam agendamentos já confirmados no sistema (a marcação em si não é o problema atacado aqui — ver Scope).
2. **Notificação assíncrona de confirmação** — o paciente é notificado (mock de canal externo, propagado pela mesma arquitetura de eventos já existente) pedindo confirmação de presença dentro de uma janela de tempo [ASSUMPTION: janela de confirmação de 48h antes do atendimento, inspirada no padrão adotado pelo Ceará de notificar 10 dias e 48h antes].
3. **Confirmação ou recusa ativa** — o paciente responde via API (confirma ou recusa). Sem resposta até o prazo, o sistema trata como não confirmado.
4. **Liberação e sugestão de repasse** — vaga não confirmada é liberada; o sistema sugere o próximo paciente da lista de espera daquele recurso, por ordem de chegada da solicitação — nunca por critério clínico.
5. **Decisão humana de repasse** — um gestor de agenda confirma ou recusa a sugestão antes que o repasse valha de fato (reaproveita o padrão de confirmação/recusa já implementado e testado na fase anterior do projeto).
6. **Log de decisões auditável** — toda notificação, confirmação, recusa e decisão de repasse fica registrada de forma explicável: é possível responder a qualquer paciente ou auditor por que a vaga X foi repassada ao paciente Y, e quando.

## What Makes This Different

Para ser honesto sobre os limites: o mecanismo de lembrete/confirmação isoladamente **não é novidade** — o próprio SUS já tem um caso real em produção (Ceará, 2025, lembretes via WhatsApp) e a literatura internacional (revisão Cochrane sobre SMS) já validou que funciona, com reduções de absenteísmo na faixa de 20% a 40%.

O diferencial do ConfirmaSUS não é o lembrete — é **fechar o ciclo que o lembrete sozinho deixa aberto**, transformando a vaga liberada em uma **oferta ativa para a lista de espera**, com decisão humana obrigatória e **registro auditável de cada repasse**. É a mesma disciplina de explicabilidade que já era o diferencial do projeto anterior (FilaJusta) — agora aplicada a uma decisão administrativa (repassar uma vaga ociosa), e não a uma decisão clínica automatizada: essa separação é exatamente o que a legislação exige.

## Who This Serves

- **Paciente com consulta/exame marcado** (usuário direto da API): recebe lembrete e evita perder a vaga por esquecimento; se não puder comparecer, sua recusa ativa libera a vaga a tempo de ajudar outra pessoa.
- **Paciente em lista de espera** (beneficiário indireto): passa a ter uma chance real de ser chamado quando uma vaga é liberada, em vez de a vaga simplesmente ficar ociosa.
- **Gestor de agenda/regulador** (usuário direto da API): é avisado de vagas liberadas com um candidato já sugerido pelo sistema, mas decide o repasse — sem depender de descobrir a ausência por acaso.
- **Auditor / órgão de controle** (consumidor do diferencial de explicabilidade): consegue justificar, com dados, por que e quando cada vaga liberada foi repassada.

## Success Criteria

**Critérios de avaliação do edital (banca):**
- Problema e Impacto (20%) — absenteísmo no SUS é dor documentada e mensurável (ver seção Problem, fontes na `addendum.md`).
- Inovação (20%) — o diferencial não é o lembrete (já existe), é o ciclo completo de detecção de ociosidade + oferta ativa à lista de espera + decisão humana auditável.
- Funcionalidade do MVP (30%) — o ciclo ponta a ponta (notificação → confirmação/recusa → liberação → sugestão → decisão humana → log) precisa rodar de forma confiável e demonstrável via Swagger/Postman.
- Apresentação (20%) — pitch e vídeo de demo claros, ancorados no dado de custo real (ex.: R$ 18,5 milhões desperdiçados em 3 anos numa única região) e no precedente real do Ceará.
- Documentação (10%) — relatório completo, no padrão já validado pelo aluno na Fase 4.

**Sinais de que o mecanismo em si funciona** [ASSUMPTION: valores ilustrativos, ajustar quando houver dataset de demo definido]:
- O sistema notifica corretamente e recebe a confirmação ou recusa do paciente em 100% dos casos simulados na demo.
- Toda vaga liberada por não confirmação gera automaticamente uma sugestão de repasse para a lista de espera e notifica o gestor de agenda, sem intervenção manual.
- Toda decisão humana de repasse (confirmar ou recusar a sugestão) fica registrada no log auditável com motivo e timestamp, em 100% dos casos simulados.

## Scope

**Dentro do MVP (hackathon):**
- API backend (sem frontend obrigatório), demonstrável via Swagger/coleção Postman.
- Ciclo único ponta a ponta: consulta/exame pré-agendado (seed) → notificação de confirmação → confirmação/recusa do paciente → liberação de vaga → sugestão de repasse à lista de espera → confirmação/recusa humana do repasse → log auditável.
- CPF como chave de identificação do paciente; dados demográficos e de agendamento mockados via seed sintético.
- Autenticação real via `auth-service` + `gateway-service` já construídos (reaproveitados sem alteração).
- Notificação simulada (mock que loga e persiste o envio) — sem integração real com SMS/WhatsApp/e-mail.
- Lista de espera por ordem de chegada da solicitação (nunca por critério clínico).
- Empacotamento reproduzível (infra-cdk já existente, subir com um único comando).

**Fora do MVP (explicitamente adiado):**
- Fluxo de marcar a consulta/exame em si (paciente escolher unidade/data/horário) — o MVP assume agendamento pré-existente via seed. [decisão do usuário, 2026-09-16]
- Qualquer score ou decisão clínica automatizada — permanentemente fora de escopo, por restrição legal.
- Repasse de vaga automático sem confirmação humana explícita.
- Integração real com canais de notificação (SMS/WhatsApp/e-mail) ou com SISREG/DATASUS.
- Autenticação e autorização completas de nível produção além do que já existe.

## Vision

O sucesso, neste hackathon, é provar que um ciclo de notificação, confirmação ativa, oferta à lista de espera e decisão humana auditável reduz uma ineficiência real e documentada do SUS, dentro dos limites legais — reaproveitando quase integralmente a arquitetura de eventos e autenticação já construída na fase anterior do projeto.

Além do hackathon, os passos naturais são: (1) reintroduzir o fluxo de agendamento em si, hoje deixado de fora; (2) trocar a notificação simulada por integração real (o precedente do Ceará com WhatsApp mostra o caminho); e (3) generalizar o padrão "sugestão + confirmação humana + log auditável" para outros problemas de realocação de recursos do SUS (leitos, equipamentos) — sempre com a decisão final nas mãos de um humano.
