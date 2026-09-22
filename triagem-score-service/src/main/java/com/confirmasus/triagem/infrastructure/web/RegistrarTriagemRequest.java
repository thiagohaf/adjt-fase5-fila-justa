package com.confirmasus.triagem.infrastructure.web;

import com.confirmasus.triagem.application.command.RegistrarTriagemInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO de requisição para registrar uma Triagem (com validação @NotNull/@NotBlank).
 * Implementa a interface RegistrarTriagemInput para uso no use case.
 */
public class RegistrarTriagemRequest implements RegistrarTriagemInput {
  @NotBlank(message = "CPF é obrigatório")
  private String cpf;

  @NotNull(message = "Frequência cardíaca é obrigatória")
  private Double frequenciaCardiaca;

  @NotNull(message = "Pressão arterial sistólica é obrigatória")
  private Double pressaoArterialSistolica;

  @NotNull(message = "Pressão arterial diastólica é obrigatória")
  private Double pressaoArterialDiastolica;

  @NotNull(message = "Saturação de oxigênio é obrigatória")
  private Double saturacaoOxigenio;

  @NotNull(message = "Frequência respiratória é obrigatória")
  private Double frequenciaRespiratoria;

  @NotNull(message = "Temperatura é obrigatória")
  private Double temperatura;

  @NotBlank(message = "Gravidade é obrigatória")
  private String gravidade;

  private List<String> sintomas;

  public RegistrarTriagemRequest() {}

  public RegistrarTriagemRequest(
    String cpf,
    Double frequenciaCardiaca,
    Double pressaoArterialSistolica,
    Double pressaoArterialDiastolica,
    Double saturacaoOxigenio,
    Double frequenciaRespiratoria,
    Double temperatura,
    String gravidade,
    List<String> sintomas
  ) {
    this.cpf = cpf;
    this.frequenciaCardiaca = frequenciaCardiaca;
    this.pressaoArterialSistolica = pressaoArterialSistolica;
    this.pressaoArterialDiastolica = pressaoArterialDiastolica;
    this.saturacaoOxigenio = saturacaoOxigenio;
    this.frequenciaRespiratoria = frequenciaRespiratoria;
    this.temperatura = temperatura;
    this.gravidade = gravidade;
    this.sintomas = sintomas;
  }

  public String getCpf() { return cpf; }
  public void setCpf(String cpf) { this.cpf = cpf; }
  public Double getFrequenciaCardiaca() { return frequenciaCardiaca; }
  public void setFrequenciaCardiaca(Double frequenciaCardiaca) { this.frequenciaCardiaca = frequenciaCardiaca; }
  public Double getPressaoArterialSistolica() { return pressaoArterialSistolica; }
  public void setPressaoArterialSistolica(Double pressaoArterialSistolica) { this.pressaoArterialSistolica = pressaoArterialSistolica; }
  public Double getPressaoArterialDiastolica() { return pressaoArterialDiastolica; }
  public void setPressaoArterialDiastolica(Double pressaoArterialDiastolica) { this.pressaoArterialDiastolica = pressaoArterialDiastolica; }
  public Double getSaturacaoOxigenio() { return saturacaoOxigenio; }
  public void setSaturacaoOxigenio(Double saturacaoOxigenio) { this.saturacaoOxigenio = saturacaoOxigenio; }
  public Double getFrequenciaRespiratoria() { return frequenciaRespiratoria; }
  public void setFrequenciaRespiratoria(Double frequenciaRespiratoria) { this.frequenciaRespiratoria = frequenciaRespiratoria; }
  public Double getTemperatura() { return temperatura; }
  public void setTemperatura(Double temperatura) { this.temperatura = temperatura; }
  public String getGravidade() { return gravidade; }
  public void setGravidade(String gravidade) { this.gravidade = gravidade; }
  public List<String> getSintomas() { return sintomas; }
  public void setSintomas(List<String> sintomas) { this.sintomas = sintomas; }
}
