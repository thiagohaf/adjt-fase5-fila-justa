-- Story 3.2b1: propaga numeroSequencialTriagem (numero sequencial da
-- Triagem que originou o Score, exposto por GET /internal/scores desde a
-- Story 3.2a) ate ScoreReplica -- usado como desempate residual de AD-5 em
-- ConsultarFilaPriorizada quando Prioridade Efetiva e occurred_at empatam.
-- NULL quando a origem do dado (bootstrap ou evento SQS) nao trouxe o
-- campo -- nunca bloqueia escrita nem quebra a ordenacao (nulls-last).
ALTER TABLE matching_alocacao.score_replica
    ADD COLUMN numero_sequencial_triagem BIGINT NULL;
