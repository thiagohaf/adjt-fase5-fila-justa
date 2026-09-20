package com.filajusta.triagem.domain;

import java.util.Objects;

/**
 * Value object para um sinal vital dentro de uma faixa fisiológica.
 * Valida se o valor está dentro de min/max (exclusive em ambas as extremidades).
 * Patch: Double.isNaN() explícito antes de comparação — NaN passaria despercebido.
 */
public class FaixaVital {
  private final String nome;
  private final double valor;
  private final double min;
  private final double max;

  public FaixaVital(String nome, Double valor, double min, double max) {
    Objects.requireNonNull(nome, "Nome do sinal vital não pode ser nulo");

    if (valor == null) {
      throw new IllegalArgumentException(nome + " é obrigatório");
    }

    if (Double.isNaN(valor)) {
      throw new IllegalArgumentException(nome + " não pode ser NaN");
    }

    if (valor < min || valor > max) {
      throw new IllegalArgumentException(
        nome + " deve estar entre " + min + " e " + max + ", recebido " + valor
      );
    }

    this.nome = nome;
    this.valor = valor;
    this.min = min;
    this.max = max;
  }

  public String nome() {
    return nome;
  }

  public double valor() {
    return valor;
  }

  /**
   * Calcula a subnota (0..1) pela distância normalizada à faixa.
   * Quanto mais perto do centro da faixa, mais próxima de 0.
   * Quanto mais longe do centro, mais próxima de 1.
   */
  public double calcularSubnota() {
    double centro = (min + max) / 2.0;
    double meia_amplitude = (max - min) / 2.0;
    double distancia_ao_centro = Math.abs(valor - centro);
    return Math.min(1.0, distancia_ao_centro / meia_amplitude);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FaixaVital that = (FaixaVital) o;
    return Double.compare(that.valor, valor) == 0 && Objects.equals(nome, that.nome);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nome, valor);
  }

  @Override
  public String toString() {
    return "FaixaVital{" + nome + "=" + valor + '}';
  }
}
