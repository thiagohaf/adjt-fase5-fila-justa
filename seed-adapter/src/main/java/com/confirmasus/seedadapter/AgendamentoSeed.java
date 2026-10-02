package com.confirmasus.seedadapter;

import java.util.UUID;

/**
 * Representação de um Agendamento na seed-data JSON.
 *
 * Story 5.2: DTO para desserialização de {@code seed-data.json}.
 * Campos: cpf, recursoId (UUID), dataHoraAgendamento (ISO 8601), estado (enum).
 *
 * Estados suportados:
 * - AGUARDANDO_JANELA: estado inicial após POST /v1/agendamentos
 * - AGUARDANDO_CONFIRMACAO: após abrirJanela (Story 1.2)
 * - CONFIRMADO: após confirmarPresenca (Story 1.3)
 * - LIBERADO: após recusarPresenca (Story 1.4)
 */
public class AgendamentoSeed {
    private String cpf;
    private UUID recursoId;
    private String dataHoraAgendamento;
    private String estado;

    // Construtores
    public AgendamentoSeed() {
    }

    public AgendamentoSeed(String cpf, UUID recursoId, String dataHoraAgendamento, String estado) {
        this.cpf = cpf;
        this.recursoId = recursoId;
        this.dataHoraAgendamento = dataHoraAgendamento;
        this.estado = estado;
    }

    // Getters e Setters
    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public void setRecursoId(UUID recursoId) {
        this.recursoId = recursoId;
    }

    public String getDataHoraAgendamento() {
        return dataHoraAgendamento;
    }

    public void setDataHoraAgendamento(String dataHoraAgendamento) {
        this.dataHoraAgendamento = dataHoraAgendamento;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
