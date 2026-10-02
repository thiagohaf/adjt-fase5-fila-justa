-- Adicionar constraint UNIQUE em (paciente_id, recurso_id) para garantir idempotência (Story 5.2, Item 2)
-- TOCTOU race condition fix: duas requisições simultâneas não podem criar dois agendamentos
-- para o mesmo paciente+recurso. O constraint garante que a segunda INSERT falha com constraint violation.
ALTER TABLE agendamento_confirmacao.agendamentos
  ADD CONSTRAINT unique_paciente_recurso UNIQUE (paciente_id, recurso_id);
