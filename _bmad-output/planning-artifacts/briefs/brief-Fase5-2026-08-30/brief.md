---
title: FilaJusta — Motor de Priorização e Alocação Inteligente (SUS)
status: final
created: 2026-08-30
updated: 2026-09-05
---

# Product Brief: FilaJusta — Motor de Priorização e Alocação Inteligente [ASSUMPTION: nome de produto sugerido; troque livremente]

> Contexto acadêmico: Hackathon FIAP Pós-Tech — Arquitetura e Desenvolvimento Java, Fase 5. Tema do edital: "Inovação para otimização de atendimento no SUS". Entrega solo — Thiago Henrique Alves Ferreira, RM369442, turma 11ADJT. Entregável exigido: MVP backend-only (sem frontend obrigatório), demonstrável via Swagger/Postman.

## Executive Summary

O SUS perde eficiência não por falta de recursos, mas por falta de critério: filas cirúrgicas e de leitos são geridas de forma manual e fragmentada, entre sistemas que não conversam entre si (SISREG, SIH/SUS, e-SUS APS, DATASUS). A prioridade de atendimento frequentemente segue a ordem de chegada em vez da gravidade clínica — abrindo espaço para esperas injustas, "fura-filas" e decisões que ninguém consegue explicar depois.

O **FilaJusta** é um motor de backend que calcula um score de prioridade clínica objetivo a partir de dados de triagem e empareia cada paciente com o leito ou especialista disponível mais adequado, em tempo real. Cada decisão fica registrada em um log auditável e explicável. Ele não substitui os sistemas oficiais do SUS: posiciona-se como uma camada de orquestração inteligente que, no mundo real, poderia se conectar a eles por meio de adaptadores.

Para o hackathon, a aposta é demonstrar esse mecanismo de ponta a ponta — triagem, score, matching e log — como uma API funcional, alimentada por dados sintéticos que simulam unidades de saúde, leitos e especialistas reais. Isso prova o conceito sem depender de acesso aos sistemas de produção do SUS, algo inviável no prazo de um hackathon acadêmico.

## The Problem

Pesquisas recentes confirmam que a dor é real e documentada, não hipotética:

- **Fragmentação de sistemas**: SISREG, SIH/SUS, e-SUS APS e os painéis DRAC/DATASUS não conversam entre si de forma confiável, impedindo uma visão unificada de demanda e oferta de leitos/vagas.
- **Priorização sem critério objetivo**: pacientes costumam ser atendidos por ordem de chegada, não por gravidade clínica — o que tanto prejudica casos urgentes quanto abre espaço para "fura-filas" sem transparência.
- **Regulação manual**: o encaminhamento de pacientes entre unidades ainda depende, em muitos lugares, de telefone e fax, tornando o processo lento e sujeito a erro humano.
- **Prontuário eletrônico instável**: falhas e lentidão em sistemas de prontuário (caso documentado no SUS-BH) já causaram filas físicas de mais de 40 pessoas disputando apenas 15 vagas de atendimento por dia.
- **Insuficiência de leitos** combinada a regulação ineficiente prolonga a permanência de pacientes à espera de transferência.

Quem sente essa dor: o paciente que não sabe quanto tempo vai esperar nem por quê; o profissional de saúde que perde tempo articulando encaminhamentos manualmente; o gestor que não tem dados confiáveis para provar (ou corrigir) a justiça da fila; e o órgão de controle que não consegue auditar decisões de priorização passadas.

## The Solution

O FilaJusta é uma API backend organizada em torno de um fluxo único e demonstrável:

1. **Triagem estruturada** — dados clínicos de entrada (sintomas, sinais vitais, gravidade percebida) chegam via API, simulando o que hoje seria preenchido em papel ou em sistemas isolados.
2. **Score de prioridade clínica** — um cálculo objetivo e determinístico transforma esses dados em uma prioridade auditável, substituindo a ordem de chegada.
3. **Matching paciente–recurso** — o motor empareia o paciente com o leito ou especialista disponível mais adequado no momento, no espírito de como aplicativos de mobilidade emparelham passageiro e motorista, ou como bolsas de valores casam ordens de compra e venda.
4. **Priorização por urgência acumulada** — pacientes com prioridade moderada que esperam por muito tempo sobem naturalmente na fila, evitando que fiquem represados indefinidamente atrás de casos sempre "mais urgentes".
5. **Log de fila auditável** — toda decisão de priorização e alocação fica registrada de forma explicável: é possível responder a qualquer paciente por que fulano foi atendido antes dele.

Uma camada adaptadora simula a integração com SISREG/DATASUS a partir de dados sintéticos (seed), preservando a extensibilidade da arquitetura sem exigir acesso real a esses sistemas — inviável no prazo do hackathon.

## What Makes This Different

