package com.filajusta.triagem.domain;

import java.util.List;
import java.util.Objects;

/**
 * Score de prioridade calculado por {@link CalculadorDeScore} (FR-3, FR-4):
 * valor {@code 0..100}, versao do algoritmo (fixa {@code "v1"} nesta fase) e
 * a lista de fatores contribuintes que o compoe -- exposta na resposta de
 * {@code POST /v1/triagens} e no payload do evento {@code ScoreCalculado}.
 */
public final class Score {

    private final int valor;
    private final String algoritmoVersao;
    private final List<FatorContribuinte> fatores;

    public Score(int valor, String algoritmoVersao, List<FatorContribuinte> fatores) {
        if (valor < 0 || valor > 100) {
            throw new IllegalArgumentException("valor do Score deve estar entre 0 e 100: " + valor);
        }
        this.valor = valor;
        this.algoritmoVersao = Objects.requireNonNull(algoritmoVersao, "algoritmoVersao");
        this.fatores = List.copyOf(Objects.requireNonNull(fatores, "fatores"));
    }

    public int getValor() {
        return valor;
    }

    public String getAlgoritmoVersao() {
        return algoritmoVersao;
    }

    public List<FatorContribuinte> getFatores() {
        return fatores;
    }
}
