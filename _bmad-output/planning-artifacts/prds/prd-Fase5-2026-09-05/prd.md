---
title: FilaJusta — Motor de Priorização e Alocação Inteligente (SUS)
status: final
created: 2026-09-05
updated: 2026-09-05
---

# PRD: FilaJusta — Motor de Priorização e Alocação Inteligente (SUS)

## 0. Document Purpose

Este PRD traduz o Product Brief (`_bmad-output/planning-artifacts/briefs/brief-Fase5-2026-08-30/brief.md` + `addendum.md`) em requisitos funcionais e não-funcionais estáveis para as próximas fases BMAD (`bmad-architecture` → `bmad-create-epics-and-stories` → `bmad-build`). Contexto: entrega individual do Hackathon FIAP Pós-Tech — Arquitetura e Desenvolvimento Java, Fase 5 (Thiago Henrique Alves Ferreira, RM369442, turma 11ADJT), avaliada por banca segundo critérios de edital (Problema/Impacto 20%, Inovação 20%, Funcionalidade do MVP 30%, Apresentação 20%, Documentação 10% — ver §9). O documento é organizado por Glossário → Features (com FRs aninhados e numerados globalmente) → NFRs/Constraints. Decisões técnicas de implementação (stack, padrão arquitetural, cloud, estratégia de custo) permanecem no `addendum.md` do brief e são insumo direto para `bmad-architecture`, não duplicadas aqui.

## 1. Vision

Hoje, quando um leito ou uma vaga de especialista aparece livre no SUS, a fila que decide quem a recebe é frequentemente por ordem de chegada, não por gravidade clínica — abrindo espaço para "fura-filas" e decisões que ninguém consegue explicar depois. Um caso documentado no SUS-BH mostra o custo concreto disso: falhas em prontuário eletrônico já geraram filas físicas de mais de 40 pessoas disputando apenas 15 vagas de atendimento por dia.

O FilaJusta é o motor de backend que decide, de forma objetiva e auditável, quem deve ser atendido a seguir nessa fila — substituindo a ordem de chegada por um score de prioridade clínica calculado a partir da triagem, e emparelhando cada paciente ao recurso disponível mais adequado em tempo real, no espírito de como aplicativos de mobilidade emparelham passageiro e motorista, ou como bolsas de valores casam ordens de compra e venda.

O diferencial não é o mecanismo de matching em si (bem estabelecido em outras indústrias), mas a combinação de priorização objetiva **com** explicabilidade desde o design: toda decisão de alocação gera um registro que responde, para qualquer paciente ou auditor, "por que fulano foi atendido antes de mim" — sem "fura-fila" silencioso de um lado, nem prioridade objetiva sem explicação do outro.

Para este MVP de hackathon, a vitória é demonstrar o fluxo ponta a ponta — triagem → score → matching → log auditável — como uma API funcional sobre dados sintéticos, construída por uma única pessoa dentro do prazo disponível, provando o conceito sem depender de acesso real a SISREG/DATASUS (inviável no prazo acadêmico). O salto ambicioso além deste MVP — trocar a Camada Adaptadora simulada por integração real com os sistemas oficiais do SUS — exigiria parceria institucional fora do escopo acadêmico atual.

## 2. Target User

### 2.1 Jobs To Be Done

- **Regulador de leitos/vagas**: decidir rapidamente qual leito ou vaga oferecer, com critério defensável, sem depender de telefonemas.
- **Profissional de triagem (enfermeiro/a)**: registrar dados clínicos estruturados e obter, imediatamente, uma prioridade objetiva — sem precisar calcular ou justificar isso manualmente.
- **Auditor / órgão de controle**: justificar, com dados, por que um paciente foi atendido antes de outro, a qualquer momento.
- **Paciente** (beneficiário indireto — não há frontend no MVP): ter sua posição na fila determinada por critério transparente, não por ordem de chegada ou influência.

### 2.2 Non-Users (v1)

- Pacientes e cidadãos não têm nenhuma interface direta neste MVP (sem app/portal) — interagem apenas indiretamente, via os atores acima.
- Administradores de sistemas oficiais do SUS (SISREG/DATASUS) não são usuários diretos: a integração com eles é simulada (seed sintético), não real, nesta fase.

### 2.3 Key User Journeys

- **UJ-1. Enfermeira registra a triagem e já recebe a prioridade explicada.**
  - **Persona + contexto:** Enfermeira de plantão em uma UPA sobrecarregada, sem tempo para preencher papel duas vezes.
  - **Entry state:** Autenticada via token mockado, chama a API diretamente (sem UI).
  - **Path:** Envia `POST` de triagem estruturada (CPF, sintomas, sinais vitais, gravidade percebida) → sistema calcula o score de prioridade clínica → sistema retorna o score já quebrado por fator contribuinte.
  - **Climax:** A resposta da chamada já mostra a prioridade e o motivo, sem etapa manual de cálculo.
  - **Resolution:** O Paciente entra na fila priorizada corretamente desde o primeiro instante. Realiza FR-1, FR-3.
  - **Edge case:** Se faltar um sinal vital obrigatório, a API rejeita com `400` explicando qual campo falta, em vez de aceitar um score incompleto.

