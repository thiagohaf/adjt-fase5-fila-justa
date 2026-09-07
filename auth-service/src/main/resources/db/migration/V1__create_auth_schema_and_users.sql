-- Schema proprio do auth-service (AD-9) -- isolamento por schema no mesmo
-- cluster Postgres 18 compartilhado; role de banco dedicado + REVOKE
-- cross-schema fica deferido (deferred-work.md) ate um 2o servico conectar.
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.usuarios (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(60)  NOT NULL,
    role          VARCHAR(32)  NOT NULL
);

-- Usuarios sinteticos pre-cadastrados (AD-14) -- nao ha CRUD de usuario via
-- API (fora de escopo). Hash BCrypt (custo 10,
-- org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder, mesma
-- classe usada em runtime para verificar). Senha em texto puro de cada
-- usuario sintetico (dado de demo, nao segredo de producao real):
--   regulador -> regulador#2026
--   triagem   -> triagem#2026
--   auditor   -> auditor#2026
INSERT INTO auth.usuarios (username, password_hash, role) VALUES
    ('regulador', '$2a$10$90H8xYjdwjybgqujfzET3uXNwS6AQKPo8tA8nU9iNx/d6gmgDcQuy', 'REGULADOR'),
    ('triagem',   '$2a$10$YjKYJBg/TrWd1z0mMSytWO8SMrxD0XH.d3WkgXHvBN70JUKCdcBNC', 'TRIAGEM'),
    ('auditor',   '$2a$10$QL4p4HVFAjrGctdVFMslT.PaiSaIMewgISJCYPcjcCk894BOzEHru', 'AUDITOR');
