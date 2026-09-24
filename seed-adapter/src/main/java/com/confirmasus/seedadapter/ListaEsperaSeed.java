package com.confirmasus.seedadapter;

import java.util.UUID;

/**
 * Representação de uma entrada de Lista de Espera na seed-data JSON.
 *
 * Story 5.3: DTO para desserialização de {@code seed-data.json}.
 * Campos: cpf, recursoId (UUID), dataSolicitacao (ISO 8601 datetime).
 *
 * Nota: pacienteId é resolvido internamente pelo gateway via gRPC (Story 1.1 dependency).
 * Seed-adapter nunca persiste CPF; CPF é descartado após resolução.
 */
public class ListaEsperaSeed {
    private String cpf;
    private UUID recursoId;
    private String dataSolicitacao;

    // Construtores
    public ListaEsperaSeed() {
    }

    public ListaEsperaSeed(String cpf, UUID recursoId, String dataSolicitacao) {
        this.cpf = cpf;
        this.recursoId = recursoId;
        this.dataSolicitacao = dataSolicitacao;
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

    public String getDataSolicitacao() {
        return dataSolicitacao;
    }

    public void setDataSolicitacao(String dataSolicitacao) {
        this.dataSolicitacao = dataSolicitacao;
    }
}
