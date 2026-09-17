---
title: Reconciliação Brief → PRD — ConfirmaSUS
created: 2026-09-16
input: brief-Fase5-2026-09-16/brief.md + addendum.md
output: prd-Fase5-2026-09-16/prd.md
---

# Reconciliação de Conteúdo — Brief vs. PRD (ConfirmaSUS)

## Gaps identificados

1. **Notificação ativa ao Gestor de Agenda vira apenas consulta passiva.** O brief, na seção Success Criteria, exige explicitamente que "toda vaga liberada por não confirmação gera automaticamente uma sugestão de repasse... e **notifica o gestor de agenda**, sem intervenção manual". O PRD não tem nenhuma FR de notificação ao Gestor de Agenda — FR-8 e FR-9 só permitem que ele **consulte** vagas com sugestão pendente (pull), e UJ-3 confirma esse modelo pull ("Consulta vagas liberadas com sugestão pendente"). Isso dilui um requisito funcional explícito do brief para uma capability mais fraca. Deveria entrar como uma nova FR em §4.3 (ex.: "FR-9a: Notificação da Sugestão de Repasse ao Gestor de Agenda") ou como consequence adicional de FR-9, e refletido em SM-1/SM-3.

2. **Roadmap pós-hackathon (Vision) omitido.** O brief tem, na seção Vision, três próximos passos explícitos além do hackathon: (1) reintroduzir o fluxo de agendamento; (2) trocar a notificação simulada por integração real (citando o precedente do Ceará); (3) generalizar o padrão "sugestão + confirmação humana + log auditável" para outros recursos do SUS (leitos, equipamentos). O §1 Vision do PRD cobre só a vitória do MVP no hackathon e não menciona nenhum desses três passos futuros. Deveria entrar como um parágrafo final em §1 Vision ou uma subseção "Beyond MVP" — é sinal de pensamento de produto além do escopo imediato, relevante para o critério Inovação.

3. **Racional da escolha do pivô (risco legal vs. reaproveitamento de código) ausente.** O addendum documenta que 4 alternativas foram descartadas (Matching com Aprovação Humana, Central de Encaminhamento Digital, Gestão de Insumos, Lembrete de Medicação) e registra a decisão explícita do usuário: "priorizar risco legal mais baixo sobre reaproveitamento máximo de código" — inclusive rejeitando a alternativa que reaproveitaria quase 100% do código já existente. O PRD (§0 Document Purpose, §8 Constraints) menciona a restrição legal como motivo do pivô, mas não registra esse trade-off específico nem que a opção de maior reaproveitamento foi conscientemente preterida por segurança jurídica. Deveria entrar em §11 Assumptions Index → Decisões Fechadas, como uma linha de rastreabilidade.

4. **Ressalva de honestidade ("para ser honesto sobre os limites") e benchmarks internacionais de literatura ficam de fora.** A seção "What Makes This Different" do brief faz questão de reconhecer, com essa frase explícita, que o mecanismo de lembrete isoladamente "não é novidade" — citando a revisão Cochrane (aumento de comparecimento de 67,8% para 78,6%) e o American Journal of Medicine (~38% de redução de faltas) como evidência de que a literatura já validou o lembrete simples, para não superestimar a inovação do lembrete em si. O §1 Vision do PRD preserva a ideia central (o diferencial é fechar o ciclo, não o lembrete) mas remove tanto a frase de honestidade quanto os números de benchmark internacional que davam peso a essa ressalva perante a banca (critério Inovação, 20%). Deveria voltar ao §1 Vision ou a uma nota em §9 Success Metrics/Apresentação.

5. **Nota de rigor sobre fontes secundárias não é carregada para a fase de documentação.** O addendum tem uma seção dedicada ("Nota de rigor") alertando que fontes como bydoctor.com.br, stealthai.com.br e soulupagencia.com são blogs de gestão citando literatura médica de forma secundária, e recomenda preferir sempre a fonte primária (Cochrane, AJM, SciELO) no relatório final. O PRD não carrega essa ressalva metodológica em nenhum lugar (nem mesmo como nota para a fase de Documentação, que vale 10% do edital). Deveria entrar como uma nota em §9 Success Metrics (bloco de contexto do edital) ou em §0 Document Purpose, para não se perder antes do relatório final ser escrito.

6. **Ancoragem específica do pitch (Apresentação) não é repassada.** O brief é específico em Success Criteria: o pitch/vídeo deve ser "ancorado no dado de custo real (ex.: R$ 18,5 milhões desperdiçados em 3 anos numa única região) e no precedente real do Ceará". O PRD (§9, nota de contexto do edital) lista os pesos dos critérios (Apresentação 20%) mas não repete essa orientação concreta de quais dados ancorar a apresentação. É um detalhe pequeno, mas é justamente o tipo de orientação qualitativa que uma estrutura de FR tende a descartar. Vale uma frase em §9 ou em nota de rodapé ligada ao critério Apresentação.

## Observação

Nenhuma contradição factual grave foi encontrada — o PRD é fiel ao núcleo do brief (ciclo ponta a ponta, restrição legal, lista de espera por ordem de chegada, decisão humana obrigatória). Os gaps acima são majoritariamente de **tom, racional e visão de longo prazo** — exatamente o tipo de conteúdo que a estrutura FR-cêntrica do PRD tende a descartar silenciosamente.
