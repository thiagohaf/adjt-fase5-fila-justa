package com.filajusta.triagem.domain;

/**
 * Fixture de teste com as faixas AD-11 tal como configuradas em
 * {@code application.yml} (filajusta.triagem.limites.*): FC 40-200, PAS
 * 60-260, PAD 30-150, SpO2 50-100, FR 5-60, Temp 30-42.
 */
final class LimitesSinaisVitaisFixture {

    private LimitesSinaisVitaisFixture() {
    }

    static LimitesSinaisVitais padrao() {
        return new LimitesSinaisVitais(
                new FaixaVital(40, 200),
                new FaixaVital(60, 260),
                new FaixaVital(30, 150),
                new FaixaVital(50, 100),
                new FaixaVital(5, 60),
                new FaixaVital(30, 42));
    }
}
