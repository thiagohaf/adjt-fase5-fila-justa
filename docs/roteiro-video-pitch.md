# Roteiro — Vídeo do Pitch (máx. 8 min)

Estrutura do edital: Introdução (1 min) · Solução (3 min) · Impacto (2 min) · Próximos passos (2 min). Tempo total: 8:00.
Sugestão: 6 a 8 slides simples (ou tela do relatório e do diagrama) e câmera ligada na abertura.

---

## 1. Introdução — 0:00 a 1:00

**Slide:** título "ConfirmaSUS" e seu nome.

> "Olá, eu sou o Thiago Henrique Alves Ferreira, RM369442, turma 11ADJT. Nesta entrega eu fui a equipe inteira: produto, arquitetura, desenvolvimento e testes.
>
> O problema que escolhi é silencioso e caro. A média mundial de faltas em consultas é de 23%, e na América do Sul chega a 27,8%. Num estudo na Região Metropolitana do Espírito Santo, o absenteísmo chegou a 38,6% nas consultas especializadas, e isso significou **18,5 milhões de reais desperdiçados em três anos**, só nessa região.
>
> A vaga fica vazia, o profissional fica parado, e quem está na lista de espera nem sabe que existiu uma vaga."

**Slide:** os números (23% mundial · 27,8% América do Sul · 38,6% no ES · R$ 18,5 mi).

## 2. A solução — 1:00 a 4:00

**Slide:** diagrama do ciclo.

> "O ConfirmaSUS fecha esse ciclo. O paciente já tem uma consulta agendada. Quando a janela de confirmação abre, o sistema o notifica e pede uma confirmação ativa.
>
> Se ele confirma, a vaga está garantida. Se ele **recusa**, ou simplesmente **não responde até o prazo**, a vaga é liberada automaticamente.
>
> Aí vem a parte que importa: o sistema **sugere o próximo paciente da lista de espera**, por ordem de chegada. E um **gestor de agenda humano confirma ou recusa** essa sugestão. Se recusar, o sistema já gera a próxima.
>
> Cada passo, da notificação à decisão do gestor, é gravado em um **log auditável**, com motivo e horário. Dá para responder a qualquer paciente ou auditor: por que a vaga X foi para o paciente Y, e quando."

**Slide:** "Decisão humana, por lei".

> "Um ponto de design: eu comecei este projeto com outra ideia, um motor de priorização clínica por score. Descobri que a legislação proíbe que um sistema automatizado decida triagem ou prioridade clínica. Pivotei. O ConfirmaSUS **nunca** decide sobre a saúde de ninguém. A fila é estritamente por ordem de chegada, e todo repasse exige um humano."

**Slide:** diferencial.

> "Lembrete por WhatsApp já existe e funciona: a rede da Sesa do Ceará registrou queda relativa de 18,75% nas faltas em 2025. Mas o lembrete sozinho deixa o ciclo aberto. **O nosso diferencial é o que acontece depois**: transformar a vaga liberada em uma oferta ativa, com decisão humana e rastro auditável."

**Slide:** arquitetura em uma frase.

> "Por baixo, são microsserviços em Java 25 e Spring Boot, com eventos assíncronos via SNS e SQS, autenticação JWT no gateway e um schema de banco por serviço. A auditoria é assíncrona: se ela cair, a confirmação do paciente continua funcionando."

## 3. Impacto — 4:00 a 6:00

**Slide:** quatro beneficiários.

> "Para o **paciente**: um aviso claro e uma forma simples de liberar a vaga se não puder ir.
>
> Para o **paciente na lista de espera**: uma chance real de ser chamado a tempo, em vez de a vaga ficar ociosa.
>
> Para o **gestor de agenda**: ele deixa de descobrir a ausência por acaso e recebe a vaga já com um candidato sugerido, mas a decisão continua dele.
>
> Para o **auditor e o SUS**: transparência. Cada repasse tem justificativa registrada."

**Slide:** caso de uso concreto.

> "Um exemplo: uma consulta de cardiologia daqui a dois dias. O paciente responde que não poderá ir. A vaga é liberada na hora, o próximo da fila é sugerido, o gestor confirma com um clique, e a consulta que seria perdida vira um atendimento.
>
*(Não projete economia em reais para o SUS: os números do vídeo são só os das fontes citadas.)*

## 4. Próximos passos — 6:00 a 8:00

**Slide:** roadmap.

> "O MVP prova o ciclo completo com dados sintéticos. Os próximos passos são:
>
> **Primeiro**, trocar a notificação simulada por um canal real, como WhatsApp, seguindo o precedente do Ceará.
>
> **Segundo**, integrar com os sistemas oficiais, como SISREG e DATASUS, no lugar do seed sintético, e reintroduzir o fluxo de agendamento em si.
>
> **Terceiro**, completar a implantação na AWS, com perfis de acesso por papel e adequação plena à LGPD.
>
> **Por fim**, generalizar o padrão de 'sugestão, confirmação humana e log auditável' para outros recursos escassos do SUS, como leitos e equipamentos.
>
> O ConfirmaSUS mostra que dá para usar tecnologia para reduzir desperdício no SUS respeitando o limite mais importante: a decisão sobre a saúde das pessoas continua nas mãos de pessoas. Obrigado."

**Slide final:** link do repositório e contato.

---

## Checklist antes de gravar

- [ ] Cronometrar cada bloco (a introdução costuma estourar).
- [ ] Confirmar que os números citados batem com o relatório (seção 2), que foram conferidos nas fontes primárias.
- [ ] Deixar o diagrama do relatório (seção 5) legível em tela cheia.
- [ ] Exportar o vídeo em até 8:00 e testar o link público do drive.