- **UJ-2. Regulador decide qual leito oferecer sem telefonema.**
  - **Persona + contexto:** Regulador de leitos de um hospital regional, historicamente dependente de ligações para saber quem está disponível.
  - **Entry state:** Autenticado, consulta a API a partir do seu posto de trabalho.
  - **Path:** Consulta a fila atual ordenada por prioridade → consulta a sugestão de Matching para um leito recém-liberado → revisa e confirma a sugestão do sistema.
  - **Climax:** Recebe uma sugestão de matching com justificativa (qual paciente, por quê) em vez de decidir "no escuro".
  - **Resolution:** O leito é oferecido ao paciente correto e a decisão fica registrada. Realiza FR-5, FR-6, FR-12.

- **UJ-3. Auditor investiga uma reclamação de fura-fila.**
  - **Persona + contexto:** Órgão de controle recebe uma reclamação de que um paciente foi "passado para trás" injustamente.
  - **Entry state:** Autenticado, sem conhecimento prévio do caso.
  - **Path:** Consulta o log auditável do paciente reclamante → vê a linha do tempo de score e posição na fila → compara com o paciente que foi atendido antes.
  - **Climax:** Cada decisão tem um fator explícito (gravidade, tempo de espera acumulado) — a resposta é defensável com dados, não "confie em nós".
  - **Resolution:** Reclamação respondida com evidência auditável, ou identificado um caso real de erro a corrigir. Realiza FR-8, FR-9.

- **UJ-4. Paciente entende, por meio do auditor, por que esperou mais que outro.**
  - **Persona + contexto:** Paciente com prioridade moderada, esperando há dias, preocupado que esteja sendo esquecido.
  - **Entry state:** Sem acesso direto ao sistema (não há frontend no MVP) — a explicação chega por meio de um auditor ou atendente que consulta a API por ele.
  - **Path:** Auditor consulta o histórico do paciente → mostra que a prioridade dele subiu ao longo do tempo de espera (urgência acumulada) e que casos de gravidade maior foram atendidos antes por motivo objetivo.
  - **Climax:** O paciente recebe uma explicação concreta, não "é assim que funciona".
  - **Resolution:** Confiança na justiça do processo, mesmo esperando. Realiza FR-7, FR-9.

## 3. Glossário

- **Paciente** — pessoa que aguarda atendimento, identificada externamente por CPF (sintético, no MVP) apenas na fronteira de ingestão (Triagem) e internamente por um ID de Paciente gerado pelo sistema, usado em todo o restante da plataforma (ver §8 Constraints/LGPD). Dados demográficos são mockados; não há cadastro completo.
- **Triagem** — registro estruturado de dados clínicos de entrada (sintomas, sinais vitais, gravidade percebida) para um Paciente.
- **Score de Prioridade Clínica** (ou apenas **Score**) — valor numérico determinístico calculado a partir da Triagem, usado como base para ordenar a fila.
- **Urgência Acumulada** (ou **Aging**) — fator que aumenta a Prioridade Efetiva de um Paciente proporcionalmente ao tempo de espera, evitando que prioridades moderadas fiquem represadas indefinidamente.
- **Prioridade Efetiva** — Score ajustado pela Urgência Acumulada (`score + aging`); é o valor usado para ordenar a fila e decidir o Matching, não o Score isolado.
- **Recurso** — leito ou especialista que pode ser alocado a um Paciente; entra no pool de disponíveis via seed (FR-10) ou por Liberação (FR-13) e sai dele ao ser confirmado (FR-12).
- **Sugestão de Matching** — recomendação, gerada pelo sistema, de qual Paciente deveria ocupar um Recurso disponível. Ainda não consome o Recurso nem é uma decisão definitiva.
- **Alocação** — atribuição definitiva de um Recurso a um Paciente, criada quando um Regulador confirma uma Sugestão de Matching (FR-12). Só existe Alocação após confirmação — antes disso, o que existe é apenas uma Sugestão de Matching.
- **Log Auditável** — registro explicável de toda decisão de priorização, Sugestão de Matching, confirmação, recusa e Liberação de Recurso, incluindo os fatores que a determinaram.
- **Camada Adaptadora** — componente que simula a integração com SISREG/DATASUS a partir de dados sintéticos (seed), isolando o domínio de uma futura integração real.
- **Regulador** — usuário direto da API responsável por decidir qual Recurso oferecer a qual Paciente.
- **Profissional de Triagem** — usuário direto da API (ex.: enfermeiro/a) que registra a Triagem.
- **Auditor** — usuário (direto ou por meio de um atendente) que consulta o Log Auditável para justificar decisões passadas.

## 4. Features

### 4.1 Triagem Estruturada

**Descrição:** Ponto de entrada de dados clínicos via API, substituindo o preenchimento em papel ou em sistemas isolados. Realiza UJ-1.

