package com.filajusta.triagem.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Cobre {@link FaixaVital#subnota(double)} -- a distancia normalizada ao
 * centro da faixa usada pelo algoritmo de Score v1 (Design Notes da spec
 * 2.1): centro da faixa -&gt; subnota 0; qualquer extremo -&gt; subnota 1.
 */
class FaixaVitalTest {

    private final FaixaVital faixa = new FaixaVital(40, 200); // frequencia cardiaca

    @Test
    void valorNoCentroDaFaixaTemSubnotaZero() {
        assertThat(faixa.subnota(120)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void valorNoLimiteMinimoTemSubnotaUm() {
        assertThat(faixa.subnota(40)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void valorNoLimiteMaximoTemSubnotaUm() {
        assertThat(faixa.subnota(200)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void valorAMeioCaminhoDoCentroAoLimiteTemSubnotaMeio() {
        assertThat(faixa.subnota(160)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void rejeitaFaixaComMinimoMaiorOuIgualAoMaximo() {
        assertThatThrownBy(() -> new FaixaVital(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceitaValorDentroDaFaixaSemLancar() {
        faixa.exigirDentroDaFaixa("frequenciaCardiaca", 120);
    }

    @Test
    void rejeitaValorForaDaFaixa() {
        assertThatThrownBy(() -> faixa.exigirDentroDaFaixa("frequenciaCardiaca", 201))
                .isInstanceOf(SinalVitalInvalidoException.class);
    }

    @Test
    void rejeitaNaN() {
        // Double.NaN falha toda comparacao (<, >), entao sem o guard
        // explicito Double.isNaN um NaN passaria a validacao e produziria um
        // Score enganoso (subnota 0) em vez de 400.
        assertThatThrownBy(() -> faixa.exigirDentroDaFaixa("frequenciaCardiaca", Double.NaN))
                .isInstanceOf(SinalVitalInvalidoException.class);
    }
}
