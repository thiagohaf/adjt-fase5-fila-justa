-- SugestaoRecusada (Story 3-3c1): registro do par (recurso_id, paciente_id)
-- recusado -- consumido por POST /v1/recursos/{id}/alocacoes/recusa
-- (RecusarSugestao). PK composta (recurso_id, paciente_id): recusar o mesmo
-- par de novo eh um upsert idempotente (atualiza motivo/recusado_em), nunca
-- duplica linha nem falha (Boundaries da spec 3-3c1) -- ao contrario de
-- matching_alocacao.alocacao (V5__create_alocacao.sql), nao existe PK
-- sintetica aqui: a identidade do par recusado JA E a PK.
--
-- Consumida pela 3-3c2 (fora do escopo desta story) para impedir que o
-- mesmo Paciente seja resugerido para o mesmo Recurso.
CREATE TABLE matching_alocacao.sugestao_recusada (
    recurso_id      UUID NOT NULL,
    paciente_id     BIGINT NOT NULL,
    motivo          TEXT NOT NULL,
    recusado_em     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (recurso_id, paciente_id)
);
