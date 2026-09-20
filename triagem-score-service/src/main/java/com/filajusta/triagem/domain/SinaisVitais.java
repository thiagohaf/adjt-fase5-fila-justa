package com.filajusta.triagem.domain;

import java.util.Objects;

/**
 * Value object agregando todos os sinais vitais obrigatórios com suas faixas.
 * Faixas fisiológicas de AD-11: FC 40–200 bpm, PAS 60–260 mmHg, PAD 30–150 mmHg
 * (com PAS > PAD), SpO2 50–100%, FR 5–60 irpm, Temp 30–42°C.
 */
public class SinaisVitais {
  private final FaixaVital frequenciaCardiaca;
  private final FaixaVital pressaoArterialSistolica;
  private final FaixaVital pressaoArterialDiastolica;
  private final FaixaVital saturacaoOxigenio;
  private final FaixaVital frequenciaRespiratoria;
  private final FaixaVital temperatura;

  public SinaisVitais(
    Double frequenciaCardiaca,
    Double pressaoArterialSistolica,
    Double pressaoArterialDiastolica,
    Double saturacaoOxigenio,
    Double frequenciaRespiratoria,
    Double temperatura
  ) {
    this.frequenciaCardiaca = new FaixaVital("frequencia_cardiaca", frequenciaCardiaca, 40, 200);
    this.pressaoArterialSistolica = new FaixaVital("pressao_arterial_sistolica", pressaoArterialSistolica, 60, 260);
    this.pressaoArterialDiastolica = new FaixaVital("pressao_arterial_diastolica", pressaoArterialDiastolica, 30, 150);
    this.saturacaoOxigenio = new FaixaVital("saturacao_oxigenio", saturacaoOxigenio, 50, 100);
    this.frequenciaRespiratoria = new FaixaVital("frequencia_respiratoria", frequenciaRespiratoria, 5, 60);
    this.temperatura = new FaixaVital("temperatura", temperatura, 30, 42);

    validarPAsmaior();
  }

  private void validarPAsmaior() {
    if (this.pressaoArterialSistolica.valor() <= this.pressaoArterialDiastolica.valor()) {
      throw new IllegalArgumentException("PAS deve ser maior que PAD");
    }
  }

  public FaixaVital getFrequenciaCardiaca() {
    return frequenciaCardiaca;
  }

  public FaixaVital getPressaoArterialSistolica() {
    return pressaoArterialSistolica;
  }

  public FaixaVital getPressaoArterialDiastolica() {
    return pressaoArterialDiastolica;
  }

  public FaixaVital getSaturacaoOxigenio() {
    return saturacaoOxigenio;
  }

  public FaixaVital getFrequenciaRespiratoria() {
    return frequenciaRespiratoria;
  }

  public FaixaVital getTemperatura() {
    return temperatura;
  }

  /**
   * Retorna array de todos os sinais vitais para processamento genérico.
   */
  public FaixaVital[] getTodos() {
    return new FaixaVital[] {
      frequenciaCardiaca,
      pressaoArterialSistolica,
      pressaoArterialDiastolica,
      saturacaoOxigenio,
      frequenciaRespiratoria,
      temperatura
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SinaisVitais that = (SinaisVitais) o;
    return Objects.equals(frequenciaCardiaca, that.frequenciaCardiaca) &&
           Objects.equals(pressaoArterialSistolica, that.pressaoArterialSistolica) &&
           Objects.equals(pressaoArterialDiastolica, that.pressaoArterialDiastolica) &&
           Objects.equals(saturacaoOxigenio, that.saturacaoOxigenio) &&
           Objects.equals(frequenciaRespiratoria, that.frequenciaRespiratoria) &&
           Objects.equals(temperatura, that.temperatura);
  }

  @Override
  public int hashCode() {
    return Objects.hash(frequenciaCardiaca, pressaoArterialSistolica, pressaoArterialDiastolica,
                        saturacaoOxigenio, frequenciaRespiratoria, temperatura);
  }
}
