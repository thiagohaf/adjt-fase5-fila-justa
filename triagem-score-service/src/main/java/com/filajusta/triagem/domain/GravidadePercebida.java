package com.filajusta.triagem.domain;

import java.util.Locale;

/**
 * Gravidade percebida relatada na Triagem -- contribui um peso fixo por
 * nivel na formula do Score v1 ({@code [ASSUMPTION]}, Design Notes da spec
 * 2.1): {@code LEVE=0, MODERADA=0.33, GRAVE=0.66, CRITICA=1}.
 */
public enum GravidadePercebida {

    LEVE(0.0),
    MODERADA(0.33),
    GRAVE(0.66),
    CRITICA(1.0);

    private final double peso;

    GravidadePercebida(double peso) {
        this.peso = peso;
    }

    public double getPeso() {
        return peso;
    }

    /**
     * @throws GravidadeInvalidaException se {@code valor} for nulo, em
     *                                     branco ou fora de
     *                                     {LEVE, MODERADA, GRAVE, CRITICA}.
     */
    public static GravidadePercebida fromTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new GravidadeInvalidaException("gravidadePercebida e obrigatorio");
        }
        try {
            return GravidadePercebida.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new GravidadeInvalidaException(
                    "gravidadePercebida invalido: '" + valor + "' -- valores aceitos: LEVE, MODERADA, GRAVE, CRITICA");
        }
    }
}
