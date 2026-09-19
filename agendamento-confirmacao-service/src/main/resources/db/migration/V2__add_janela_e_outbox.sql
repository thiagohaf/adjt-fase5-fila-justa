-- Spec 1.2 (Abertura da Janela de Confirmacao e Notificacao, AD-3/AD-4):
-- colunas de janela em agendamentos + infraestrutura outbox propria do
-- agendamento-confirmacao-service.

-- janela_abre_em (AD-4): calculada e persistida no momento do
-- RegistrarAgendamento (Story 1.1, ja fechada) -- NOT NULL porque
-- AbrirJanelaDeConfirmacao (poller) filtra por ela em toda execucao;
-- adicionada sem DEFAULT porque nenhuma linha existente precisa ser
-- retroativamente preenchida neste ambiente (schema recem-criado na Story
-- 1.1, sem dados de producao). janela_expira_em fica nullable -- so
-- preenchida pela Story 1.5 (poller de expiracao, fora de escopo desta
-- spec), a coluna ja nasce aqui para nao exigir migracao de tipo futura
-- (mesmo raciocinio do enum StatusAgendamento, Design Notes da spec 1.1).
ALTER TABLE agendamento_confirmacao.agendamentos
    ADD COLUMN janela_abre_em TIMESTAMPTZ,
    ADD COLUMN janela_expira_em TIMESTAMPTZ;

-- Sem NOT NULL direto no ADD COLUMN (postgres exigiria um DEFAULT para
-- validar linhas ja existentes) -- schema ainda vazio nesta fase do
-- projeto, entao o UPDATE abaixo e um no-op seguro antes de travar a
-- constraint real que AgendamentoJpaEntity espera (nullable = false).
UPDATE agendamento_confirmacao.agendamentos SET janela_abre_em = criado_em WHERE janela_abre_em IS NULL;
ALTER TABLE agendamento_confirmacao.agendamentos ALTER COLUMN janela_abre_em SET NOT NULL;

-- AbrirJanelaDeConfirmacao le "WHERE status = 'AGUARDANDO_JANELA' AND
-- janela_abre_em <= now()" a cada execucao do poller -- indice parcial evita
-- sequential scan conforme a tabela cresce (so indexa as linhas ainda
-- aguardando abertura de janela), mesmo raciocinio de
-- idx_eventos_outbox_pendentes (matching-alocacao-service V4).
CREATE INDEX idx_agendamentos_aguardando_janela ON agendamento_confirmacao.agendamentos (janela_abre_em)
    WHERE status = 'AGUARDANDO_JANELA';

-- Infraestrutura outbox propria deste servico (AD-3) -- mesmo DDL de
-- matching-alocacao-service/.../V4__create_eventos_outbox.sql, schema
-- agendamento_confirmacao. event_id e UNIQUE porque uma republicacao apos
-- falha de rede reusa o mesmo eventId (nunca regenerado, mesmo Boundary do
-- molde).
CREATE TABLE agendamento_confirmacao.eventos_outbox (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,
    event_type      VARCHAR(64) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    version         INTEGER NOT NULL,
    correlation_id  VARCHAR(128) NOT NULL,
    publicado_em    TIMESTAMPTZ,
    payload         JSONB NOT NULL
);

-- RelaySnsPublisherJob le "WHERE publicado_em IS NULL ORDER BY id" a cada
-- execucao do poller -- indice parcial evita sequential scan conforme a
-- tabela cresce (mesmo raciocinio de idx_agendamentos_aguardando_janela
-- acima).
CREATE INDEX idx_eventos_outbox_pendentes ON agendamento_confirmacao.eventos_outbox (id)
    WHERE publicado_em IS NULL;
