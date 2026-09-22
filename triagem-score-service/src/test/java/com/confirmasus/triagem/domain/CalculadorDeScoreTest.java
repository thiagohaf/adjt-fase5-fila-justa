package com.confirmasus.triagem.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadorDeScoreTest {
  private final CalculadorDeScore calculador = new CalculadorDeScore();

  @Test
  void scoreHappyPath() {
    SinaisVitais sinais = new SinaisVitais(80.0, 120.0, 80.0, 95.0, 16.0, 36.5);
    GravidadePercebida gravidade = GravidadePercebida.LEVE;

    Score score = calculador.calcular(sinais, gravidade);

    assertEquals("v1", score.versao());
    assertEquals(7, score.fatores().size());
    assertTrue(score.valor() >= 0 && score.valor() <= 100);
  }

  @Test
  void scoreComGravidadeCritica() {
    SinaisVitais sinais = new SinaisVitais(180.0, 180.0, 110.0, 98.0, 30.0, 38.0);
    GravidadePercebida gravidade = GravidadePercebida.CRITICA;

    Score score = calculador.calcular(sinais, gravidade);

    assertTrue(score.valor() > 50, "Score com gravidade crítica deve ser alto");
  }

  @Test
  void mesmoInputsGeramMesmoScore() {
    SinaisVitais sinais = new SinaisVitais(80.0, 120.0, 80.0, 95.0, 16.0, 36.5);
    GravidadePercebida gravidade = GravidadePercebida.MODERADA;

    Score score1 = calculador.calcular(sinais, gravidade);
    Score score2 = calculador.calcular(sinais, gravidade);

    assertEquals(score1.valor(), score2.valor());
    assertEquals(score1.versao(), score2.versao());
  }
}
