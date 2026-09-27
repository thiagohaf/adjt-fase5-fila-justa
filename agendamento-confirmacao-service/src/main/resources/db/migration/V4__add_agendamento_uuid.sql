-- Adicionar coluna UUID ao agendamento para API (seed-adapter esperava UUID)
ALTER TABLE agendamento_confirmacao.agendamentos
  ADD COLUMN agendamento_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