**Functional Requirements:**

#### FR-1: Registro de Triagem

Um Profissional de Triagem pode submeter, via API, uma Triagem estruturada (CPF do Paciente, sintomas, sinais vitais, gravidade percebida).

**Consequences (testable):**
- API retorna `201` com o identificador da Triagem criada e o Score já calculado (ver FR-3).
- Campos de sinais vitais obrigatórios ausentes ou fora de faixa fisiológica plausível causam `400` com indicação do campo inválido. `[ASSUMPTION: faixas fisiológicas plausíveis concretas (ex.: limites de frequência cardíaca, pressão arterial) ficam para definição em bmad-architecture]`
- CPF com formato/checksum inválido causa `400` antes de qualquer cálculo de Score.

#### FR-2: Identificação Mínima do Paciente

O sistema associa a Triagem a um Paciente existente pelo CPF ou cria um registro mínimo (CPF + dados demográficos mockados) caso ainda não exista, gerando ou reutilizando um ID de Paciente interno. A partir desse ponto, o CPF não é propagado para os demais componentes do sistema (Score, Matching, Log Auditável) — eles referenciam o Paciente exclusivamente pelo ID interno (ver §8 Constraints/LGPD).

**Consequences (testable):**
- Duas Triagens para o mesmo CPF nunca criam dois Pacientes distintos (idempotência por CPF) nem dois IDs internos diferentes.
- Não é exigido nenhum fluxo de cadastro completo separado — a criação é implícita na primeira Triagem.
- Nenhum evento ou registro gerado após a Triagem (Score, Sugestão de Matching, Log Auditável) carrega o CPF em texto claro — todos referenciam o ID de Paciente interno.

**Out of Scope:** Cadastro completo de Paciente, validação de identidade além do formato do CPF.

### 4.2 Score de Prioridade Clínica

**Descrição:** Transforma os dados de Triagem em uma prioridade objetiva e auditável, substituindo a ordem de chegada. Realiza UJ-1.

**Functional Requirements:**

#### FR-3: Cálculo do Score

O sistema calcula o Score de Prioridade Clínica de um Paciente a partir dos dados da Triagem mais recente, **de forma síncrona** — a resposta da chamada de Triagem (FR-1) já inclui o Score, sem exigir uma segunda chamada. A propagação do Score para o Matching (FR-5) e para o Log Auditável (FR-8/FR-9) não pode bloquear ou atrasar essa resposta. `[ASSUMPTION: o mecanismo exato dessa propagação (evento de domínio assíncrono, chamada síncrona interna ao mesmo bounded context, ou outro) fica para bmad-architecture, à luz do Event Storming e da alternativa de "CQRS lógico" já anotada no addendum]`

**Consequences (testable):**
- O Score e o detalhamento dos fatores que o compõem (para explicabilidade, alimenta FR-8) são persistidos junto com a Triagem.
- Consultar a Triagem retorna o Score e a lista de fatores contribuintes, não apenas o número final.
- A resposta ao `POST` de Triagem (FR-1) nunca exige uma segunda chamada (polling) para obter o Score.

#### FR-4: Determinismo do Score

O algoritmo de Score é determinístico: as mesmas entradas de Triagem sempre produzem o mesmo Score.

**Consequences (testable):**
- Reexecutar o cálculo para a mesma Triagem (mesmos inputs, mesma versão do algoritmo) produz sempre o mesmo valor.
- A versão do algoritmo usada é registrada junto ao Score, para rastreabilidade caso o algoritmo evolua.

**Notes:** A validação clínica do critério de gravidade em si está fora do escopo desta entrega acadêmica — o objetivo é demonstrar o mecanismo de priorização objetiva e auditável, não substituir julgamento médico real. `[NON-GOAL for MVP]`

### 4.3 Matching Paciente–Recurso

**Descrição:** Empareia o Paciente de maior prioridade elegível com o Recurso disponível mais adequado, no espírito de apps de mobilidade ou bolsas de valores, ao longo de todo o ciclo de vida do Recurso — sugestão → confirmação/recusa → uso → Liberação, permitindo nova sugestão. Realiza UJ-2.

**Functional Requirements:**

#### FR-5: Sugestão de Matching

O sistema sugere, para um Recurso disponível, o Paciente elegível de maior Prioridade Efetiva (Score + Urgência Acumulada, ver FR-7) compatível com aquele Recurso. Esta é sempre uma sugestão — o sistema nunca aloca automaticamente; toda Alocação exige confirmação humana (FR-12). Quando um Paciente é elegível para mais de um Recurso disponível simultaneamente, o sistema escolhe entre eles por especificidade e ociosidade (ver regras de desempate abaixo).

