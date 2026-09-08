package com.filajusta.triagem.domain;

import java.util.Objects;

/**
 * Sinais vitais de uma Triagem -- value object que valida todas as
 * invariantes de AD-11 no construtor: presenca obrigatoria de cada campo,
 * faixa fisiologica plausivel, e PAS &gt; PAD. Lanca
 * {@link SinalVitalInvalidoException} no primeiro campo invalido encontrado
 * (Boundaries da spec 2.1), nomeando o campo, antes de qualquer calculo de
 * Score.
 *
 * <p>Ordem de verificacao (deterministica): frequencia cardiaca, pressao
 * sistolica, pressao diastolica (+ comparacao PAS &gt; PAD), saturacao de
 * oxigenio, frequencia respiratoria, temperatura.
 */
public final class SinaisVitais {

    private final double frequenciaCardiaca;
    private final double pressaoArterialSistolica;
    private final double pressaoArterialDiastolica;
    private final double saturacaoOxigenio;
    private final double frequenciaRespiratoria;
    private final double temperatura;

    public SinaisVitais(Double frequenciaCardiaca,
                         Double pressaoArterialSistolica,
                         Double pressaoArterialDiastolica,
                         Double saturacaoOxigenio,
                         Double frequenciaRespiratoria,
                         Double temperatura,
                         LimitesSinaisVitais limites) {
        Objects.requireNonNull(limites, "limites");

        exigirPresente("frequenciaCardiaca", frequenciaCardiaca);
        limites.frequenciaCardiaca().exigirDentroDaFaixa("frequenciaCardiaca", frequenciaCardiaca);

        exigirPresente("pressaoArterialSistolica", pressaoArterialSistolica);
        limites.pressaoArterialSistolica().exigirDentroDaFaixa("pressaoArterialSistolica", pressaoArterialSistolica);

        exigirPresente("pressaoArterialDiastolica", pressaoArterialDiastolica);
        limites.pressaoArterialDiastolica().exigirDentroDaFaixa("pressaoArterialDiastolica", pressaoArterialDiastolica);

        if (pressaoArterialSistolica <= pressaoArterialDiastolica) {
            throw new SinalVitalInvalidoException("pressaoArterialSistolica",
                    "pressaoArterialSistolica deve ser maior que pressaoArterialDiastolica");
        }

        exigirPresente("saturacaoOxigenio", saturacaoOxigenio);
        limites.saturacaoOxigenio().exigirDentroDaFaixa("saturacaoOxigenio", saturacaoOxigenio);

        exigirPresente("frequenciaRespiratoria", frequenciaRespiratoria);
        limites.frequenciaRespiratoria().exigirDentroDaFaixa("frequenciaRespiratoria", frequenciaRespiratoria);

        exigirPresente("temperatura", temperatura);
        limites.temperatura().exigirDentroDaFaixa("temperatura", temperatura);

        this.frequenciaCardiaca = frequenciaCardiaca;
        this.pressaoArterialSistolica = pressaoArterialSistolica;
        this.pressaoArterialDiastolica = pressaoArterialDiastolica;
        this.saturacaoOxigenio = saturacaoOxigenio;
        this.frequenciaRespiratoria = frequenciaRespiratoria;
        this.temperatura = temperatura;
    }

    private static void exigirPresente(String campo, Double valor) {
        if (valor == null) {
            throw new SinalVitalInvalidoException(campo, campo + " e obrigatorio");
        }
    }

    public double getFrequenciaCardiaca() {
        return frequenciaCardiaca;
    }

    public double getPressaoArterialSistolica() {
        return pressaoArterialSistolica;
    }

    public double getPressaoArterialDiastolica() {
        return pressaoArterialDiastolica;
    }

    public double getSaturacaoOxigenio() {
        return saturacaoOxigenio;
    }

    public double getFrequenciaRespiratoria() {
        return frequenciaRespiratoria;
    }

    public double getTemperatura() {
        return temperatura;
    }
}
