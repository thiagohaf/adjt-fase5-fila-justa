-- Alocacao (Story 3-3b1): registro de confirmacao de uma Sugestao de
-- Matching -- consumido por POST /v1/recursos/{id}/alocacoes
-- (ConfirmarAlocacao). alocacao_id (UUID v4, gerado pela aplicacao) e a PK
-- sintetica.
--
-- Os 2 indices unicos parciais abaixo (WHERE status = 'ATIVA') sao a UNICA
-- fonte de verdade sob concorrencia (Technical Decision do
-- epic-3-context.md): um Recurso so pode ter 1 Alocacao ativa, e um
-- Paciente so pode ter 1 Alocacao ativa -- uma segunda tentativa de INSERT
-- que viole qualquer um dos dois falha com 23505 (unique_violation), que
-- AlocacaoRepositorioAdapter traduz para RecursoJaAlocadoException /
-- PacienteJaAlocadoException conforme o nome da constraint.
CREATE TABLE matching_alocacao.alocacao (
    alocacao_id     UUID NOT NULL PRIMARY KEY,
    recurso_id      UUID NOT NULL,
    paciente_id     BIGINT NOT NULL,
    status          TEXT NOT NULL,
    confirmado_em   TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_alocacao_recurso_ativa
    ON matching_alocacao.alocacao (recurso_id)
    WHERE status = 'ATIVA';

CREATE UNIQUE INDEX ux_alocacao_paciente_ativa
    ON matching_alocacao.alocacao (paciente_id)
    WHERE status = 'ATIVA';