**Regras de desempate:**
- **Entre Pacientes** com Prioridade Efetiva igual para o mesmo Recurso: vence quem tem a Triagem mais antiga (*price-time priority* — mesmo critério usado em livros de ofertas de bolsa de valores). Isso não é uma regressão à ordem de chegada como critério primário — é o mesmo princípio de justiça objetiva aplicado apenas como critério de desempate residual, quando a Prioridade Efetiva por si só não distingue os Pacientes.
- **Entre Recursos** igualmente elegíveis para o mesmo Paciente: prefere o Recurso mais específico ao caso (preserva recursos raros para quem realmente precisa deles); em empate residual (mesmo tipo/especificidade), prefere o Recurso ocioso há mais tempo. `[ASSUMPTION: a ordenação concreta de "especificidade" entre tipos de Recurso (ex.: leito comum < leito de UTI < leito de UTI especializado) fica para definição em bmad-architecture]`

**Consequences (testable):**
- Dado um Recurso disponível e uma fila com prioridades distintas, o sistema sempre sugere o Paciente de maior Prioridade Efetiva compatível com o tipo do Recurso.
- Dois Pacientes com Prioridade Efetiva idêntica: o sistema sempre sugere o de Triagem mais antiga, nunca o mais recente.
- Um Paciente elegível para um Recurso genérico e um Recurso mais específico simultaneamente livres: o sistema nunca sugere o Recurso específico se o genérico resolve o caso.
- A sugestão inclui a justificativa (Score, tempo de espera, critério de desempate aplicado) usada na decisão.
- A sugestão é recalculada a cada consulta (FR-6) e não reserva o Paciente — o mesmo Paciente pode aparecer como sugestão para mais de um Recurso ao mesmo tempo, até que uma confirmação (FR-12) o consuma. Uma tentativa de confirmar uma Alocação para um Paciente já alocado a outro Recurso é rejeitada com `409`, e o sistema recalcula automaticamente a próxima sugestão elegível para o Recurso em questão.

#### FR-6: Consulta da Fila e de Sugestões de Matching

Um Regulador pode consultar, via API, a fila atual ordenada por Prioridade Efetiva e a Sugestão de Matching para um Recurso específico.

**Consequences (testable):**
- A consulta da fila reflete, sem reprocessamento manual, qualquer Triagem nova inserida ou Recurso liberado (realiza SM-1).
- A consulta por Recurso retorna no máximo um Paciente sugerido por vez.

#### FR-12: Confirmação ou Recusa da Sugestão de Matching

Um Regulador pode confirmar a Sugestão de Matching (FR-5) como está, criando uma Alocação, ou recusá-la para aquele Paciente específico informando um motivo obrigatório. Realiza UJ-2.

**Consequences (testable):**
- Ao confirmar: o Recurso é removido do pool de Recursos disponíveis (não é mais sugerido para outro Paciente) e a Alocação é criada, gerando o registro correspondente no Log Auditável (FR-8).
- Ao recusar: o Recurso permanece disponível; o sistema aplica de novo as regras de FR-5 (incluindo desempates) e sugere o próximo Paciente elegível de maior Prioridade Efetiva para aquele mesmo Recurso.
- Recusar sem informar um motivo é rejeitado com `400` — a recusa também é uma decisão e precisa ser explicável (alimenta FR-8/FR-9).
- O Paciente recusado para um Recurso específico permanece na fila normalmente, com a mesma Prioridade Efetiva — a recusa não penaliza nem reinicia sua posição; ele segue elegível para o próximo Recurso compatível.

**Out of Scope:** Seleção manual livre de qual Paciente alocar a um Recurso, ignorando a ordem de prioridade objetiva do sistema — isso quebraria o critério objetivo que é o diferencial central do produto. `[NON-GOAL for MVP]`

#### FR-13: Liberação de Recurso

Um Recurso alocado (FR-12) retorna automaticamente ao pool de Recursos disponíveis após decorrido um tempo de atendimento simulado, tornando-se elegível para uma nova Sugestão de Matching (FR-5). Realiza UJ-2.

**Consequences (testable):**
- A Liberação gera um registro no Log Auditável (FR-8), fechando o ciclo sugestão → confirmação → uso → Liberação para aquele Recurso.
- Um Recurso liberado volta ao pool com o mesmo tipo/especificidade de antes — a Liberação não muda sua categoria.
- O dataset de demonstração (FR-10) inclui pelo menos um Recurso cujo tempo de atendimento simulado se completa durante a gravação da demo, para que o ciclo completo (uso → Liberação → nova sugestão) seja observável sem esperar em tempo real.

`[ASSUMPTION: a duração exata do "tempo de atendimento simulado" (por tipo de Recurso) fica para calibração em bmad-architecture/implementação, de forma análoga ao k/teto do Aging (FR-7) — deve ser curta o suficiente para completar ao menos um ciclo dentro do vídeo de demonstração]`

**Out of Scope:** Liberação manual antecipada de um Recurso pelo Regulador (ex.: "paciente teve alta antes do previsto") — a Liberação é sempre automática, baseada no tempo de atendimento simulado. `[NON-GOAL for MVP]`

### 4.4 Priorização por Urgência Acumulada

