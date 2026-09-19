# Epic 1 Context: Confirmação Ativa de Presença

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Permitir que um Paciente identificado por CPF (resolvido internamente para um ID de Paciente, sem o CPF circular além do ponto de ingestão) seja notificado quando sua Janela de Confirmação abre, e possa confirmar ou recusar presença via API — ou, na ausência de resposta, ter o Agendamento marcado automaticamente como Não Confirmado ao expirar o prazo. Qualquer um desses três desfechos (Recusa ou Não Confirmado) libera a vaga imediatamente, disparando o fluxo de repasse do Epic 2. Este epic entrega o serviço `agendamento-confirmacao-service` (renomeado/podado de `triagem-score-service`) e fecha o primeiro elo do ciclo de combate ao absenteísmo que justifica o produto.

## Stories

- Story 1.1: Registrar Agendamento e Resolver Paciente por CPF
- Story 1.2: Abertura da Janela de Confirmação e Notificação
- Story 1.3: Confirmação de Presença
- Story 1.4: Recusa Ativa e Liberação Imediata da Vaga
- Story 1.5: Expiração da Janela e Liberação Automática

## Requirements & Constraints

- CPF com formato/checksum inválido rejeita o registro correspondente com erro `422`; nenhum componente fora deste serviço jamais persiste ou recebe o CPF — apenas o `pacienteId` circula em eventos e consultas.
- A notificação (mockada) de abertura de Janela é publicada exatamente uma vez por Agendamento por abertura de janela — idempotente sob reprocessamento do gatilho.
- Confirmação de presença é idempotente: repetida para o mesmo Agendamento retorna sucesso silencioso, sem novo registro; uma vez confirmada, a vaga nunca é liberada mesmo que o prazo depois expire.
- Recusa dispara a liberação da vaga imediatamente, sem esperar o fim da Janela; reenvio da mesma recusa (retry) é idempotente.
- Expiração sem resposta transiciona automaticamente para Não Confirmado, sem intervenção manual, com causa distinta de Recusa para fins de auditoria.
- Toda tentativa fora do estado esperado (janela ainda não aberta, vaga já liberada, estado inválido) retorna `409` com motivo — nunca aceita nem falha silenciosamente; `422` é reservado para entrada inválida (CPF, recurso inexistente, data/hora inválida).
- Sob concorrência (duas requisições, ou múltiplas instâncias de poller disputando a mesma transição), apenas uma transição vence; a perdedora recebe `409`, exceto quando o resultado perdedor é logicamente equivalente ao pedido (idempotência).
- Cobertura de linha ≥90% (JaCoCo) na camada de domínio + teste de mutação (PIT) + teste de integração dos contratos exercitados + cenário BDD quando a story participa do ciclo ponta a ponta.

## Technical Decisions

- Clean Architecture por serviço: `domain/` sem dependência de framework → `application/command|query/` → `infrastructure/` (web, gRPC, persistência, mensageria, scheduler). CQRS lógico, mesmo banco, sem read-model separado.
- Agendamento tem estado único: `AGUARDANDO_JANELA` → `AGUARDANDO_CONFIRMACAO` → `CONFIRMADO` (terminal) ou `LIBERADO` (terminal, com `motivoLiberacao ∈ {RECUSA, NAO_CONFIRMADO}`). "Vaga Liberada" não é entidade separada — é o próprio estado `LIBERADO`.
- Toda transição de estado é escrita condicional (`UPDATE ... WHERE status = <estado_esperado>`), nunca leitura-depois-escrita sem guarda — garante que Confirmação, Recusa e expiração concorrentes produzam no máximo uma transição vencedora.
- Dois pollers `@Scheduled` independentes, ambos com a mesma disciplina de escrita condicional: um de abertura de janela (publica notificação) e um de expiração (publica Não Confirmado + Vaga Liberada). Cadência exata do poller é `[Deferred]`, configurável.
- Todo evento de domínio é gravado na tabela outbox na mesma transação local do comando (nunca chamada síncrona bloqueante); relay poller publica em tópico SNS FIFO após confirmação do broker; `MessageGroupId = agendamentoId`, `MessageDeduplicationId = eventId`. Eventos produzidos por este serviço: `NotificacaoConfirmacaoPublicada`, `ConfirmacaoRegistrada`, `RecusaRegistrada`, `AgendamentoNaoConfirmado`, `VagaLiberada`.
- O único meio de outro serviço obter um `pacienteId` a partir de um CPF é o gRPC interno `ResolverOuCriarPaciente(cpf) -> pacienteId`, exclusivo de `liberacao-repasse-service` ao ingerir Lista de Espera, chamado apenas pelo `seed-adapter` em tempo de deploy (timeout curto, sem retry). CPF nunca aparece em evento, log ou schema fora deste serviço.
- Schema próprio `agendamento_confirmacao` (isolamento por schema/usuário, `REVOKE` cross-schema, enforcement ArchUnit no CI); security group liberando só o SG do `gateway-service` na porta HTTP e o SG do `liberacao-repasse-service` na porta gRPC; health-check público; segredos via variável de ambiente/Secrets Manager.
- Continuidade sob falha parcial: o fluxo de Confirmação/Recusa continua aceitando respostas mesmo se `auditoria-service` estiver indisponível — a propagação para auditoria é assíncrona e nunca bloqueia nem perde o evento.
- Convenção de erro cross-cutting: `409` + `{error, motivo}` para conflito de estado; `422` para entrada inválida; `404` só para rota/recurso inexistente.

## Cross-Story Dependencies

- Story 1.1 é pré-requisito de todas as demais (Agendamento e Paciente precisam existir antes de qualquer transição de estado).
- Story 1.2 (abertura de janela) precede Stories 1.3/1.4 (Agendamento só aceita Confirmação/Recusa em `AGUARDANDO_CONFIRMACAO`).
- Stories 1.3, 1.4 e 1.5 competem pela mesma transição de estado do Agendamento e compartilham a mesma disciplina de escrita condicional — devem ser implementadas de forma consistente entre si.
- O evento `VagaLiberada` (emitido pelas Stories 1.4 e 1.5) é a dependência de entrada do Epic 2 (`liberacao-repasse-service`), que consome esse evento para gerar a Sugestão de Repasse.
- O gRPC `ResolverOuCriarPaciente` exposto por este serviço (Story 1.1) é consumido pelo `seed-adapter` do Epic 4 e pelo `liberacao-repasse-service` do Epic 2 durante a ingestão da Lista de Espera.
