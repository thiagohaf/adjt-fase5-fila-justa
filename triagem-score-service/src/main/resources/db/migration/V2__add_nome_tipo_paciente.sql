-- Story 4.4b: Adicionar campos nome e tipo_paciente à tabela pacientes
-- Para suportar filtros de auditoria por nome e tipo do paciente

ALTER TABLE triagem_score.pacientes
  ADD COLUMN nome VARCHAR(255),
  ADD COLUMN tipo_paciente VARCHAR(50) DEFAULT 'REGULAR';

-- Índice para filtro LIKE case-insensitive em nome
CREATE INDEX idx_pacientes_nome ON triagem_score.pacientes(LOWER(nome));

-- Índice para filtro por tipo_paciente
CREATE INDEX idx_pacientes_tipo_paciente ON triagem_score.pacientes(tipo_paciente);