**Descrição:** Garante que Pacientes de prioridade moderada não fiquem represados indefinidamente atrás de casos sempre "mais urgentes". Realiza UJ-1, UJ-4.

**Functional Requirements:**

#### FR-7: Aging da Prioridade Efetiva

O sistema ajusta a prioridade efetiva de um Paciente em espera aplicando um fator de Urgência Acumulada **linear e com teto** (`prioridade_efetiva = score + min(k × tempo_de_espera, teto)`), proporcional ao tempo de espera desde a Triagem. O `teto` é expresso como **no máximo 20% da amplitude total possível do Score** (não como um valor absoluto), garantindo por regra que a Urgência Acumulada nunca seja suficiente para inverter uma diferença de gravidade real significativa entre dois Pacientes. `k` é calibrado para que esse teto seja atingido após um tempo de espera de **12 a 24 horas simuladas**. `[ASSUMPTION: conversão de "20% da amplitude do Score" e de "12–24h" para os valores absolutos concretos de k/teto fica para bmad-architecture, quando a fórmula e a escala definitiva do Score forem fechadas]`

**Consequences (testable):**
- Dois Pacientes com Score inicial idêntico: o que espera há mais tempo tem prioridade efetiva igual ou maior, nunca menor.
- A prioridade efetiva de um Paciente nunca ultrapassa `score + 20% da amplitude total do Score` — o crescimento por espera é limitado, não indefinido.
- O teto é atingido entre 12 e 24 horas de espera simulada, nem antes nem depois desse intervalo.
- Um Paciente de prioridade moderada, no dataset de demonstração, muda de posição visivelmente na fila ao longo do tempo simulado sem qualquer reprocessamento manual (realiza SM-3).
- O recálculo da fila ao inserir um novo Paciente de alta urgência não exige reprocessamento manual (realiza SM-1).

### 4.5 Log de Fila Auditável

**Descrição:** Toda decisão de priorização, sugestão, confirmação, recusa e Liberação fica registrada de forma explicável — o diferencial central do produto. Realiza UJ-3, UJ-4.

**Functional Requirements:**

#### FR-8: Registro de Decisão

Toda vez que um Score é calculado (FR-3), uma Sugestão de Matching é gerada (FR-5), uma Alocação é confirmada ou recusada (FR-12), ou um Recurso é liberado (FR-13), o sistema grava um registro de Log Auditável com os fatores que levaram à decisão (incluindo o motivo, no caso de recusa), timestamp e identificação (por ID interno) do Paciente/Recurso envolvidos.

**Consequences (testable):**
- Nenhuma mudança de Score, Sugestão de Matching, confirmação, recusa ou Liberação ocorre sem um registro correspondente no Log Auditável. Garantias de entrega sob falha parcial de um serviço (retries, reconciliação) ficam para `bmad-architecture` — ver NFR de Alta Disponibilidade (§7). `[ASSUMPTION: mecanismo de entrega garantida (at-least-once + reconciliação) do registro de auditoria fica para bmad-architecture]`
- O registro nunca é alterado retroativamente — apenas novos registros são adicionados (append-only).

#### FR-9: Consulta de Auditoria

Um Auditor pode consultar, via API, o histórico completo de decisões para um Paciente ou Recurso específico (por CPF, na fronteira de consulta, ou pelo ID interno do Paciente), incluindo a justificativa legível de cada decisão.

**Consequences (testable):**
- 100% das decisões (sugestão, confirmação, recusa e Liberação) no dataset de demonstração são explicáveis por meio deste endpoint, por desenho do próprio dataset (realiza SM-2).
- A consulta permite comparar, para dois Pacientes, por que um foi atendido antes do outro (cenário-chave de UJ-3).
- A resposta identifica o Paciente pelo seu ID interno; o CPF, quando exibido, aparece mascarado (ver §8 Constraints/LGPD) — a consulta nunca expõe o CPF em texto claro, mesmo sendo um uso legítimo de auditoria, não depuração.

### 4.6 Camada Adaptadora (Ingestão Simulada SISREG/DATASUS)

**Descrição:** Isola o domínio de uma futura integração real com sistemas oficiais do SUS, usando dados sintéticos nesta fase. Realiza UJ-2 (disponibilidade de Recursos).

**Functional Requirements:**

#### FR-10: Carga de Dados Sintéticos

O sistema carrega, via seed reproduzível, dados sintéticos de unidades de saúde, leitos e especialistas, através de uma Camada Adaptadora que simula a integração com SISREG/DATASUS.

