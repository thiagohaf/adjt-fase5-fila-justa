-- Schema e tabelas para triagem-score-service (Epic 2)

CREATE SCHEMA IF NOT EXISTS triagem_score;

-- Tabela de Pacientes (criados implicitamente por CPF)
CREATE TABLE triagem_score.pacientes (
  id UUID PRIMARY KEY,
  cpf VARCHAR(11) NOT NULL UNIQUE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pacientes_cpf ON triagem_score.pacientes(cpf);

-- Tabela de Triagens (score + fatores + sinais vitais em JSONB)
CREATE TABLE triagem_score.triagens (
  id UUID PRIMARY KEY,
  paciente_id UUID NOT NULL REFERENCES triagem_score.pacientes(id),
  gravidade VARCHAR(20) NOT NULL,
  sinais_vitais JSONB NOT NULL,
  sintomas JSONB NOT NULL,
  score_versao VARCHAR(10) NOT NULL,
  score_valor INTEGER NOT NULL CHECK (score_valor >= 0 AND score_valor <= 100),
  score_fatores JSONB NOT NULL,
  registrado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_triagens_paciente_id ON triagem_score.triagens(paciente_id);
-- Patch: índice explícito na FK (Postgres não indexa FK automaticamente)
CREATE INDEX idx_triagens_paciente_id_explicit ON triagem_score.triagens(paciente_id);

-- Tabela de Eventos Outbox (para relay assíncrono)
CREATE TABLE triagem_score.eventos_outbox (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(50) NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  payload JSONB NOT NULL,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eventos_outbox_event_type ON triagem_score.eventos_outbox(event_type);
CREATE INDEX idx_eventos_outbox_criado_em ON triagem_score.eventos_outbox(criado_em);
