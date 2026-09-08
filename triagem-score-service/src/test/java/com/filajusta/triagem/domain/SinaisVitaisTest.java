package com.filajusta.triagem.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a validacao de AD-11 em {@link SinaisVitais}: presenca obrigatoria,
 * faixa fisiologica plausivel e PAS &gt; PAD -- todas lancando
 * {@link SinalVitalInvalidoException} nomeando o campo, antes de qualquer
 * calculo de Score (Boundaries/I-O Matrix da spec 2.1).
 */
class SinaisVitaisTest {

    private final LimitesSinaisVitais limites = LimitesSinaisVitaisFixture.padrao();

    @Test
    void aceitaSinaisVitaisDentroDasFaixas() {
        SinaisVitais sv = new SinaisVitais(80.0, 120.0, 80.0, 98.0, 16.0, 36.5, limites);

        assertThat(sv.getFrequenciaCardiaca()).isEqualTo(80.0);
        assertThat(sv.getPressaoArterialSistolica()).isEqualTo(120.0);
        assertThat(sv.getPressaoArterialDiastolica()).isEqualTo(80.0);
        assertThat(sv.getSaturacaoOxigenio()).isEqualTo(98.0);
        assertThat(sv.getFrequenciaRespiratoria()).isEqualTo(16.0);
        assertThat(sv.getTemperatura()).isEqualTo(36.5);
    }

    @Test
    void aceitaValoresNosLimitesExtremosDaFaixa() {
        SinaisVitais sv = new SinaisVitais(40.0, 260.0, 150.0, 50.0, 5.0, 30.0, limites);

        assertThat(sv.getFrequenciaCardiaca()).isEqualTo(40.0);
        assertThat(sv.getTemperatura()).isEqualTo(30.0);
    }

    @Test
    void rejeitaFrequenciaCardiacaAusente() {
        assertThatThrownBy(() -> new SinaisVitais(null, 120.0, 80.0, 98.0, 16.0, 36.5, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("frequenciaCardiaca"));
    }

    @Test
    void rejeitaFrequenciaCardiacaForaDaFaixa() {
        assertThatThrownBy(() -> new SinaisVitais(201.0, 120.0, 80.0, 98.0, 16.0, 36.5, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("frequenciaCardiaca"));
    }

    @Test
    void rejeitaPressaoSistolicaAusente() {
        assertThatThrownBy(() -> new SinaisVitais(80.0, null, 80.0, 98.0, 16.0, 36.5, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("pressaoArterialSistolica"));
    }

    @Test
    void rejeitaPressaoDiastolicaForaDaFaixa() {
        assertThatThrownBy(() -> new SinaisVitais(80.0, 120.0, 151.0, 98.0, 16.0, 36.5, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("pressaoArterialDiastolica"));
    }

    @Test
    void rejeitaPasMenorOuIgualAPad() {
        assertThatThrownBy(() -> new SinaisVitais(80.0, 80.0, 80.0, 98.0, 16.0, 36.5, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("pressaoArterialSistolica"));
    }

    @Test
    void rejeitaSaturacaoOxigenioAusente() {
        assertThatThrownBy(() -> new SinaisVitais(80.0, 120.0, 80.0, null, 16.0, 36.5, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("saturacaoOxigenio"));
    }

    @Test
    void rejeitaFrequenciaRespiratoriaForaDaFaixa() {
        assertThatThrownBy(() -> new SinaisVitais(80.0, 120.0, 80.0, 98.0, 61.0, 36.5, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("frequenciaRespiratoria"));
    }

    @Test
    void rejeitaTemperaturaAusente() {
        assertThatThrownBy(() -> new SinaisVitais(80.0, 120.0, 80.0, 98.0, 16.0, null, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("temperatura"));
    }

    @Test
    void rejeitaTemperaturaForaDaFaixa() {
        assertThatThrownBy(() -> new SinaisVitais(80.0, 120.0, 80.0, 98.0, 16.0, 42.1, limites))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo())
                        .isEqualTo("temperatura"));
    }
}
