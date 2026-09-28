# Addendum — ConfirmaSUS

Conteúdo de apoio ao `brief.md`: não é necessário para entender a proposta, mas preserva o raciocínio por trás das decisões e os dados brutos de pesquisa.

## Alternativas descartadas (sessão de brainstorming, 2026-09-16)

A direção ConfirmaSUS foi escolhida entre 5 clusters de ideias gerados na sessão `_bmad-output/brainstorming/brainstorm-pivot-filajusta-decisao-humana-2026-09-16/`:

- **Matching com Aprovação Humana Obrigatória** — reaproveitaria quase 100% do código já existente (a Story 3.3 do `matching-alocacao-service` já exige confirmação humana antes de qualquer alocação valer). Descartada porque o risco legal residual depende de uma leitura exata da legislação — se ela proibir até um score "sugerido"/rascunho revisável por humano, essa direção continuaria inviável.
- **Central de Encaminhamento Digital** — substituiria o fluxo de telefone/fax entre unidades (dor documentada no brief anterior) por solicitação/notificação/aceite humano registrado. Zero decisão clínica, mas não foi a escolhida.
- **Gestão de Insumos e Recursos com Alerta** — reaproveitaria o domínio `Recurso` já implementado e é um tema citado explicitamente no edital, mas tem overlap pequeno com o diferencial de auditabilidade que já era forte no projeto anterior.
- **Lembrete e Confirmação de Medicação (adesão terapêutica)** — reaproveitaria 100% da infraestrutura, mas é o domínio mais distante do que já foi construído.

Decisão do usuário: priorizar risco legal mais baixo sobre reaproveitamento máximo de código.

## Dados de pesquisa — absenteísmo no SUS (fontes completas)

Pesquisa conduzida via subagente de busca web em 2026-09-16, para embasar a seção "The Problem" do brief:

### Taxa de absenteísmo

Estudos apontam taxas altas e variáveis conforme região/especialidade. Levantamento na Região Metropolitana do Espírito Santo (2014-2016) encontrou absenteísmo médio de 38,6% em consultas especializadas e 32,1% em exames especializados, com Urologia liderando (26,9%) e Gastro/Pneumo nos menores índices (Beltrame, Oliveira, Santos & Santos Neto, 2019/2020 — https://www.scielo.br/j/sdeb/a/BYJbCp6ZBz9NCynKt3h3X3J/ e https://seer.ufrgs.br/index.php/saberesplurais/article/view/151127/97616). Estimativas gerais para a saúde pública brasileira giram em torno de 25% (https://wiki.saude.gov.br/regulacao/index.php/Absente%C3%ADsmo_no_Sistema_%C3%9Anico_de_Sa%C3%BAde_(SUS)). Revisão sistemática global aponta média mundial de 23%, com a África em 43% e a América do Sul em 27,8% — o Brasil fica na faixa alta mesmo no contexto internacional. Saúde privada brasileira reporta tipicamente 10-20% (fontes de gestão de clínicas, menor rigor acadêmico, apenas indicativo do contraste público x privado).

### Impacto e custo

Absenteísmo gera descontinuidade do cuidado, adiamento da resolução clínica e aumento do tempo de espera na fila, já que vagas ociosas não são realocadas a tempo. No estudo do Espírito Santo, 1.002.719 procedimentos (consultas + exames) resultaram em R$ 18.566.462,03 desperdiçados em três anos (R$ 3,56 milhões em consultas e R$ 15 milhões em exames) — aproximação regional, não nacional.

### Iniciativas reais de lembrete/confirmação

O governo do Ceará implantou em 2025 um fluxo automático de mensagens via WhatsApp (no agendamento, 10 dias antes e 48h antes da consulta/exame) na rede estadual de saúde (Sesa). Comparando agosto-novembro de 2024 com o mesmo período de 2025, a queda relativa no absenteísmo foi de ~18,75%, com variação por tipo de unidade: hospitais estaduais caíram de 36,34% para 21,87% (novembro); CEOs, de 23,4% para 19,4%; policlínicas, de 24,5% para 17,7% (outubro) (Governo do Ceará, dez/2025 — https://www.ceara.gov.br/2025/12/19/ceara-reduz-em-cerca-de-19-as-faltas-em-marcacoes-de-consultas-e-exames-laboratoriais-com-servico-de-mensagens-pelo-whatsapp/). Internacionalmente, revisão Cochrane sobre lembretes por SMS (7 estudos, 5.841 participantes) mostrou aumento de comparecimento de 67,8% para 78,6%; estudo no American Journal of Medicine relatou redução de ~38% nas faltas com lembrete por texto (citados via fontes secundárias bydoctor.com.br/stealthai.com.br, referenciando literatura médica revisada). Fontes de gestão de clínicas relatam quedas de 20-40% no no-show quando lembrete é combinado com confirmação ativa (via soulupagencia.com, citando JMIR).

### Motivos das faltas

As causas mais citadas na literatura são esquecimento da data/horário, falhas de comunicação entre serviço e usuário, melhora do sintoma, conflito com horário de trabalho e falta de transporte/custo de deslocamento (Beltrame et al., 2019/2020). Levantamento em clínica geral de Portugal (aproximação por falta de dado mais específico do SUS) atribuiu 27,6% das faltas a esquecimento e 8,9% a transporte/logística (via Medscape Brasil — https://portugues.medscape.com/verartigo/6510278). Estudo espanhol estimou que 52,4% dos casos de absenteísmo seriam evitáveis.

### Nota de rigor

Parte das fontes acima (bydoctor.com.br, stealthai.com.br, soulupagencia.com) são blogs de gestão de clínicas citando literatura médica de forma secundária — usar com essa ressalva no relatório final, preferindo sempre citar a fonte primária (Cochrane, AJM, SciELO) quando possível.
