-- Schema proprio do agendamento-confirmacao-service (AD-9) -- isolamento por
-- schema no mesmo cluster Postgres 18 compartilhado; role de banco dedicado
-- + REVOKE cross-schema fica deferido (deferred-work.md), mesmo padrao do
-- auth-service. Migration reescrita do zero (AD-1) -- nenhuma migracao de
-- dado do schema antigo (triagem_score, servico descartado).
CREATE SCHEMA IF NOT EXISTS agendamento_confirmacao;

-- Paciente identificado por CPF (FR-2). UNIQUE(cpf) e a rede de seguranca da
-- idempotencia de ResolverOuCriarPaciente (application/command) sob corrida
-- de dois registros simultaneos para o mesmo CPF. Mesmo desenho da tabela
-- do servico anterior (so o schema muda).
CREATE TABLE agendamento_confirmacao.pacientes (
    id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cpf VARCHAR(11) NOT NULL UNIQUE
);

-- Agendamento (Story 1.1): associa um Paciente a um recurso (validado so
-- por formato, AD-1 -- sem FK para nenhum catalogo local de Recurso, que
-- nao existe neste servico) e uma data/hora futura no momento do registro.
-- status nasce sempre AGUARDANDO_JANELA; os demais valores do enum
-- StatusAgendamento (AGUARDANDO_CONFIRMACAO, CONFIRMADO, LIBERADO) so se
-- tornam alcancaveis nas proximas stories do Epic 1 (Design Notes da spec
-- 1.1 -- a coluna ja comporta esses valores, sem migracao futura de tipo).
CREATE TABLE agendamento_confirmacao.agendamentos (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id            BIGINT NOT NULL REFERENCES agendamento_confirmacao.pacientes(id),
    recurso_id             UUID NOT NULL,
    data_hora_agendamento  TIMESTAMPTZ NOT NULL,
    status                 VARCHAR(32) NOT NULL,
    criado_em              TIMESTAMPTZ NOT NULL
);

-- Postgres nao indexa colunas de FK automaticamente -- sem este indice, toda
-- consulta futura por paciente_id (ex.: historico de Agendamentos de um
-- Paciente, Story 1.2+) faria sequential scan.
CREATE INDEX idx_agendamentos_paciente_id ON agendamento_confirmacao.agendamentos(paciente_id);

-- recurso_id nao tem FK local (AD-1 -- sem catalogo de Recurso neste
-- servico), mas e o campo mais provavel de ser consultado nas proximas
-- stories (ex.: checar disponibilidade/Agendamentos ativos de um recurso) --
-- mesmo raciocinio do indice de paciente_id acima, sem ele essa consulta
-- futura tambem faria sequential scan.
CREATE INDEX idx_agendamentos_recurso_id ON agendamento_confirmacao.agendamentos(recurso_id);
