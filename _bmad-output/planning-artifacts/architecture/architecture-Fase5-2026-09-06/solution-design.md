# Solution Design — FilaJusta

> Documento de leitura humana, derivado da spine de arquitetura (`ARCHITECTURE-SPINE.md`, status: final) e do memlog da fase. Enquanto a spine é o contrato terso para quem for construir, este documento existe para explicar o "porquê" a quem for avaliar: a banca do hackathon FIAP Pós-Tech Fase 5.

## 1. Contexto e objetivo

O FilaJusta é o motor de backend que decide, de forma objetiva e auditável, quem deve ser atendido a seguir em uma fila de saúde do SUS — substituindo a ordem de chegada por um Score de prioridade clínica calculado na triagem, e emparelhando cada paciente ao recurso disponível mais adequado em tempo real. O diferencial não é o mecanismo de matching em si, mas a combinação de priorização objetiva **com** explicabilidade desde o design: toda decisão de alocação gera um registro que responde "por que fulano foi atendido antes de mim".

A arquitetura adota microsserviços deliberadamente — não porque o volume de dados do MVP (dezenas de pacientes, poucos recursos) exija escala, mas porque é um salto de complexidade arquitetural intencional em relação ao monólito modular da entrega anterior do mesmo aluno (Fase 4), usando Spring Cloud, CQRS e Event Storming como o addendum técnico pediu. Cada bounded context vira um serviço com fronteira de dados própria, comunicação assíncrona por eventos onde a continuidade importa, e uma fronteira de privacidade (CPF) fisicamente isolada — não apenas documentada.

## 2. C4 Nível 1 — Contexto do Sistema

```mermaid
graph TB
  PT[Profissional de Triagem]
  RG[Regulador]
  AD[Auditor]

  subgraph Sistema["FilaJusta (backend)"]
    FJ[Motor de Priorização e Alocação]
  end

  SISREG["SISREG / DATASUS<br/>(simulado nesta fase — FR-10)"]

  PT -->|"POST triagem (CPF, sinais vitais)"| Sistema
  RG -->|"consulta fila / confirma ou recusa sugestão"| Sistema
  AD -->|"consulta log auditável"| Sistema
  Sistema -.->|"carga sintética, sem chamada real"| SISREG
```

Os três atores diretos (Profissional de Triagem, Regulador, Auditor) acessam o sistema via API — não há frontend nesta fase (PRD §2.2). O Paciente é beneficiário indireto, representado nas respostas do sistema mas sem acesso direto. A integração com SISREG/DATASUS é inteiramente simulada: a fronteira existe na arquitetura (para uma troca futura), mas nenhuma chamada real ocorre.

## 3. C4 Nível 2 — Contêineres

```mermaid
graph TB
  Client["Cliente API<br/>(Swagger/Postman)"]

  subgraph FilaJusta["FilaJusta"]
    GW["gateway-service<br/>(Spring Cloud Gateway)"]
    TS["triagem-score-service<br/>(Spring Boot)"]
    MA["matching-alocacao-service<br/>(Spring Boot)"]
    AU["auditoria-service<br/>(Spring Boot)"]
    SEED["seed-adapter<br/>(Lambda, Quarkus)"]
    PG[("PostgreSQL 18<br/>schema por serviço")]
    MSG[["SNS FIFO + SQS FIFO + DLQ"]]
  end

  Client -->|"REST + token mockado"| GW
  SEED -->|"REST + token mockado"| GW
  GW -->|REST| TS
  GW -->|REST| MA
  GW -->|REST| AU

  TS -->|"JDBC (schema triagem_score)"| PG
  MA -->|"JDBC (schema matching_alocacao)"| PG
  AU -->|"JDBC (schema auditoria)"| PG

  TS -->|"outbox → publica evento"| MSG
  MA -->|"outbox → publica evento"| MSG
  MSG -->|"consome ScoreCalculado"| MA
  MSG -->|"consome ScoreCalculado, eventos de Matching"| AU

  AU -.->|"gRPC: ResolveCpfParaId / ObterCpfMascarado"| TS
```

Quatro contêineres de runtime (gateway + 3 serviços de domínio), um job serverless (`seed-adapter`), um banco compartilhado com isolamento lógico por schema, e um backbone de mensageria assíncrona. Cada seta carrega o protocolo explícito — é uma escolha deliberada: onde a comunicação é síncrona (REST, gRPC), a latência é assumida; onde é assíncrona (SNS/SQS), a indisponibilidade momentânea de um consumidor não derruba o produtor.

## 4. C4 Nível 3 — Componentes (`matching-alocacao-service`)

`matching-alocacao-service` é o contêiner mais interessante para detalhar: concentra a regra de negócio central do produto (a sugestão de matching e seus desempates) e os três padrões de integração da arquitetura (evento consumido, comando interno disparado por fila, gRPC não aplicável aqui mas API pública exposta).

