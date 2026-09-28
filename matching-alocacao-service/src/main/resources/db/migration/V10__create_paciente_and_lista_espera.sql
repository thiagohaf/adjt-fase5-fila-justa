-- Migration V10: Create paciente and lista_espera_entrada tables for Story 5.3

CREATE TABLE matching_alocacao.paciente (
    paciente_id BIGSERIAL PRIMARY KEY,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_paciente_cpf ON matching_alocacao.paciente(cpf);

CREATE TABLE matching_alocacao.lista_espera_entrada (
    entrada_id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL REFERENCES matching_alocacao.paciente(paciente_id),
    recurso_id UUID NOT NULL,
    data_solicitacao TIMESTAMP NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT uk_paciente_recurso UNIQUE (paciente_id, recurso_id)
);

CREATE INDEX idx_lista_espera_recurso ON matching_alocacao.lista_espera_entrada(recurso_id);
CREATE INDEX idx_lista_espera_data_solicitacao ON matching_alocacao.lista_espera_entrada(data_solicitacao);
