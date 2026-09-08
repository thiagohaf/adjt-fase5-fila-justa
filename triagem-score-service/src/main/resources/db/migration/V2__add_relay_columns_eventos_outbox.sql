-- Story 3.0 (relay/publisher real do ScoreCalculado, AD-3): fecha o gap
-- entre a tabela eventos_outbox (Story 2.1) e o envelope ja fixado no
-- Epic 2 -- {eventId, eventType, occurredAt, version, correlationId,
-- payload}. Tabela nunca foi implantada em nenhum ambiente real
-- (deferred-work.md) -- sem risco de linha legada sem os valores novos,
-- entao version/correlation_id entram como NOT NULL sem DEFAULT: todo
-- INSERT novo (EventoOutboxRepositorioAdapter) ja preenche os dois.
ALTER TABLE triagem_score.eventos_outbox
    ADD COLUMN version        INTEGER,
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN publicado_em   TIMESTAMPTZ;

ALTER TABLE triagem_score.eventos_outbox
    ALTER COLUMN version SET NOT NULL,
    ALTER COLUMN correlation_id SET NOT NULL;

-- RelaySnsPublisherJob le "WHERE publicado_em IS NULL ORDER BY id" a cada
-- execucao do poller (Design Notes da spec 3.0) -- indice parcial evita
-- sequential scan conforme a tabela cresce (so indexa as linhas ainda
-- pendentes de publicacao).
CREATE INDEX idx_eventos_outbox_pendentes ON triagem_score.eventos_outbox (id)
    WHERE publicado_em IS NULL;