**Consequences (testable):**
- Subir o ambiente do zero (um único comando) deixa o sistema com um dataset de demonstração pronto para uso, sem passos manuais adicionais.
- A interface da Camada Adaptadora é desenhada de forma que uma integração real futura possa substituí-la sem alterar a lógica de domínio de Score/Matching/Log (alimenta `bmad-architecture`).
- O seed inclui, no mínimo, ~12–15 Pacientes sintéticos (cobrindo pelo menos 3 níveis de gravidade) e ~6–8 Recursos (leitos/especialistas, em pelo menos 2 tipos), com escassez deliberada — menos Recursos do que Pacientes elegíveis simultaneamente. O volume pode ser ampliado além desse mínimo se ajudar a demonstrar mais cenários do sistema em ação.
- O seed inclui pelo menos um par de Pacientes com Score empatado (demonstra o desempate por Triagem mais antiga, FR-5) e pelo menos um Paciente elegível simultaneamente para um Recurso genérico e um específico (demonstra o desempate best-fit, FR-5).
- Os timestamps de Triagem no seed são retroativos (ex.: registrados como se tivessem ocorrido horas antes do momento do seed), para que a Urgência Acumulada (FR-7) já apareça refletida na fila imediatamente após o seed, sem exigir espera em tempo real durante a demonstração.
- Pelo menos um Recurso já é alocado no momento do seed com um tempo de atendimento simulado próximo do fim, para que a Liberação (FR-13) e a sugestão seguinte ocorram de forma observável durante a gravação da demo.

**Out of Scope:** Qualquer chamada real a SISREG, SIH/SUS, e-SUS APS ou DATASUS.

### 4.7 Autenticação Simplificada

**Descrição:** Protege os endpoints sem ser o foco de inovação desta entrega.

**Functional Requirements:**

#### FR-11: Autenticação via Serviço Dedicado (Login Mockado)

Um usuário sintético pré-cadastrado (representando Regulador, Profissional de Triagem ou Auditor) autentica-se via `POST /login` com usuário e senha mockados contra um serviço de autenticação dedicado, recebendo um token assinado; a API exige esse token para acessar qualquer endpoint que não seja público/de health-check/de login.

**Consequences (testable):**
- Login com credenciais inválidas retorna `401`.
- Requisições sem token válido (ausente, expirado ou com assinatura inválida) para endpoints protegidos retornam `401`.
- Não há distinção de papéis (Regulador vs. Profissional de Triagem vs. Auditor) exigida nesta fase — qualquer token válido acessa qualquer endpoint, independentemente do papel do usuário autenticado. `[ASSUMPTION]` As referências a "Regulador", "Profissional de Triagem" e "Auditor" ao longo deste documento (§4) descrevem o ator pretendido de cada ação, não uma restrição de autorização tecnicamente aplicada.

**Out of Scope:** Auto-registro de usuário, CRUD de usuário via API, OAuth/SSO real, RBAC por papel aplicado, expiração/rotação de token de nível produção — autenticação e autorização completas de nível produção ficam para depois do MVP (ver Non-Goals).

## 5. Non-Goals (Explicit)

- Interoperabilidade real com SISREG, SIH/SUS, e-SUS APS, DATASUS ou qualquer sistema oficial do SUS nesta fase.
- Frontend / interface de usuário de qualquer tipo — a entrega é backend-only, demonstrável via Swagger/Postman.
- Agendamento inteligente e redução de no-show (parqueado para v2, conforme brief).
- Score de risco de deterioração clínica pós-priorização (parqueado para v2, conforme brief).
- Autenticação e autorização completas de nível produção (RBAC aplicado, SSO, OAuth real, CRUD de usuário via API) — login mockado contra usuários sintéticos pré-cadastrados, com token assinado emitido por um serviço dedicado, é suficiente nesta fase.
- Validação clínica formal do algoritmo de Score — é uma demonstração de mecanismo, não um dispositivo médico.
- Seleção manual livre de qual Paciente alocar a um Recurso, ignorando a ordem de prioridade objetiva do sistema (o Regulador só confirma a sugestão ou a recusa — ver FR-12).
- Liberação manual antecipada de um Recurso pelo Regulador — a Liberação é sempre automática, baseada em tempo de atendimento simulado (ver FR-13).
- Conformidade legal plena com a LGPD (base legal documentada para dado sensível de saúde, política de retenção/eliminação, encarregado/DPO) — o MVP segue princípios de minimização por boa prática, mas não constitui compliance formal; seria pré-requisito antes de qualquer uso com dados reais (ver §8 Constraints).

## 6. MVP Scope

### 6.1 In Scope

- Fluxo único ponta a ponta: Triagem → Score → Sugestão de Matching → confirmação ou recusa → uso → Liberação → Log Auditável (FR-1 a FR-9, FR-12, FR-13).
- Camada Adaptadora com dados de unidades de saúde, leitos e especialistas via seed sintético (FR-10).
- CPF como chave de identificação do Paciente; dados demográficos mockados.
- Autenticação simplificada por token mockado (FR-11).
- Persistência simples, com dataset pequeno o suficiente para a demonstração em vídeo rodar sem falhas.
- Empacotamento reproduzível — subir o sistema inteiro com um único comando (ver §7 NFRs).
- Demonstração via Swagger/coleção Postman.

### 6.2 Out of Scope for MVP

- Tudo listado em §5 Non-Goals.

## 7. Cross-Cutting NFRs

