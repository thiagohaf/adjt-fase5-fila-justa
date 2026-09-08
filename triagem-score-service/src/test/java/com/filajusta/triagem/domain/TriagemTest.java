package com.filajusta.triagem.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a normalizacao/validacao de {@code sintomas} no construtor de
 * {@link Triagem}: {@code null} vira lista vazia; um elemento nulo dentro da
 * lista e um {@code 400} nomeando o campo {@code sintomas} (e nao um NPE de
 * {@code List.copyOf}, que escaparia como {@code 500}).
 */
class TriagemTest {

    private final LimitesSinaisVitais limites = LimitesSinaisVitaisFixture.padrao();
    private final SinaisVitais sinaisVitais =
            new SinaisVitais(80.0, 120.0, 80.0, 98.0, 16.0, 36.5, limites);
    private final Score score = new CalculadorDeScore().calcular(sinaisVitais, GravidadePercebida.LEVE, limites);

    @Test
    void sintomasNuloViraListaVazia() {
        Triagem triagem = new Triagem(null, 1L, sinaisVitais, GravidadePercebida.LEVE, null, score, Instant.now());

        assertThat(triagem.getSintomas()).isEmpty();
    }

    @Test
    void sintomasComValoresValidosSaoPreservados() {
        Triagem triagem = new Triagem(
                null, 1L, sinaisVitais, GravidadePercebida.LEVE, List.of("tosse", "febre"), score, Instant.now());

        assertThat(triagem.getSintomas()).containsExactly("tosse", "febre");
    }

    @Test
    void sintomasComElementoNuloLanca400NomeandoOCampo() {
        List<String> sintomasComNulo = Arrays.asList("tosse", null);

        assertThatThrownBy(() -> new Triagem(
                null, 1L, sinaisVitais, GravidadePercebida.LEVE, sintomasComNulo, score, Instant.now()))
                .isInstanceOf(SinalVitalInvalidoException.class)
                .satisfies(ex -> assertThat(((SinalVitalInvalidoException) ex).getCampo()).isEqualTo("sintomas"));
    }
}
