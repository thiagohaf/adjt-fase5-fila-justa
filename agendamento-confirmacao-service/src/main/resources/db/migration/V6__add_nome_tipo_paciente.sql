-- Story 4.4b: Adicionar campos nome e tipo_paciente à tabela pacientes
-- Para suportar filtros de auditoria por nome e tipo do paciente

ALTER TABLE agendamento_confirmacao.pacientes
  ADD COLUMN nome VARCHAR(255),
  ADD COLUMN tipo_paciente VARCHAR(50) DEFAULT 'REGULAR';

-- Índice para filtro LIKE case-insensitive em nome
CREATE INDEX idx_pacientes_nome ON agendamento_confirmacao.pacientes(LOWER(nome));

-- Índice para filtro por tipo_paciente
CREATE INDEX idx_pacientes_tipo_paciente ON agendamento_confirmacao.pacientes(tipo_paciente);
