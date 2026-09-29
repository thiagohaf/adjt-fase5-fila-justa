-- LiberacaoAgendada (Story 3-4a1): registra que o Recurso de uma Alocacao
-- confirmada precisa voltar ao pool apos delay_segundos -- gravado por
-- ConfirmarAlocacao na mesma transacao da confirmacao (Boundaries da spec
-- 3-4a1). alocacao_id (mesmo UUID da linha em "alocacao") e a PK: uma
-- Alocacao confirmada agenda no maximo 1 liberacao, sem identidade
-- sintetica propria.
--
-- correlation_id propagado explicitamente pelo chamador -- nao e
-- recuperavel depois so pela tabela "alocacao", que nao tem essa coluna.
--
-- enviado_em comeca NULL e so e preenchido pelo relay da Story 3-4a2
-- (nenhum consumidor real ainda nesta story, Boundaries da spec).
CREATE TABLE matching_alocacao.liberacao_agendada (
    alocacao_id     UUID NOT NULL PRIMARY KEY,
    recurso_id      UUID NOT NULL,
    correlation_id  TEXT NOT NULL,
    delay_segundos  INTEGER NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    enviado_em      TIMESTAMPTZ
);

-- O relay da Story 3-4a2 vai ler "WHERE enviado_em IS NULL ORDER BY
-- criado_em" a cada execucao do poller (mesmo Design Notes de
-- idx_eventos_outbox_pendentes, V4__create_eventos_outbox.sql) -- indice
-- parcial evita sequential scan conforme a tabela cresce (so indexa as
-- linhas ainda pendentes de envio).
CREATE INDEX idx_liberacao_agendada_pendentes ON matching_alocacao.liberacao_agendada (criado_em)
    WHERE enviado_em IS NULL;
