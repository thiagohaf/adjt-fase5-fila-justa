package com.filajusta.triagem.domain;

import java.util.Objects;

/**
 * As 6 faixas fisiologicas plausiveis da Triagem (AD-11), fonte unica vinda
 * de {@code filajusta.triagem.limites.*} (application.yml) -- montada na
 * raiz de composicao ({@code TriagemScoreServiceApplication}) e injetada em
 * {@link SinaisVitais} (validacao) e {@link CalculadorDeScore} (subnotas),
 * garantindo que os dois usem exatamente os mesmos limites.
 */
public record LimitesSinaisVitais(
        FaixaVital frequenciaCardiaca,
        FaixaVital pressaoArterialSistolica,
        FaixaVital pressaoArterialDiastolica,
        FaixaVital saturacaoOxigenio,
        FaixaVital frequenciaRespiratoria,
        FaixaVital temperatura) {

    public LimitesSinaisVitais {
        Objects.requireNonNull(frequenciaCardiaca, "frequenciaCardiaca");
        Objects.requireNonNull(pressaoArterialSistolica, "pressaoArterialSistolica");
        Objects.requireNonNull(pressaoArterialDiastolica, "pressaoArterialDiastolica");
        Objects.requireNonNull(saturacaoOxigenio, "saturacaoOxigenio");
        Objects.requireNonNull(frequenciaRespiratoria, "frequenciaRespiratoria");
        Objects.requireNonNull(temperatura, "temperatura");
    }
}
