-- Remove a infraestrutura de priorizacao clinica legada (replica de Score
-- de gravidade + fila ordenada por score/aging), decomissionada por
-- restricao legal: a Sugestao de Repasse passa a ser FIFO pura por Lista de
-- Espera (criado_em), nunca por gravidade/score (AD-6, Architecture Spine).
--
-- matching_alocacao.recurso.especificidade_rank NAO e removida aqui: ainda
-- e usada por LiberacaoDuracaoProperties#duracaoParaRank para calibrar a
-- duracao de liberacao automatica por tipo de recurso -- nao decide mais
-- nenhuma sugestao/prioridade de paciente, mas nao e dado morto.
DROP TABLE matching_alocacao.score_replica;
