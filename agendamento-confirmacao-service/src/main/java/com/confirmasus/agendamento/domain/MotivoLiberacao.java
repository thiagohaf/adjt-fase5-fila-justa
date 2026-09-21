package com.confirmasus.agendamento.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Motivos de liberação de uma vaga (Spec 1.4, Boundaries & Constraints).
 * {@code RECUSA}: Paciente recusou presença via {@code POST /v1/agendamentos/{id}/recusa}.
 * {@code NAO_CONFIRMADO}: Paciente não confirmou presença dentro da janela (Story 1.5, fora de escopo).
 *
 * <p>{@code @Converter(autoApply = true)} garante que JPA converte automaticamente
 * {@code String motivoLiberacao} em/de {@code MotivoLiberacao} na hidratação/persistência
 * de {@code AgendamentoJpaEntity}.
 */
@Converter(autoApply = true)
public enum MotivoLiberacao implements AttributeConverter<MotivoLiberacao, String> {
    RECUSA,
    NAO_CONFIRMADO;

    @Override
    public String convertToDatabaseColumn(MotivoLiberacao attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public MotivoLiberacao convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MotivoLiberacao.valueOf(dbData);
    }
}
