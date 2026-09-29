-- Recurso (Story 3.2b2): agregado de capacidade de atendimento (leito,
-- especialista, sala etc.) do matching-alocacao-service, upsertado
-- idempotentemente por codigo_recurso via POST /internal/recursos
-- (UpsertRecurso). Dependencia da Story 3-2b3 (sugestao com tiers de
-- especificidade_rank) e do seed-adapter do Epic 5 (Intent da spec 3.2b2).
--
-- recurso_id (UUID v4, gerado pela aplicacao) e a PK sintetica -- estavel
-- entre upserts: o INSERT ... ON CONFLICT (codigo_recurso) DO UPDATE
-- (RecursoJpaRepository#upsert) nunca sobrescreve esta coluna, entao um
-- upsert repetido para o mesmo codigo_recurso sempre preserva o recurso_id
-- ja atribuido na primeira insercao.
CREATE TABLE matching_alocacao.recurso (
    recurso_id          UUID NOT NULL PRIMARY KEY,
    codigo_recurso       TEXT NOT NULL UNIQUE,
    especificidade_rank  INTEGER NOT NULL,
    disponivel           BOOLEAN NOT NULL
);
