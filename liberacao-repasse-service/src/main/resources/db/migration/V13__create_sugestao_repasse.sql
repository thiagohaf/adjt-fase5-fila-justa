-- SugestaoRepasse (Story 6.1, AD-6): uma linha por Vaga liberada
-- (agendamento_id). A UNIQUE(agendamento_id) e a chave de idempotencia do
-- consumidor de VagaLiberada -- a janela de dedup do SQS FIFO expira em 5 min
-- e o evento pode ser reentregue/redrive de DLQ muito depois.
--
-- Recusar uma sugestao reatribui paciente_id ao proximo candidato mantendo
-- PENDENTE (a Vaga e a mesma); ESGOTADA = Lista de Espera sem candidato
-- elegivel (sem sugestao pendente, paciente_id NULL). ConfirmarRepasse e
-- RecusarSugestaoRepasse sao UPDATE ... WHERE status = 'PENDENTE': a
-- perdedora de uma corrida afeta 0 linhas e recebe 409.
CREATE TABLE matching_alocacao.sugestao_repasse (
    sugestao_id     UUID PRIMARY KEY,
    agendamento_id  BIGINT NOT NULL,
    recurso_id      UUID NOT NULL,
    paciente_id     BIGINT,
    status          TEXT NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    decidido_em     TIMESTAMPTZ,
    CONSTRAINT ux_sugestao_repasse_agendamento UNIQUE (agendamento_id),
    CONSTRAINT ck_sugestao_repasse_status CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'ESGOTADA')),
    CONSTRAINT ck_sugestao_repasse_paciente CHECK ((status = 'ESGOTADA') = (paciente_id IS NULL))
);

CREATE INDEX ix_sugestao_repasse_recurso_pendente
    ON matching_alocacao.sugestao_repasse (recurso_id, criado_em DESC)
    WHERE status = 'PENDENTE';

-- Recusa vale so para a Vaga atual (agendamento_id), nao para o Recurso
-- inteiro: quem recusou uma Vaga volta a ser elegivel nas proximas. Linhas
-- legadas (agendamento_id NULL, do fluxo antigo por Recurso) deixam de
-- excluir alguem.
ALTER TABLE matching_alocacao.sugestao_recusada DROP CONSTRAINT sugestao_recusada_pkey;
ALTER TABLE matching_alocacao.sugestao_recusada ADD COLUMN agendamento_id BIGINT;
CREATE UNIQUE INDEX ux_sugestao_recusada_vaga_paciente
    ON matching_alocacao.sugestao_recusada (agendamento_id, paciente_id);

-- Substituida: a sugestao agora e persistida, nao recalculada a cada GET.
DROP TABLE matching_alocacao.ultima_sugestao_registrada;
