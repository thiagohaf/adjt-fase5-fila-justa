-- Spec 1.4: adicionar coluna motivo_liberacao para rastrear a causa de liberação de uma vaga
-- (RECUSA ou NAO_CONFIRMADO)
ALTER TABLE agendamento_confirmacao.agendamentos
ADD COLUMN motivo_liberacao VARCHAR(32) NULL;
