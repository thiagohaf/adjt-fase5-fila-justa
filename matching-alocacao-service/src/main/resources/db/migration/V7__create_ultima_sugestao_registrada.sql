-- UltimaSugestaoRegistrada (Story 3-3c2b1): registro de qual foi o ultimo
-- pacienteId sugerido para cada Recurso -- porto de apoio ao rastreamento
-- AD-10 (evento SugestaoGerada so quando a sugestao de um Recurso muda).
-- PK simples (recurso_id): so existe UMA ultima sugestao por Recurso, ao
-- contrario de matching_alocacao.sugestao_recusada (V6, PK composta
-- recurso_id+paciente_id, onde varios pacientes recusados coexistem para o
-- mesmo Recurso). Registrar de novo o mesmo Recurso e um upsert idempotente
-- (atualiza paciente_id/registrado_em), nunca duplica linha nem falha
-- (Boundaries da spec 3-3c2b1).
--
-- Infraestrutura pura, sem consumidor real ainda -- mesmo padrao da Story
-- 3-3a para o outbox: pre-requisito puro, consumido pela proxima sub-story
-- (3-3c2b2), que aplica o rastreamento em ConsultarSugestaoRecurso.
CREATE TABLE matching_alocacao.ultima_sugestao_registrada (
    recurso_id      UUID PRIMARY KEY,
    paciente_id     BIGINT NOT NULL,
    registrado_em   TIMESTAMPTZ NOT NULL
);
