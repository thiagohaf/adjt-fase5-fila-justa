---
title: Reconciliação PRD × Brief (input reconciliation)
input: brief.md (`_bmad-output/planning-artifacts/briefs/brief-Fase5-2026-08-30/brief.md`)
output: prd.md (`_bmad-output/planning-artifacts/prds/prd-Fase5-2026-09-05/prd.md`)
generated: 2026-09-05
---

# Reconciliação: brief.md → prd.md

Método: leitura seção-a-seção do brief, checando se cada afirmação de conteúdo, nuance e intenção narrativa
reaparece no PRD — literal ou funcionalmente equivalente — e sinalizando o que foi perdido, enfraquecido ou
contradito. Foco especial em elementos qualitativos (tom, metáforas, framing "por que isso importa") que uma
estrutura rígida de FR/UJ tende a apagar mesmo quando a capacidade técnica em si está coberta.

## Cobertura geral (o que passou bem)

- Fluxo ponta a ponta (triagem → score → matching → log) — preservado em Vision, Features 4.1–4.5 e MVP Scope.
- Todos os quatro atores do brief ("Who This Serves") reaparecem no PRD (§2.1 JTBD), com os mesmos papéis.
- "fura-fila" como termo — sobrevive (ver gap #4 abaixo sobre a redução do seu peso narrativo).
- Escopo fora do MVP (interoperabilidade real, frontend, agendamento inteligente, score de deterioração,
  auth completa) — reproduzido quase verbatim em §5 Non-Goals do PRD.
- Camada Adaptadora / seed sintético / CPF como chave / persistência simples / empacotamento reproduzível —
  todos presentes (FR-10, §6, §8).
- Sinais de sucesso ilustrativos do brief (recálculo automático, 100% de decisões explicáveis, ninguém
  "esquecido" indefinidamente) — mapeados quase 1:1 para SM-1/SM-2/SM-3, com o PRD ainda adicionando um
  counter-metric (SM-C1) que o brief não tinha — expansão, não perda.
- Nota de arquitetura futura (Event Storming/bounded contexts, addendum) — referenciada em §7 NFRs.
- "Não substitui os sistemas oficiais do SUS" / adapter isolando integração real futura — preservado em FR-10
  e no Glossário (Camada Adaptadora).

## Gaps identificados

### 1. Vision do PRD corta a ambição pós-MVP e o "contexto de mérito" (solo, prazo apertado)

O brief fecha com uma seção "Vision" que faz dois movimentos: (a) reafirma que o sucesso é provar o mecanismo
"em um MVP backend construído por **uma única pessoa**, dentro do prazo disponível" — ou seja, o mérito é
contextualizado pela restrição solo+hackathon; e (b) nomeia o próximo salto ambicioso específico — "trocar a
camada adaptadora simulada por integração real com os sistemas oficiais do SUS, o que exigiria **parceria
institucional** fora do escopo acadêmico atual."

O §1 Vision do PRD cobre apenas a "vitória" do MVP (fluxo ponta a ponta como API funcional sobre dados
sintéticos) e não traz nem a moldura "solo + prazo" nem o próximo passo de parceria institucional. Fica só
como Non-Goal técnico ("interoperabilidade real... fora de escopo"), sem a leitura de visão de produto que o
brief articula. Não é um requisito perdido, mas é framing/intenção de longo prazo que desaparece do documento
que alimentará a apresentação e a arquitetura.

### 2. As duas metáforas (mobilidade + bolsa de valores) viram uma só no ponto mais visível do PRD

O brief usa deliberadamente **duas** metáforas emparelhadas — "no espírito de como aplicativos de mobilidade
emparelham passageiro e motorista, **ou** como bolsas de valores casam ordens de compra e venda" — e a seção
Success Criteria do brief liga isso explicitamente ao critério de avaliação "Apresentação (20%)": *"usando a
metáfora de matching (mobilidade/bolsa de valores) para comunicar o mecanismo rapidamente."* O brief ainda cita
uma terceira analogia em "What Makes This Different" (mobilidade, bolsas de valores, **tráfego aéreo**) para
argumentar que o padrão já é validado alhures.

No PRD, o §1 Vision — a seção mais lida, no topo do documento — menciona **apenas** a bolsa de valores ("como
uma bolsa de valores casa ordens de compra e venda"), sem a mobilidade. A metáfora de mobilidade sobrevive
apenas mais abaixo, na descrição da Feature 4.3 ("no espírito de apps de mobilidade ou bolsas de valores") e
no nome da regra de desempate de FR-5 ("*price-time priority* — mesmo critério usado em livros de ofertas de
bolsa de valores", de novo só bolsa). A instrução explícita do brief de usar **as duas** metáforas juntas para
o pitch não é reafirmada em lugar nenhum do PRD (nem na nota de critérios em §9), e a terceira analogia
(tráfego aéreo) desaparece completamente.

### 3. O caso concreto SUS-BH (a evidência mais forte do "Problema") não é herdado

O brief documenta um caso específico e citável: falhas em prontuário eletrônico no SUS-BH já causaram "filas
físicas de mais de 40 pessoas disputando apenas 15 vagas de atendimento por dia." É o dado mais concreto de
toda a seção "The Problem" — exatamente o tipo de evidência que sustenta o critério "Problema e Impacto (20%)"
da banca. O PRD não reproduz esse caso em lugar nenhum (nem em §0 Document Purpose, nem em §1 Vision, nem em
notas). O brief argumenta que esse critério "já está endereçado" em outro artefato (`brainstorm-intent.md`), o
que atenua a gravidade do gap — mas, como o PRD é o documento que alimenta arquitetura/épicos daqui em diante,
essa evidência concreta simplesmente não circula mais para as fases seguintes.

Menor, mas relacionado: a lista específica de sistemas fragmentados do brief (SISREG, SIH/SUS, e-SUS APS,
painéis DRAC/DATASUS) é reduzida no PRD a um shorthand recorrente "SISREG/DATASUS", perdendo a menção a e-SUS
APS e DRAC como sistemas fragmentados nomeados.

### 4. "fura-fila" perde peso: de fio condutor a título de uma única User Journey

No brief, o framing "ordem de chegada em vez de gravidade clínica... abre espaço para 'fura-filas' e decisões
que ninguém consegue explicar depois" aparece no Executive Summary e é retomado no Problem e implicitamente em
"What Makes This Different" (a dicotomia "simples mas injusto" vs. "objetivo mas opaco"). É um fio condutor
emocional que atravessa o documento. No PRD, esse framing sobrevive essencialmente como o **título de UJ-3**
("Auditor investiga uma reclamação de fura-fila") — um uso pontual, não mais o motivo recorrente que justifica
por que a auditabilidade importa desde a primeira frase. A dicotomia "simples-mas-injusto vs. objetivo-mas-
opaco" em si é bem preservada em §1 Vision parágrafo 2, mas sem o vocabulário "fura-fila" ligado a ela.

### 5. Critério de Documentação (10%): referência à Fase 4 preservada para scripts, mas não para o relatório

O brief liga a nota de Documentação (10%) explicitamente ao "padrão já validado pelo aluno na Fase 4 (ver
referência de estilo)" — ou seja, o relatório final deve seguir esse precedente. O PRD reaproveita essa mesma
referência à Fase 4, mas só para os **scripts de infraestrutura** (§8 Constraints: "manter... o padrão de
scripts deploy/pause/destroy já validado no precedente da Fase 4") — a aplicação do precedente ao **formato do
relatório/documentação em si** não é repetida em nenhum lugar do PRD (nem na nota de critérios em §9). Efeito
prático baixo (é um detalhe de entrega, não de requisito de produto), mas é uma inconsistência de qual parte
do precedente da Fase 4 sobreviveu e qual não.

## Itens verificados e considerados sem gap material

- Regras de desempate de FR-5 (Triagem mais antiga, especificidade do Recurso, ociosidade) — não estão
  explícitas no brief nesse nível de detalhe; são elaboração legítima do PRD, não contradição.
- Fórmula linear-com-teto de Urgência Acumulada (FR-7, 20% da amplitude / 12–24h) — o brief só pede que a
  urgência acumulada exista e tenha um efeito perceptível; o PRD formaliza isso como assumption explícita
  (§11), o que é expansão consistente, não invenção não sinalizada.
- CPF tratado como dado sensível mesmo sendo sintético (§8 PRD) — não está no brief, mas é sinalizado no
  próprio PRD como `[ASSUMPTION]` de boa prática, não como se fosse requisito do brief.
