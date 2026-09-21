package com.confirmasus.triagem.domain;

/**
 * Enum para os níveis de gravidade percebida clinicamente.
 * Pesos na fórmula do Score: LEVE=0, MODERADA=0.33, GRAVE=0.66, CRITICA=1.
 */
public enum GravidadePercebida {
  LEVE(0.0),
  MODERADA(0.33),
  GRAVE(0.66),
  CRITICA(1.0);

  private final double peso;

  GravidadePercebida(double peso) {
    this.peso = peso;
  }

  public double peso() {
    return peso;
  }
}
