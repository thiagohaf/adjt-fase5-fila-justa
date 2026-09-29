-- Story 5.1: Expandir modelo Recurso com especialidade e unidade
-- Adiciona dois campos STRING ao Recurso para suportar categorização
-- de recursos por tipo de atendimento e localização.
ALTER TABLE matching_alocacao.recurso
ADD COLUMN especialidade TEXT,
ADD COLUMN unidade TEXT;
