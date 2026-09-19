-- Schema proprio do matching-alocacao-service (AD-9) -- isolamento por
-- schema no mesmo cluster Postgres 18 compartilhado; role de banco dedicado
-- + REVOKE cross-schema fica deferido (deferred-work.md), mesmo padrao dos
-- demais servicos.
CREATE SCHEMA IF NOT EXISTS matching_alocacao;

-- Replica local, somente-leitura, do Score de um Paciente (Story 3.1b) --
-- mantida por ScoreCalculadoConsumerJob (infrastructure/relay) via upsert
-- idempotente: last-write-wins por occurred_at, desempate por event_id em
-- ordem lexicografica quando occurred_at empata (Boundaries da spec 3.1b,
-- ver ScoreReplicaJpaRepository#upsertSeMaisRecente). paciente_id e a PK --
-- uma linha por Paciente, sempre a versao mais recente conhecida.
--
-- Sem calculo de Prioridade Efetiva/Aging nesta fase -- isso e a Story 3.1c,
-- onde a replica finalmente tem um consumidor real (GET /v1/fila).
CREATE TABLE matching_alocacao.score_replica (
    paciente_id BIGINT NOT NULL PRIMARY KEY,
    score       INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    event_id    UUID NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
