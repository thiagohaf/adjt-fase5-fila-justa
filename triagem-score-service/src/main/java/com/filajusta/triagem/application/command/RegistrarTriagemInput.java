package com.filajusta.triagem.application.command;

import java.util.List;

/**
 * Interface para o DTO de entrada do comando de registrar triagem.
 * Abstraída para não depender de classes de web.
 */
public interface RegistrarTriagemInput {
  String getCpf();
  Double getFrequenciaCardiaca();
  Double getPressaoArterialSistolica();
  Double getPressaoArterialDiastolica();
  Double getSaturacaoOxigenio();
  Double getFrequenciaRespiratoria();
  Double getTemperatura();
  String getGravidade();
  List<String> getSintomas();
}