O diferencial não é "mais um sistema de fila" — é a **combinação de priorização objetiva com auditabilidade**. A maioria das soluções de fila (inclusive as oficiais atuais) falha em pelo menos um dos dois: ou prioriza por ordem de chegada (simples, mas injusto), ou prioriza por critério clínico sem conseguir explicar a decisão depois (opaco, sujeito a contestação). O FilaJusta entrega os dois ao mesmo tempo, com um log que qualquer auditor ou paciente poderia, em tese, consultar.

Para ser honesto sobre os limites: o "moat" aqui não é tecnológico — o mecanismo de matching e score, isoladamente, é bem estabelecido em outras indústrias (mobilidade, bolsas de valores, tráfego aéreo). O que diferencia é a aplicação bem executada desse padrão já validado a um problema real e mal servido do SUS, tratando a transparência como prioridade de design desde o início — não como um recurso adicionado depois.

## Who This Serves

- **Regulador / gestor de leitos e vagas** (usuário direto da API): precisa decidir rapidamente qual leito ou vaga oferecer, com critério defensável, sem depender de telefonemas.
- **Enfermeiro(a) / profissional de triagem** (usuário direto da API): alimenta o sistema com dados clínicos estruturados que viram prioridade objetiva.
- **Paciente** (beneficiário indireto no MVP, já que não há frontend): tem sua posição na fila determinada por critério transparente, não por ordem de chegada ou influência.
- **Auditor / órgão de controle** (consumidor do diferencial de explicabilidade): precisa conseguir justificar, com dados, por que um paciente foi atendido antes de outro.

## Success Criteria

Os critérios de sucesso desta entrega se dividem em duas frentes: o que os professores avaliam e o que comprovaria que o mecanismo funciona de fato.

**Critérios de avaliação do edital (banca):**
- Problema e Impacto (20%) — a dor precisa ser reconhecível e bem fundamentada (já endereçado — ver `brainstorm-intent.md` e a seção acima).
- Inovação (20%) — o log auditável/explicável como diferencial central, não um "score a mais".
- Funcionalidade do MVP (30%) — o fluxo ponta a ponta (triagem → score → matching → log) precisa rodar de forma confiável e demonstrável via Swagger/Postman.
- Apresentação (20%) — pitch e vídeo de demo claros, usando a metáfora de matching (mobilidade/bolsa de valores) para comunicar o mecanismo rapidamente.
- Documentação (10%) — relatório completo, no padrão já validado pelo aluno na Fase 4 (ver referência de estilo).

**Sinais de que o mecanismo em si funciona** [ASSUMPTION: valores ilustrativos, ajustar quando houver dataset de demo definido]:
- O sistema recalcula a fila corretamente ao inserir um novo paciente de alta urgência, sem exigir reprocessamento manual.
- Todas as decisões de alocação no log auditável podem ser explicadas (quais fatores levaram àquela prioridade/match) em 100% dos casos simulados na demo.
- Nenhum paciente de prioridade moderada fica "esquecido" indefinidamente no dataset de demonstração — a urgência acumulada garante progressão visível ao longo do tempo simulado.

## Scope

**Dentro do MVP (hackathon):**
- API backend (sem frontend obrigatório), demonstrável via Swagger/coleção Postman.
- Fluxo único ponta a ponta: triagem → score de prioridade → matching/sugestão de alocação → log auditável.
- CPF como chave de identificação do paciente; dados demográficos mockados — sem cadastro completo.
- Autenticação simplificada (token mockado é suficiente; não é o foco de inovação desta entrega).
- Dados de unidades de saúde, leitos e especialistas via seed sintético, atrás de uma camada adaptadora que simula o SISREG/DATASUS.
- Persistência simples, com um dataset pequeno o suficiente para a demonstração em vídeo rodar sem falhas.
- Empacotamento reproduzível (ex.: subir com um único comando) para garantir a demo.

**Fora do MVP (explicitamente adiado):**
- Interoperabilidade real com SISREG/DATASUS ou qualquer sistema oficial do SUS.
- Frontend / interface de usuário.
- Agendamento inteligente e redução de no-show (parqueado para v2).
- Score de risco de deterioração clínica pós-priorização (parqueado para v2).
- Autenticação e autorização completas de nível produção.

Decisões técnicas de implementação (stack, padrão arquitetural, cloud, estratégia de custo) estão documentadas em `addendum.md` — são insumo direto para a etapa de arquitetura, não fazem parte deste brief.

## Vision

O sucesso, neste hackathon, é provar que priorização objetiva e auditabilidade cabem em um MVP backend construído por uma única pessoa, dentro do prazo disponível. Além dele, os passos naturais já identificados na sessão de brainstorming são os itens listados em "Fora do MVP" acima — com destaque para o salto mais ambicioso: trocar a camada adaptadora simulada por integração real com os sistemas oficiais do SUS, o que exigiria parceria institucional fora do escopo acadêmico atual.

Arquiteturalmente, a intenção declarada é evoluir de uma prova de conceito para uma base em microsserviços com Event Storming, CQRS e alta disponibilidade na AWS — como detalhado no `addendum.md` — mantendo desde já a disciplina de método (BMAD) que o aluno já validou na fase anterior do curso.
