package com.filajusta.triagem.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Algoritmo de Score v1 ({@code [ASSUMPTION]}, Design Notes da spec 2.1,
 * confirmado com o usuario -- nao validado clinicamente, versionado para
 * evoluir): cada um dos 6 sinais vitais contribui uma subnota {@code 0..1}
 * pela distancia normalizada ao centro da faixa fisiologica (AD-11,
 * {@link FaixaVital#subnota}); {@code gravidadePercebida} contribui um peso
 * fixo por nivel ({@link GravidadePercebida#getPeso()}). Score final =
 * media das 7 subnotas x 100, arredondado. {@code sintomas} nao entra na
 * formula (nenhuma taxonomia de peso por sintoma existe nos artefatos de
 * planejamento).
 *
 * <p>Puro e deterministico (FR-4): mesmos {@link SinaisVitais}/
 * {@link GravidadePercebida}/{@link LimitesSinaisVitais} sempre produzem o
 * mesmo {@link Score}.
 */
public final class CalculadorDeScore {

    public static final String ALGORITMO_VERSAO = "v1";

    public Score calcular(SinaisVitais sinaisVitais, GravidadePercebida gravidadePercebida, LimitesSinaisVitais limites) {
        List<FatorContribuinte> fatores = new ArrayList<>(7);
        fatores.add(new FatorContribuinte("frequencia_cardiaca",
                limites.frequenciaCardiaca().subnota(sinaisVitais.getFrequenciaCardiaca())));
        fatores.add(new FatorContribuinte("pressao_arterial_sistolica",
                limites.pressaoArterialSistolica().subnota(sinaisVitais.getPressaoArterialSistolica())));
        fatores.add(new FatorContribuinte("pressao_arterial_diastolica",
                limites.pressaoArterialDiastolica().subnota(sinaisVitais.getPressaoArterialDiastolica())));
        fatores.add(new FatorContribuinte("saturacao_oxigenio",
                limites.saturacaoOxigenio().subnota(sinaisVitais.getSaturacaoOxigenio())));
        fatores.add(new FatorContribuinte("frequencia_respiratoria",
                limites.frequenciaRespiratoria().subnota(sinaisVitais.getFrequenciaRespiratoria())));
        fatores.add(new FatorContribuinte("temperatura",
                limites.temperatura().subnota(sinaisVitais.getTemperatura())));
        fatores.add(new FatorContribuinte("gravidade_percebida", gravidadePercebida.getPeso()));

        double media = fatores.stream()
                .mapToDouble(FatorContribuinte::contribuicao)
                .average()
                .orElse(0.0);
        int valor = (int) Math.round(media * 100);

        return new Score(valor, ALGORITMO_VERSAO, fatores);
    }
}
