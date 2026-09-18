# Jornadas de Usuário — ConfirmaSUS

## UJ-1. Paciente confirma presença e evita perder a vaga

- **Persona + contexto:** Marina, paciente com exame de imagem marcado para daqui a três dias, que já esqueceu compromissos médicos antes.
- **Entry state:** Agendamento já existe no sistema (seed); Marina ainda não foi notificada.
- **Path:** Sistema publica notificação de confirmação dentro da Janela de Confirmação → Marina responde `POST` confirmando presença via API (simulando resposta a SMS/WhatsApp).
- **Climax:** A confirmação é registrada antes do prazo, sem qualquer intervenção manual de um atendente.
- **Resolution:** A vaga permanece com Marina; nada é liberado. Realiza CAP-3, CAP-4.

## UJ-2. Paciente não responde e sua vaga chega a quem estava esperando

- **Persona + contexto:** João, paciente com consulta marcada, que teve uma emergência de trabalho e nem viu a notificação.
- **Entry state:** Notificação de confirmação foi enviada; Janela de Confirmação está correndo.
- **Path:** Janela expira sem resposta → sistema marca como Não Confirmado e libera a vaga → sistema consulta a Lista de Espera daquele recurso e sugere automaticamente o próximo paciente por ordem de chegada da solicitação.
- **Climax:** A vaga não fica "perdida" silenciosamente — uma sugestão de repasse já está pronta para o Gestor de Agenda no mesmo instante em que a vaga é liberada.
- **Resolution:** Vaga ociosa vira uma oportunidade real para outro paciente, sem que ninguém precise notar a ausência manualmente. Realiza CAP-6, CAP-7, CAP-8, CAP-9.

## UJ-3. Gestor de Agenda decide o repasse sem telefonema

- **Persona + contexto:** Carla, gestora de agenda de uma UBS, historicamente dependente de ligações para saber quem faltou e quem chamar em seguida.
- **Entry state:** Autenticada, consulta a API a partir do seu posto de trabalho.
- **Path:** Consulta Vagas Liberadas com sugestão pendente → revisa o candidato sugerido pela Lista de Espera → confirma ou recusa a sugestão.
- **Climax:** Recebe uma sugestão pronta (qual paciente, por ordem de chegada de qual solicitação) em vez de descobrir a vaga vazia "no escuro".
- **Resolution:** A vaga é repassada ao paciente correto e a decisão fica registrada. Realiza CAP-9, CAP-10, CAP-11.

## UJ-4. Auditor investiga uma reclamação sobre repasse de vaga

- **Persona + contexto:** Renato, auditor de um órgão de controle, recebe uma reclamação de que a vaga de um paciente foi "dada" a outro sem aviso.
- **Entry state:** Autenticado, sem conhecimento prévio do caso.
- **Path:** Consulta o log auditável do agendamento reclamado → vê a linha do tempo (notificação enviada, prazo, não-confirmação, liberação, sugestão, decisão do gestor) → confirma que o paciente reclamante não confirmou dentro do prazo.
- **Climax:** Cada etapa tem timestamp e motivo explícitos — a resposta é defensável com dados, não "confie em nós".
- **Resolution:** Reclamação respondida com evidência auditável, ou identificado um caso real de erro (ex.: notificação nunca enviada) a corrigir. Realiza CAP-12, CAP-13.
