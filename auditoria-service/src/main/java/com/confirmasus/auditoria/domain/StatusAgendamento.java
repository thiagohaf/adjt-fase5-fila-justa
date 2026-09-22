package com.confirmasus.auditoria.domain;

/**
 * Estados do Agendamento (referência ao enum do agendamento-confirmacao-service).
 *
 * <p>Usado para filtrar DecisaoAuditoria por status do agendamento subjacente (Story 4.4a).
 * Valores sincronizados com {@code com.confirmasus.agendamento.domain.StatusAgendamento}
 * do agendamento-confirmacao-service.
 *
 * <p>Estados:
 * <ul>
 *   <li>{@code AGUARDANDO_JANELA} - Agendamento criado, aguarda abertura da janela de confirmação
 *   <li>{@code AGUARDANDO_CONFIRMACAO} - Janela de confirmação aberta, aguarda confirmação do paciente
 *   <li>{@code CONFIRMADO} - Paciente confirmou presença (estado terminal)
 *   <li>{@code LIBERADO} - Vaga foi liberada (estado terminal)
 * </ul>
 */
public enum StatusAgendamento {
    AGUARDANDO_JANELA,
    AGUARDANDO_CONFIRMACAO,
    CONFIRMADO,
    LIBERADO
}
