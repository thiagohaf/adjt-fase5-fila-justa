package com.filajusta.triagem.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre o parsing/pesos fixos de {@link GravidadePercebida} (Design Notes da
 * spec 2.1: LEVE=0, MODERADA=0.33, GRAVE=0.66, CRITICA=1).
 */
class GravidadePercebidaTest {

    @ParameterizedTest
    @CsvSource({
            "LEVE, 0.0",
            "MODERADA, 0.33",
            "GRAVE, 0.66",
            "CRITICA, 1.0",
            "leve, 0.0",
    })
    void resolvePesoPorNivel(String texto, double pesoEsperado) {
        assertThat(GravidadePercebida.fromTexto(texto).getPeso()).isEqualTo(pesoEsperado);
    }

    @Test
    void rejeitaValorNulo() {
        assertThatThrownBy(() -> GravidadePercebida.fromTexto(null))
                .isInstanceOf(GravidadeInvalidaException.class);
    }

    @Test
    void rejeitaValorEmBranco() {
        assertThatThrownBy(() -> GravidadePercebida.fromTexto("  "))
                .isInstanceOf(GravidadeInvalidaException.class);
    }

    @Test
    void rejeitaValorForaDoConjunto() {
        assertThatThrownBy(() -> GravidadePercebida.fromTexto("SEVERA"))
                .isInstanceOf(GravidadeInvalidaException.class);
    }
}
