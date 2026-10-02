package com.confirmasus.auditoria.domain;

/**
 * Tipo/categoria de Paciente (referência ao serviço de pacientes).
 *
 * <p>Usado para filtrar DecisaoAuditoria por tipo do paciente subjacente (Story 4.4b).
 * Valores sincronizados com o campo tipoPaciente do paciente-service.
 *
 * <p>Tipos:
 * <ul>
 *   <li>{@code PRIORITARIO} - Paciente com prioridade de atendimento
 *   <li>{@code REGULAR} - Paciente com atendimento regular
 * </ul>
 */
public enum TipoPaciente {
    PRIORITARIO,
    REGULAR
}