```mermaid
graph TB
  subgraph MA["matching-alocacao-service"]
    subgraph domain["domain"]
      D1["Recurso"]
      D2["SugestaoMatching"]
      D3["Alocacao"]
      D4["Aging / PrioridadeEfetiva"]
    end
    subgraph app["application"]
      subgraph cmd["command"]
        C1["ConfirmarAlocacao"]
        C2["RecusarSugestao"]
        C3["LiberarRecurso (interno)"]
      end
      subgraph qry["query"]
        Q1["ConsultarFila"]
        Q2["ConsultarSugestao"]
      end
    end
    subgraph infra["infrastructure"]
      I1["web (REST controllers)"]
      I2["persistence (schema matching_alocacao)"]
      I3["outbox (publisher SNS FIFO)"]
      I4["sqs-consumer (réplica de Score)"]
    end
  end

  I1 --> C1
  I1 --> C2
  I1 --> Q1
  I1 --> Q2
  I4 -->|"upsert idempotente, last-write-wins"| D4
  C1 --> D3
  C1 --> I3
  C2 --> D2
  C3 --> D1
  C3 --> I3
  Q1 --> D4
  Q2 --> D2
  D1 --> I2
  D3 --> I2
```

`LiberarRecurso` não tem uma seta partindo de `web/` — é deliberado (AD-6): o único gatilho é a expiração de uma mensagem SQS de delay, nunca um endpoint REST. `sqs-consumer` alimenta apenas a réplica local de Score/Aging (`D4`), nunca o `domain` de Recurso/Alocação — o serviço nunca recalcula ou corrige um Score, só o lê.

## 5. Decisões arquiteturais principais

### AD-1 — Três serviços, não quatro

O addendum listava quatro bounded contexts prováveis, incluindo "Ingestão/Adaptador". Na prática, esse contexto não tem consultas nem estado próprio de longo prazo — é uma carga única disparada no deploy (FR-10). Transformá-lo num serviço sempre ativo custaria uma task ECS rodando 24/7 sem nunca atender uma requisição de negócio. Virou `seed-adapter`, um job Lambda que entra pelo mesmo gateway que qualquer outro cliente, com o mesmo token mockado — sem privilégio especial de acesso direto aos outros serviços.

### AD-2 — CQRS lógico, não físico

O addendum pedia para avaliar CQRS por serviço, não aplicá-lo cegamente. Com dezenas de registros no MVP, um read-model separado seria complexidade sem retorno. A decisão documentada é: comandos e consultas em pacotes distintos, mesmo banco — e se o produto crescesse para produção real, `matching-alocacao-service` seria o primeiro candidato a um read-model dedicado, porque a fila é consultada ordens de magnitude mais que é escrita.

### AD-3 — Outbox + SNS/SQS FIFO em vez de chamada síncrona

O PRD exige que a resposta da Triagem nunca espere por Matching ou Auditoria, e que nenhum evento se perca se um desses serviços cair. Uma chamada gRPC síncrona resolveria a latência, mas não a resiliência: se `auditoria-service` estivesse fora do ar, a chamada falharia. A escolha foi então: gravar o evento na mesma transação do comando (outbox) e publicar de forma assíncrona — o consumidor processa quando voltar. FIFO (em vez de standard) foi escolhido para dar ordem determinística por paciente/recurso sem depender de heurísticas de reconciliação. O ponto mais sutil, encontrado só na revisão adversarial da spine: o `eventId` precisa ser gerado no momento em que a linha do outbox é gravada — não quando o relay publica — senão uma nova tentativa após falha de rede geraria um ID novo para o mesmo fato, quebrando a deduplicação que a Auditoria depende para nunca duplicar um registro.

### AD-4 — Score é dono único, réplicas são somente-leitura

Só `triagem-score-service` calcula e persiste o Score. `matching-alocacao-service` mantém uma cópia local, mas nunca a corrige — ela existe só para que a fila possa ser consultada sem uma chamada síncrona a cada leitura. A Prioridade Efetiva (Score + Aging) é sempre recalculada a partir dessa cópia no momento da consulta, nunca cacheada em memória — isso importa porque o serviço pode rodar em múltiplas instâncias simultâneas (ECS Fargate escalável), e um valor cacheado divergiria entre instâncias.

### AD-6 — Liberação de Recurso via mensagem de delay, não scheduler externo

A duração do atendimento simulado (2–5 minutos de relógio real, não horas simuladas aceleradas) é curta o suficiente para caber dentro da gravação da demo em vídeo. O limite físico de 15 minutos do `DelaySeconds` do SQS foi verificado durante a revisão adversarial da spine — os valores escolhidos folgam bastante dessa margem, mas a regra documenta o teto para não ser violada por um tipo de recurso futuro com atendimento mais longo.

### AD-7 — CPF nunca sai da fronteira de ingestão

