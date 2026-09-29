-- Remove a liberacao agendada (Stories 3-4a/3-4b, mecanismo de delay via SQS
-- standard) -- superada pelo AD-3/AD-5 do spine: a vaga volta ao pool pelo
-- evento VagaLiberada, nao por uma mensagem atrasada.
DROP TABLE IF EXISTS matching_alocacao.liberacao_agendada;
