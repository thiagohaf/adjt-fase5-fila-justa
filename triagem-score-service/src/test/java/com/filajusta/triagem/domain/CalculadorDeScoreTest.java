package com.filajusta.triagem.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Cobre o algoritmo de Score v1 (Design Notes da spec 2.1) e a I/O & Edge-Case
 * Matrix: "Mesmos inputs, 2 Triagens distintas" -&gt; Score identico, versao
 * registrada (FR-4, determinismo).
 */
class CalculadorDeScoreTest {

    private final LimitesSinaisVitais limites = LimitesSinaisVitaisFixture.padrao();
    private final CalculadorDeScore calculadora = new CalculadorDeScore();

    @Test
    void todosOsSinaisNoCentroDaFaixaEGravidadeLeveDaoScoreZero() {
        SinaisVitais sv = new SinaisVitais(120.0, 160.0, 90.0, 75.0, 32.5, 36.0, limites);

        Score score = calculadora.calcular(sv, GravidadePercebida.LEVE, limites);

        assertThat(score.getValor()).isZero();
        assertThat(score.getAlgoritmoVersao()).isEqualTo("v1");
        assertThat(score.getFatores()).hasSize(7);
        assertThat(score.getFatores())
                .allSatisfy(fator -> assertThat(fator.contribuicao()).isEqualTo(0.0));
    }

    @Test
    void todosOsSinaisNoExtremoEGravidadeCriticaDaoScoreCem() {
        SinaisVitais sv = new SinaisVitais(200.0, 260.0, 150.0, 100.0, 60.0, 42.0, limites);

        Score score = calculadora.calcular(sv, GravidadePercebida.CRITICA, limites);

        assertThat(score.getValor()).isEqualTo(100);
    }

    @Test
    void fatoresContribuintesSaoNomeadosCorretamente() {
        SinaisVitais sv = new SinaisVitais(120.0, 160.0, 90.0, 75.0, 32.5, 36.0, limites);

        Score score = calculadora.calcular(sv, GravidadePercebida.MODERADA, limites);

        assertThat(score.getFatores())
                .extracting(FatorContribuinte::fator)
                .containsExactly(
                        "frequencia_cardiaca",
                        "pressao_arterial_sistolica",
                        "pressao_arterial_diastolica",
                        "saturacao_oxigenio",
                        "frequencia_respiratoria",
                        "temperatura",
                        "gravidade_percebida");
    }

    @Test
    void gravidadeModeradaComVitaisNoCentroDaFaixaProduzScoreCinco() {
        // media = 0.33 / 7 = 0.04714...; x100 = 4.714...; arredondado = 5.
        SinaisVitais sv = new SinaisVitais(120.0, 160.0, 90.0, 75.0, 32.5, 36.0, limites);

        Score score = calculadora.calcular(sv, GravidadePercebida.MODERADA, limites);

        assertThat(score.getValor()).isEqualTo(5);
        assertThat(score.getFatores())
                .filteredOn(fator -> fator.fator().equals("gravidade_percebida"))
                .extracting(FatorContribuinte::contribuicao)
                .containsExactly(0.33);
    }

    @Test
    void mesmosInputsProduzemSempreOMesmoScore() {
        SinaisVitais sv1 = new SinaisVitais(95.0, 130.0, 85.0, 96.0, 18.0, 37.2, limites);
        SinaisVitais sv2 = new SinaisVitais(95.0, 130.0, 85.0, 96.0, 18.0, 37.2, limites);

        Score score1 = calculadora.calcular(sv1, GravidadePercebida.GRAVE, limites);
        Score score2 = calculadora.calcular(sv2, GravidadePercebida.GRAVE, limites);

        assertThat(score1.getValor()).isEqualTo(score2.getValor());
        assertThat(score1.getAlgoritmoVersao()).isEqualTo(score2.getAlgoritmoVersao());
        assertThat(score1.getFatores())
                .extracting(FatorContribuinte::fator, FatorContribuinte::contribuicao)
                .containsExactlyElementsOf(
                        score2.getFatores().stream()
                                .map(f -> tuple(f.fator(), f.contribuicao()))
                                .toList());
    }
}