É a decisão mais visivelmente ligada ao diferencial de auditabilidade do produto: se o CPF vazasse para o Log Auditável ou para o Matching, qualquer vazamento de dados nesses serviços exporia identificação direta de paciente. A regra vai além do CPF — nenhum atributo de identificação direta (nome, endereço, contato) pode sair do serviço de ingestão, fechando uma brecha que um desenvolvedor bem-intencionado poderia abrir sem perceber (ex.: adicionar o nome do paciente a um evento "só para a UI mostrar bonito").

### AD-12 — Isolamento de rede por security group, não por subnet privada

A decisão mais orientada a custo do documento: uma VPC com subnet pública única, sem NAT Gateway (explicitamente vetado como custo recorrente pelo PRD/addendum), usando security groups para impedir que qualquer coisa além do gateway alcance as portas HTTP dos serviços de domínio. É uma troca consciente — perde-se a defesa em profundidade de uma subnet privada verdadeira, mas ganha-se custo zero de NAT fora da rede de laboratório do hackathon.

## 6. Fluxo ponta a ponta

```mermaid
sequenceDiagram
  actor PT as Profissional de Triagem
  actor RG as Regulador
  participant GW as gateway-service
  participant TS as triagem-score-service
  participant MSG as SNS/SQS FIFO
  participant MA as matching-alocacao-service
  participant AU as auditoria-service

  PT->>GW: POST /triagens (CPF, sinais vitais)
  GW->>TS: encaminha (token validado)
  TS->>TS: resolve/cria Paciente (CPF→pacienteId)
  TS->>TS: calcula Score (síncrono)
  TS-->>GW: 201 (Score + fatores)
  GW-->>PT: 201 (Score + fatores)
  TS->>MSG: outbox publica ScoreCalculado (mesma transação)
  MSG->>MA: consome ScoreCalculado (réplica local)
  MSG->>AU: consome ScoreCalculado (registro de auditoria)

  RG->>GW: GET /recursos/{id}/sugestao
  GW->>MA: encaminha
  MA->>MA: calcula Prioridade Efetiva, aplica desempates
  MA-->>RG: Sugestão (paciente, justificativa)

  RG->>GW: POST /recursos/{id}/alocacoes (confirmar)
  GW->>MA: encaminha
  MA->>MA: cria Alocação, remove Recurso do pool
  MA->>MSG: outbox publica AlocacaoConfirmada
  MA->>MSG: agenda mensagem de delay (duração do atendimento)
  MSG->>AU: consome AlocacaoConfirmada (registro de auditoria)

  Note over MSG,MA: ... decorrido o tempo de atendimento simulado ...
  MSG->>MA: mensagem de delay expira
  MA->>MA: LiberarRecurso (interno, não-REST) — Recurso volta ao pool
  MA->>MSG: outbox publica RecursoLiberado
  MSG->>AU: consome RecursoLiberado (registro de auditoria, fecha o ciclo)
```

O ciclo Triagem → Score → Sugestão → Confirmação → Alocação → uso → Liberação → Log Auditável nunca depende de uma chamada síncrona entre `triagem-score-service` e `matching-alocacao-service`, nem de um passo manual para liberar um recurso — os dois pontos que a arquitetura mais protege contra divergência (AD-3 e AD-6).

## 7. Rastreabilidade de requisitos

| Requisito | Contêiner / Componente | Decisões que governam |
| --- | --- | --- |
| FR-1 Registro de Triagem | `triagem-score-service` → `infrastructure/web` | AD-1, AD-2, AD-11 |
| FR-2 Identificação mínima do Paciente | `triagem-score-service` → `domain` (Paciente) | AD-1, AD-7 |
| FR-3 Cálculo do Score | `triagem-score-service` → `application/command` | AD-3, AD-4 |
| FR-4 Determinismo do Score | `triagem-score-service` → `domain` (Score) | AD-4 |
| FR-5 Sugestão de Matching | `matching-alocacao-service` → `application/query` | AD-4, AD-5 |
| FR-6 Consulta da fila e sugestões | `matching-alocacao-service` → `application/query` | AD-2, AD-10 |
| FR-7 Aging da Prioridade Efetiva | `matching-alocacao-service` → `domain` (Aging) | AD-4 |
| FR-8 Registro de decisão | `auditoria-service` → `application/query` (consumidor de eventos) | AD-3, AD-10 |
| FR-9 Consulta de auditoria | `auditoria-service` → `infrastructure/grpc-client` | AD-7, AD-10 |
| FR-10 Carga de dados sintéticos | `seed-adapter` (job Lambda) | AD-1, AD-8 |
| FR-11 Autenticação por token mockado | `gateway-service` | AD-8, AD-12 |
| FR-12 Confirmação/recusa da sugestão | `matching-alocacao-service` → `application/command` | AD-1, AD-6 |
| FR-13 Liberação de Recurso | `matching-alocacao-service` → `application/command` (`LiberarRecurso`, interno) | AD-6 |

Isolamento por schema (AD-9) e topologia de rede (AD-12) são invariantes transversais — aplicam-se a todas as linhas acima, mesmo onde não citados explicitamente.
