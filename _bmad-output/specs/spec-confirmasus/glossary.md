# Glossário — ConfirmaSUS

- **Paciente** — pessoa com um Agendamento, identificada externamente por CPF (sintético) apenas na fronteira de ingestão e internamente por um ID de Paciente gerado pelo sistema (ver Constraints/CPF no SPEC).
- **Agendamento** — consulta ou exame já marcado para um Paciente em um Recurso (unidade + especialidade/tipo), carregado via seed sintético (CAP-1). Marcar o Agendamento em si não é uma capacidade deste sistema (Non-goal).
- **Janela de Confirmação** — período antes do Agendamento durante o qual o Paciente pode confirmar ou recusar presença.
- **Confirmação** — ação explícita do Paciente informando que comparecerá.
- **Recusa** — ação explícita do Paciente informando que não comparecerá, dita antes do fim da Janela de Confirmação.
- **Não Confirmado** — estado atribuído automaticamente pelo sistema quando a Janela de Confirmação expira sem Confirmação nem Recusa. Equivalente à Recusa para efeito de liberação da vaga, mas registrado com causa distinta no Log Auditável.
- **Vaga Liberada** — o Agendamento cuja ocupação deixou de estar garantida (por Recusa ou por ficar Não Confirmado), disponível para repasse.
- **Lista de Espera** — fila de Pacientes aguardando aquele tipo de Recurso, ordenada exclusivamente por ordem de chegada da solicitação — nunca por critério clínico ou de gravidade.
- **Sugestão de Repasse** — recomendação, gerada automaticamente pelo sistema, de qual Paciente da Lista de Espera deveria ocupar uma Vaga Liberada. Ainda não é uma decisão definitiva.
- **Repasse Confirmado** — atribuição definitiva da Vaga Liberada a um Paciente da Lista de Espera, criada quando um Gestor de Agenda confirma uma Sugestão de Repasse. Só existe após essa confirmação.
- **Log Auditável** — registro explicável de toda notificação, Confirmação, Recusa, expiração, liberação, Sugestão de Repasse e decisão de repasse, incluindo motivo e timestamp.
- **Gestor de Agenda** — usuário direto da API responsável por decidir o repasse de uma Vaga Liberada.
- **Auditor** — usuário (direto ou por meio de um atendente) que consulta o Log Auditável para justificar decisões passadas.

**Nota de escopo:** este sistema não calcula nenhum "Score de Prioridade Clínica" nem faz "Matching automático" por gravidade — conceitos do produto anterior, permanentemente fora de escopo aqui, por restrição legal.
