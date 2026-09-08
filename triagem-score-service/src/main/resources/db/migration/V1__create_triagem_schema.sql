-- Schema proprio do triagem-score-service (AD-9) -- isolamento por schema no
-- mesmo cluster Postgres 18 compartilhado; role de banco dedicado + REVOKE
-- cross-schema fica deferido (deferred-work.md), mesmo padrao do auth-service.
CREATE SCHEMA IF NOT EXISTS triagem_score;

-- Paciente identificado por CPF (FR-2). UNIQUE(cpf) e a rede de seguranca da
-- idempotencia de ResolverOuCriarPaciente (application/command) sob corrida
-- de duas Triagens simultaneas para o mesmo CPF.
CREATE TABLE triagem_score.pacientes (
    id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cpf VARCHAR(11) NOT NULL UNIQUE
);

-- Triagem (FR-1): sinais vitais validados (AD-11), gravidade percebida e
-- Score calculado (FR-3, FR-4). sintomas e score_fatores sao JSONB --
-- sintomas e lista livre de strings (nao entra na formula do Score, ver
-- Design Notes da spec 2.1); score_fatores e a lista de FatorContribuinte
-- ({"fator": ..., "contribuicao": ...}) retornada na resposta.
CREATE TABLE triagem_score.triagens (
    id                           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id                  BIGINT NOT NULL REFERENCES triagem_score.pacientes(id),
    frequencia_cardiaca          DOUBLE PRECISION NOT NULL,
    pressao_arterial_sistolica   DOUBLE PRECISION NOT NULL,
    pressao_arterial_diastolica  DOUBLE PRECISION NOT NULL,
    saturacao_oxigenio           DOUBLE PRECISION NOT NULL,
    frequencia_respiratoria      DOUBLE PRECISION NOT NULL,
    temperatura                  DOUBLE PRECISION NOT NULL,
    gravidade_percebida          VARCHAR(16) NOT NULL,
    sintomas                     JSONB NOT NULL,
    score_valor                  INTEGER NOT NULL,
    score_algoritmo_versao       VARCHAR(8) NOT NULL,
    score_fatores                JSONB NOT NULL,
    criado_em                    TIMESTAMPTZ NOT NULL
);

-- Postgres nao indexa colunas de FK automaticamente -- sem este indice,
-- toda consulta futura por paciente_id (ex.: historico de Triagens de um
-- Paciente) faria sequential scan.
CREATE INDEX idx_triagens_paciente_id ON triagem_score.triagens(paciente_id);

-- Outbox (AD-3): {eventId (UUID v4), eventType="ScoreCalculado", occurredAt,
-- payload} gravado na mesma transacao local de RegistrarTriagem. Sem
-- publisher/relay real nesta fase (Design Notes da spec 2.1) -- nada le esta
-- tabela ainda. event_id e UNIQUE porque, quando o relay existir, uma
-- republicacao apos falha de rede reusa o mesmo eventId (nunca regenerado).
CREATE TABLE triagem_score.eventos_outbox (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    UUID NOT NULL UNIQUE,
    event_type  VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload     JSONB NOT NULL
);
