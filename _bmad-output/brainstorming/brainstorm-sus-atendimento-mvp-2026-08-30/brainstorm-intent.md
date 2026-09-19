# Intent Doc — MVP Backend: Motor de Priorização e Alocação Inteligente (SUS)

> Insumo condensado do brainstorming de 2026-08-30 para uso direto em `bmad-product-brief` / `bmad-prd`. Contém apenas o que foi escolhido e o que é crítico para justificá-lo.

## Contexto do desafio

Hackathon FIAP Pós-Tech ("Arquitetura e Desenvolvimento Java", Fase 5). Tema: inovação para otimização de atendimento no SUS. Entregável: **MVP backend-only** (sem frontend obrigatório), demonstrável via Postman/Swagger, mais pitch, vídeo demo e relatório escrito. Critérios de avaliação: Problema e Impacto 20%, Inovação 20%, Funcionalidade do MVP 30%, Apresentação 20%, Documentação 10%.

## Problema (evidenciado em pesquisa)

Filas de atendimento no SUS — especialmente cirúrgicas e de regulação de leitos — são geridas de forma manual, fragmentada e não auditável, causando esperas longas e alocação injusta ou ineficiente:

- Fragmentação e baixa interoperabilidade entre sistemas (SISREG, SIH/SUS, e-SUS APS, DATASUS) impedem visão unificada da demanda e da oferta.
- Falta de integração de dados entre unidades é apontada como causa raiz das filas.
- Insuficiência de leitos combinada com regulação assistencial ineficiente (hoje frequentemente feita por telefone/fax) prolonga esperas.
- Priorização de pacientes tende a seguir ordem de chegada em vez de critério clínico objetivo, abrindo espaço para "fura-fila" e falta de transparência.
- Sistemas de prontuário eletrônico do SUS têm falhas/lentidão que atrasam o atendimento (caso documentado em SUS-BH).

## Usuários / personas-alvo

- **Regulador/gestor de saúde**: precisa alocar leitos e vagas de especialista com critério objetivo e visibilidade de gargalos em tempo real.
- **Enfermeiro(a)/profissional de triagem**: registra dados clínicos estruturados que alimentam a priorização.
- **Paciente** (beneficiário indireto no MVP backend): tem sua posição na fila determinada por um score clínico transparente, não por ordem de chegada ou influência externa.
- **Auditor/órgão de controle** (uso do diferencial de explicabilidade): precisa conseguir explicar por que um paciente foi atendido antes de outro.

## Solução escolhida: Motor de Priorização e Alocação Inteligente

Resultado da síntese entre Affinity Clustering (5 clusters) e Impact-Effort: o cluster "Fila e Priorização Clínica Inteligente" venceu por alto impacto (dor documentada e recorrente na pesquisa) com esforço moderado-baixo para um MVP backend Java, absorvendo o mecanismo de matching de leitos de outros clusters.

**Mecanismo central** (mesma ideia observada de 4 ângulos convergentes na sessão):
1. **Score de prioridade clínica objetivo** — calculado a partir de dados estruturados de triagem (não ordem de chegada), funcionando como um "passe de prioridade clínica" via API.
2. **Matching em tempo real paciente–recurso** — no estilo Uber/99 (motorista-passageiro) ou order matching de bolsa de valores: casa o paciente com o leito/especialista disponível mais adequado no momento.
3. **Priorização por urgência acumulada ao longo do tempo** — inspirada em controle de tráfego aéreo (prioriza por gravidade clínica + tempo de espera acumulado), evitando que casos moderados fiquem indefinidamente represados.
4. **Camada adapter/seed simulando SISREG/DATASUS** — dados de unidades, leitos e especialistas vêm de um seed sintético por trás de um adapter pattern, demonstrando extensibilidade para integração real futura sem precisar implementá-la no prazo do hackathon.
5. **Log de fila auditável e explicável** — cada decisão de priorização/alocação fica registrada e é possível explicar por que um paciente furou (ou não) a fila. Este é o diferencial central de inovação/transparência da proposta.

## Por que vence nos critérios do hackathon

- **Problema e Impacto (20%)**: ataca diretamente uma dor documentada e repetida na pesquisa (filas sem critério confiável, fragmentação de sistemas, regulação manual ineficiente).
- **Inovação (20%)**: o log de fila auditável/explicável é um diferencial que dificilmente concorrentes terão — vai além de "só calcular um score" e entrega transparência e defesa contra fura-fila.
- **Funcionalidade do MVP (30%)**: escopo cabe em um fluxo ponta a ponta único (triagem → score → matching/alocação → log), viável em Java com dataset sintético, demonstrável 100% via Swagger/Postman.
- **Apresentação (20%)** e **Documentação (10%)**: a metáfora de matching (Uber/bolsa de valores/tráfego aéreo) é fácil de comunicar em vídeo e relatório, e o adapter pattern permite explicar claramente o que é real vs. simulado no MVP.

## Escopo do MVP (restrições explícitas do hackathon)

- **Backend-only**: nenhuma interface obrigatória; demonstração via Swagger/coleção Postman.
- **Sem cadastro completo de paciente**: usar CPF como chave, dados demográficos mockados; foco 100% na lógica de fila e priorização.
- **Sem autenticação completa**: token mockado é suficiente.
- **Sem integração real com SISREG/DATASUS**: substituída por dados simulados via seed no banco, atrás de um adapter pattern que demonstre extensibilidade.
- **Um único fluxo ponta a ponta priorizado**: triagem → cálculo de score → sugestão/matching de leito ou especialista. Demais fluxos ficam como "próximos passos" no relatório.
- **Mensageria (RabbitMQ/Kafka), se usada, é apenas simbólica**: mostrar arquitetura orientada a eventos, sem exigir múltiplos consumidores reais.
- **Persistência simples** (H2 ou Postgres) com dataset sintético de poucas unidades de saúde, garantindo demo em vídeo sem falhas.
- **Empacotamento via Docker Compose**, subindo com um único comando.

## Ideias parqueadas para v2 (não fazem parte do MVP, mas não devem se perder)

- **Agendamento inteligente / redução de no-show**: lembretes inteligentes, realocação automática de vagas ociosas, score de confiabilidade de agendamento por unidade (yield management estilo companhias aéreas).
- **Score de risco de deterioração clínica**: para pacientes já na fila de cirurgia eletiva, sinalizando necessidade de reavaliação antes do atendimento.
- **Interoperabilidade real com SISREG/DATASUS**: substituir a camada adapter/seed por integração de fato com os sistemas oficiais do SUS, fora do prazo viável do hackathon.