- **Continuidade sob falha parcial:** o fluxo de Triagem continua aceitando e pontuando registros mesmo se o serviço de Matching ou o de Log Auditável estiver temporariamente indisponível — a propagação do Score/decisões para esses serviços (FR-3, FR-8) é processada quando eles voltarem, não perdida. Não se exige alta disponibilidade de infraestrutura full-produção (multi-AZ, failover automático) — redundância e resiliência ficam proporcionais ao escopo do hackathon; mecanismos concretos ficam para `bmad-architecture`, que já tem notas de Event Storming/bounded contexts no addendum.
- **Observabilidade mínima:** logs estruturados e health-check por serviço. `[ASSUMPTION: campos de log e formato exato do health-check ficam para bmad-architecture]`
- **Reprodutibilidade:** todo o sistema (serviços + seed de dados) sobe com um único comando, sem passos manuais, para garantir que a demo em vídeo não falhe.
- **Contratos versionados:** comunicação entre serviços usa contratos versionados (prática de mercado, conforme addendum), evitando quebras silenciosas entre Triagem/Score, Matching e Log.
- **Testes automatizados:** cobertura de linha ≥90% (JaCoCo) na camada de domínio de cada microsserviço, medida por serviço — não uma média agregada do sistema nem aplicada à camada de infraestrutura/adapters. Complementada por: teste de mutação (ex.: PIT) na mesma camada de domínio, para validar que os testes realmente pegam bugs e não só executam linhas; testes de integração cobrindo os contratos entre serviços e a Camada Adaptadora; e testes de aceitação em BDD (Gherkin/Cucumber-JVM) cobrindo o fluxo único ponta a ponta (Triagem → Score → Sugestão de Matching → confirmação/recusa → uso → Liberação → Log Auditável) descrito em §6.1 — os cenários Given/When/Then derivam diretamente das "Consequences (testable)" de cada FR em §4, servindo tanto de especificação executável quanto de suíte end-to-end. Esta é uma decisão firme desta fase, escolhida por melhor prática de mercado (risco/criticidade do domínio) — coincide com, mas não deriva automaticamente de, o precedente da Fase 4 do mesmo aluno.
- **Segredos fora do código:** credenciais e segredos de configuração não ficam versionados no repositório (herdado do addendum).

## 8. Constraints and Guardrails

**Cost**
- O projeto é pago do próprio bolso do aluno — toda decisão de infraestrutura deve privilegiar baixo custo e fácil desligamento (Free Tier onde possível, recursos pausáveis/destruíveis fora da janela de demo).
- Evitar serviços gerenciados caros de operação contínua (ex.: MSK, RDS multi-AZ permanente, NAT Gateway 24/7) salvo necessidade estrita, justificada na arquitetura.
- Manter (ou evoluir) o padrão de scripts `deploy/pause/destroy` já validado no precedente da Fase 4 do mesmo aluno.

**Privacy / Dados (LGPD by design)**
- Todos os dados de Pacientes usados no MVP são sintéticos — nenhum dado real de paciente do SUS circula em nenhum ambiente (dev, demo ou repositório).
- Ainda que sintéticos, o CPF (dado pessoal identificador) e os dados clínicos de Triagem — sintomas, sinais vitais, gravidade (dado sensível de saúde, LGPD Lei 13.709/2018 art. 5º II) — são tratados seguindo os princípios de minimização e limitação de propagação da LGPD, como decisão de design desta fase, não apenas como boa prática opcional.
- O CPF **não transita** pelo sistema além da fronteira de ingestão: ele é usado somente em FR-1 (entrada) e FR-2 (resolução/criação do Paciente), onde é imediatamente convertido em um ID de Paciente interno. Score (FR-3), Sugestão de Matching (FR-5), Log Auditável (FR-8) e qualquer evento interno referenciam exclusivamente esse ID — nunca o CPF.
- Toda resposta de API que identifica um Paciente (incluindo a consulta de auditoria, FR-9) usa o ID interno como identificador primário; o CPF, quando exibido, aparece mascarado (nunca em texto claro). `[ASSUMPTION: formato exato da máscara de CPF e se existe um lookup separado e mais privilegiado para o CPF completo ficam para bmad-architecture]`
- **Escopo honesto sobre LGPD:** como todo o dataset é sintético, não há titular de dados real e a LGPD provavelmente não se aplica a este MVP em sentido estrito. O design acima segue os *princípios* de minimização e limitação de propagação da LGPD por boa prática de engenharia, não constitui uma alegação de conformidade legal plena. Conformidade legal plena — base legal documentada para dado sensível de saúde (LGPD art. 11), política de retenção/eliminação (art. 18), encarregado (DPO), controle de acesso auditado sobre qualquer lookup privilegiado de CPF completo — fica fora do escopo deste MVP e seria pré-requisito antes de qualquer uso com dados reais de pacientes.

## 9. Success Metrics

