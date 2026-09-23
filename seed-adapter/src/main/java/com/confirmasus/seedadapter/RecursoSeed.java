package com.confirmasus.seedadapter;

/**
 * Representação de um Recurso na seed-data JSON.
 *
 * Story 5.1: DTO para desserialização de {@code seed-data.json}.
 * Campos: codigoRecurso, especialidade, unidade, especificidadeRank, disponivel.
 */
public class RecursoSeed {
    private String codigoRecurso;
    private String especialidade;
    private String unidade;
    private int especificidadeRank;
    private boolean disponivel;

    // Construtores
    public RecursoSeed() {
    }

    public RecursoSeed(String codigoRecurso, String especialidade, String unidade,
                      int especificidadeRank, boolean disponivel) {
        this.codigoRecurso = codigoRecurso;
        this.especialidade = especialidade;
        this.unidade = unidade;
        this.especificidadeRank = especificidadeRank;
        this.disponivel = disponivel;
    }

    // Getters e Setters
    public String getCodigoRecurso() {
        return codigoRecurso;
    }

    public void setCodigoRecurso(String codigoRecurso) {
        this.codigoRecurso = codigoRecurso;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getUnidade() {
        return unidade;
    }

    public void setUnidade(String unidade) {
        this.unidade = unidade;
    }

    public int getEspecificidadeRank() {
        return especificidadeRank;
    }

    public void setEspecificidadeRank(int especificidadeRank) {
        this.especificidadeRank = especificidadeRank;
    }

    public boolean isDisponivel() {
        return disponivel;
    }

    public void setDisponivel(boolean disponivel) {
        this.disponivel = disponivel;
    }
}
