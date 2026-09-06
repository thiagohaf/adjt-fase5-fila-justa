# Reconciliação — ARCHITECTURE-SPINE.md × addendum.md

**Verdict:** Majoritariamente fiel ao addendum, mas com 3 gaps reais (custo/rede, Quarkus, CQRS "avaliado por serviço") e 1 imprecisão de rotulagem (Event Storming) que vale corrigir antes do Finalize.

## Gaps

- **NAT Gateway 24/7 — constraint explícita, mas topologia de rede nunca é decidida.** O addendum chama de "crítica" a restrição de custo e nomeia NAT Gateway 24/7 como algo a evitar "salvo necessidade estrita, justificada". A spine tem AD-9 (Postgres) e o diagrama de deployment, mas nenhum AD decide VPC/subnets/egress — sem isso, o default de infraestrutura (ECS Fargate em subnet privada) *implica* NAT Gateway por padrão. É uma dimensão estrutural inteira (topologia de rede) deixada em silêncio, não em Deferred. **Sugestão:** adicionar um AD ou linha em Deferred decidindo explicitamente subnets públicas com security group restritivo (sem NAT) ou VPC endpoints para SNS/SQS/S3, já que é exatamente o tipo de decisão que o addendum pede para ser justificada.

- **Quarkus descartado sem reconhecer o trade-off do precedente.** O addendum cita "pode misturar conforme o serviço, como no precedente da Fase 4... que usou Quarkus tanto na API quanto nas Lambdas" — citando Quarkus especificamente para Lambdas (cold start/memória menores com compilação nativa). A tabela Stack da spine usa Spring Boot uniformemente, inclusive para o `seed-adapter` (Lambda), sem mencionar Quarkus nem justificar a troca. Não é necessariamente errado optar por uma stack única por simplicidade, mas a decisão de abandonar a otimização de cold-start do precedente para a única Lambda do sistema é uma escolha que a spine faz silenciosamente. **Sugestão:** um AD curto ou nota em Deferred justificando "Spring Boot uniforme por simplicidade de manutenção (uma stack só), aceitando cold-start maior na Lambda de seed — irrelevante pois roda uma vez no bootstrap, não em request-path".

- **CQRS "avaliado por serviço" virou CQRS lógico uniforme sem registro da avaliação.** O addendum pede avaliação por serviço ("nem todos precisarão de CQRS pleno... pode ser CQRS lógico onde o overhead de um read-model dedicado não se justificar"), implicando que a avaliação pode concluir diferente por serviço. AD-2 aplica CQRS lógico a todos os 3 serviços em bloco, sem registrar por que nenhum deles justificaria um read-model físico dedicado (ex.: `matching-alocacao-service`, que serve a consulta de fila em tempo real sob potencial concorrência, seria o candidato mais plausível a precisar de um). Não invalida a decisão — é defensável na escala do hackathon — mas falta o registro da avaliação que o addendum pede explicitamente.

## Imprecisão de rotulagem (menor)

- **"Event Storming a partir do PRD" (Design Paradigm) superestima o que foi feito.** O addendum pede Event Storming "conduzido explicitamente como atividade de descoberta antes de fechar os limites dos microsserviços" — uma técnica colaborativa de descoberta. O Fast path na prática reaproveitou a decomposição de features já pronta no PRD (§4), não conduziu uma sessão de Event Storming de fato. Isso também explica por que a spine fechou em 3 bounded contexts de runtime em vez dos 4 "prováveis" citados no addendum (Triagem/Score, Matching/Alocação, Auditoria/Log de Fila, **Ingestão/Adaptador**) — a spine dobrou Ingestão/Adaptador em um job não-runtime (decisão registrada e defensável em AD-1), mas o texto do Design Paradigm não é honesto sobre isso ter sido inferido do PRD, não descoberto via Event Storming real. **Sugestão:** trocar a frase por algo como "bounded contexts derivados das features do PRD (equivalente a um Event Storming leve já refletido na decomposição do PRD)", e opcionalmente marcar a fusão de Ingestão/Adaptador em job como `[ASSUMPTION]`.

## O que está correto (não mudar)

- Clean Architecture por serviço (AD-2) reflete fielmente "domínio sem dependência de framework".
- gRPC restrito a `ResolveCpfParaId`/`ObterCpfMascarado` é uma leitura defensável de "onde fizer sentido" — o desenho deliberadamente minimiza acoplamento síncrono entre serviços (AD-1/AD-3), então não há outro caminho quente que pediria gRPC.
- MSK evitado (SNS/SQS no lugar) e scripts deploy/pause/destroy mantidos — ambos batem com o addendum.
- Alta disponibilidade: a spine escopa corretamente para resiliência a falha parcial (AD-3/AD-10), deferindo HA de infraestrutura full-produção — consistente com o próprio NFR do PRD que o addendum alimenta.
- Spring Cloud Gateway é usado de forma real (AD-8), não só citado.

## Achado fora de escopo (nota rápida)

Spring Cloud Config aparece na tabela Stack ("Gateway, Config, Netflix Eureka") mas nenhum AD ou convenção usa Config de fato — está apenas nomeado. Vale decidir se entra como AD (config centralizada de fato) ou sai da lista até ter uso real.