*Nota — critérios de avaliação do edital (contexto acadêmico, não métricas de produto): Problema/Impacto 20%, Inovação 20% (o Log Auditável como diferencial central), Funcionalidade do MVP 30% (fluxo ponta a ponta confiável via Swagger/Postman), Apresentação 20%, Documentação 10%. As Success Metrics abaixo validam a Funcionalidade do MVP e a Inovação.*

**Primary**
- **SM-1**: Recálculo correto da fila ao inserir um novo Paciente de alta urgência ou liberar um Recurso, sem exigir reprocessamento manual. Validates FR-6, FR-7, FR-13.
- **SM-2**: 100% das decisões (Sugestão de Matching, confirmação, recusa e Liberação) registradas no Log Auditável são explicáveis (fatores documentados e consultáveis) nos casos do dataset de demonstração, por desenho (FR-10 garante que os cenários de empate e escassez estejam representados). Validates FR-8, FR-9, FR-12, FR-13.

**Secondary**
- **SM-3**: Nenhum Paciente de prioridade moderada fica "esquecido" indefinidamente no dataset de demonstração — a Urgência Acumulada garante progressão visível ao longo do tempo simulado. Validates FR-7.

**Counter-metrics (do not optimize)**
- **SM-C1**: A Urgência Acumulada não deve, sistematicamente, fazer um Paciente de prioridade moderada ultrapassar um Paciente de gravidade clínica real mais alta apenas por tempo de espera — garantido pelo teto do aging (FR-7), não apenas por sorte no dataset de demo. Counterbalances SM-3 / FR-7.

`[ASSUMPTION: valores/critérios acima herdados do brief como ilustrativos; a composição-alvo do dataset de demo já está definida (§4.6 FR-10), mas a validação com números reais só ocorre quando o seed concreto for implementado]`

## 10. Open Questions

Nenhuma pendência bloqueante para esta fase. Várias decisões técnicas foram conscientemente deferidas para `bmad-architecture` — elas não bloqueiam este PRD, mas não estão numericamente fechadas; ver "Assumptions Abertas" em §11.

## 11. Assumptions Index

### Decisões Fechadas (log de rastreabilidade)

*Já confirmadas nesta fase — listadas aqui apenas para rastreabilidade, não são pendências.*

- §4.6 FR-10 — Composição-alvo do seed de demonstração (~12–15 Pacientes, ~6–8 Recursos, escassez deliberada, timestamps retroativos), podendo ser ampliada para demonstrar mais cenários.
- §4.3 FR-12 — Confirmação/recusa da Sugestão de Matching definida como ação humana explícita (sem seleção manual livre), resolvendo a ambiguidade sobre autonomia do Matching.
- §4.3 FR-13 — Liberação de Recurso é sempre automática (baseada em tempo de atendimento simulado), sem liberação manual antecipada.
- §7 NFRs — ≥90% JaCoCo no domínio por serviço + mutação (PIT) + integração + aceitação BDD é decisão firme desta fase, por melhor prática de mercado (não herança automática da Fase 4, embora coincida no número).
- §8 Constraints — CPF tratado como dado pessoal identificador e Triagem como dado sensível de saúde (LGPD) é decisão de design desta fase, não apenas boa prática opcional; CPF não transita além da fronteira de ingestão (FR-1/FR-2).
- §9 Success Metrics — Valores/critérios do brief mantidos como ilustrativos por decisão do usuário; validação com números reais ocorre quando o seed concreto for implementado (não é uma incerteza a resolver, é a natureza de uma métrica antes do dado real existir).

### Assumptions Abertas (a confirmar em `bmad-architecture`)

- §4.4 FR-7 — Forma da Urgência Acumulada decidida (linear com teto relativo de 20% da amplitude do Score, atingido em 12–24h simuladas); conversão para valores absolutos de `k`/`teto` fica para quando a fórmula/escala definitiva do Score for fechada.
- §4.2 FR-3 — Capability decidida (resposta síncrona, propagação não-bloqueante); mecanismo exato (evento assíncrono, chamada interna síncrona, etc.) fica para o Event Storming.
- §4.1 FR-1 — Faixas fisiológicas plausíveis concretas (sinais vitais) não definidas.
- §4.3 FR-5 — Ordenação concreta de "especificidade" entre tipos de Recurso não definida.
- §4.3 FR-13 — Duração exata do tempo de atendimento simulado (por tipo de Recurso) não definida.
- §4.5 FR-8 — Mecanismo de entrega garantida (at-least-once + reconciliação) do registro de auditoria sob falha parcial não definido.
- §4.7 FR-11 — Nenhuma distinção de papéis (RBAC) exigida no MVP; qualquer token válido acessa qualquer endpoint — postura consciente, mas revisitável se a banca exigir isolamento por papel. Mecanismo de emissão do token (serviço de autenticação dedicado, login mockado contra usuários pré-cadastrados) definido em `bmad-architecture` (AD-14), reabrindo e substituindo a decisão original de bearer estático.
- §8 Constraints — Formato exato da máscara de CPF e existência de um lookup privilegiado para CPF completo não definidos.
