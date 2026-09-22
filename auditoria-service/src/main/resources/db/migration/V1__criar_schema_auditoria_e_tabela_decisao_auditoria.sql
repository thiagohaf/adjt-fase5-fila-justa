-- Story 4.1: Criar schema de auditoria e tabela append-only para decisões
-- Isolado conforme AD-10 (schema separation)

CREATE SCHEMA IF NOT EXISTS auditoria;

-- Tabela append-only: nunca altera entrada pós-criação, apenas INSERT
-- UNIQUE constraint em evento_id garante idempotência
CREATE TABLE auditoria.decisao_auditoria (
    id BIGSERIAL PRIMARY KEY,
    evento_id UUID NOT NULL UNIQUE,
    agendamento_id BIGINT,
    paciente_id BIGINT,
    tipo_decisao VARCHAR(50) NOT NULL,
    motivo TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_bruto JSONB
);

-- Índices para consultas de auditoria (Story 4.2 - endpoints de leitura)
CREATE INDEX idx_decisao_auditoria_evento_id ON auditoria.decisao_auditoria(evento_id);
CREATE INDEX idx_decisao_auditoria_agendamento_id ON auditoria.decisao_auditoria(agendamento_id);
CREATE INDEX idx_decisao_auditoria_paciente_id ON auditoria.decisao_auditoria(paciente_id);
CREATE INDEX idx_decisao_auditoria_tipo_decisao ON auditoria.decisao_auditoria(tipo_decisao);
CREATE INDEX idx_decisao_auditoria_timestamp ON auditoria.decisao_auditoria(timestamp);
CREATE INDEX idx_decisao_auditoria_criado_em ON auditoria.decisao_auditoria(criado_em);

-- Comentários (documentação no schema)
COMMENT ON SCHEMA auditoria IS 'Schema isolado para log auditável de decisões (Story 4.1)';
COMMENT ON TABLE auditoria.decisao_auditoria IS 'Tabela append-only: registro imutável de decisões do sistema. PK=id (auto), evento_id UNIQUE (dedup).';
COMMENT ON COLUMN auditoria.decisao_auditoria.evento_id IS 'UUID único do evento, garante idempotência por dedup (UNIQUE constraint)';
COMMENT ON COLUMN auditoria.decisao_auditoria.tipo_decisao IS 'NOTIFICACAO|CONFIRMACAO|RECUSA|NAO_CONFIRMADO|LIBERACAO|SUGESTAO_GERADA|REPASSE_CONFIRMADO|SUGESTAO_RECUSADA|GENERICO';
COMMENT ON COLUMN auditoria.decisao_auditoria.timestamp IS 'Instante da decisão (vem do evento original, occurredAt)';
COMMENT ON COLUMN auditoria.decisao_auditoria.criado_em IS 'Instante do registro em auditoria (sempre agora)';
COMMENT ON COLUMN auditoria.decisao_auditoria.payload_bruto IS 'Payload JSON bruto do evento (para GENERICO e troubleshooting)';
