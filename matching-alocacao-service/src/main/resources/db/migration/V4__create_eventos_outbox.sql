-- Story 3-3a: infraestrutura outbox propria do matching-alocacao-service
-- (AD-3), pre-requisito puro de infraestrutura para as Stories 3.3b
-- (confirmacao, AlocacaoConfirmada) e 3.3c (recusa/SugestaoGerada,
-- SugestaoRecusada) -- nenhum produtor real grava nesta tabela ainda.
--
-- Equivalente a triagem_score.eventos_outbox depois de
-- V1__create_triagem_schema.sql + V2__add_relay_columns_eventos_outbox.sql
-- combinadas numa unica migration nova, ja com as colunas do relay (Story
-- 3.0 de triagem-score-service) desde a primeira versao -- sem a lacuna
-- historica daquele servico (tabela criada na 2.1 sem version/correlation_id/
-- publicado_em, so adicionados depois na 3.0). event_id e UNIQUE porque uma
-- republicacao apos falha de rede reusa o mesmo eventId (nunca regenerado,
-- Boundaries da spec 3-3a).
CREATE TABLE matching_alocacao.eventos_outbox (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,
    event_type      VARCHAR(64) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    version         INTEGER NOT NULL,
    correlation_id  VARCHAR(128) NOT NULL,
    publicado_em    TIMESTAMPTZ,
    payload         JSONB NOT NULL
);

-- RelaySnsPublisherJob le "WHERE publicado_em IS NULL ORDER BY id" a cada
-- execucao do poller (mesmo Design Notes da spec 3.0 de
-- triagem-score-service) -- indice parcial evita sequential scan conforme a
-- tabela cresce (so indexa as linhas ainda pendentes de publicacao).
CREATE INDEX idx_eventos_outbox_pendentes ON matching_alocacao.eventos_outbox (id)
    WHERE publicado_em IS NULL;
