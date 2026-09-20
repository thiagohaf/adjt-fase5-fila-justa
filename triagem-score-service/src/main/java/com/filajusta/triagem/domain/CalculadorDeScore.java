package com.filajusta.triagem.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de domínio implementando a fórmula de Score v1 (determinístico, versionado).
 *
 * Fórmula:
 * - Cada sinal vital contribui uma subnota 0..1 pela distância normalizada à faixa.
 * - Gravidade percebida contribui um peso fixo por nível (LEVE=0, MODERADA=0.33, GRAVE=0.66, CRITICA=1).
 * - Score final = média ponderada das subnotas × 100, arredondado.
 */
public class CalculadorDeScore {
  private static final String VERSAO = "v1";

  public Score calcular(SinaisVitais sinaisVitais, GravidadePercebida gravidade) {
    List<FatorContribuinte> fatores = new ArrayList<>();
    double somaSubnotas = 0.0;

    // Calcula subnota para cada sinal vital
    for (FaixaVital sinal : sinaisVitais.getTodos()) {
      double subnota = sinal.calcularSubnota();
      fatores.add(new FatorContribuinte(sinal.nome(), subnota));
      somaSubnotas += subnota;
    }

    // Subnota de gravidade percebida
    double pesoGravidade = gravidade.peso();
    fatores.add(new FatorContribuinte("gravidade_percebida", pesoGravidade));
    somaSubnotas += pesoGravidade;

    // Média ponderada
    double media = somaSubnotas / fatores.size();

    // Normaliza para 0-100
    int valorFinal = (int) Math.round(media * 100);

    return new Score(VERSAO, valorFinal, fatores);
  }
}
