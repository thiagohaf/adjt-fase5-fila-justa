package com.filajusta.triagem.domain;

import java.util.Objects;

/**
 * Value object representando a contribuição de um fator ao Score final.
 * Cada fator tem um nome e uma contribuição (0..1).
 */
public class FatorContribuinte {
  private final String fator;
  private final double contribuicao;

  public FatorContribuinte(String fator, double contribuicao) {
    Objects.requireNonNull(fator, "Fator não pode ser nulo");

    if (contribuicao < 0 || contribuicao > 1) {
      throw new IllegalArgumentException("Contribuição deve estar entre 0 e 1, recebido " + contribuicao);
    }

    this.fator = fator;
    this.contribuicao = contribuicao;
  }

  public String fator() {
    return fator;
  }

  public double contribuicao() {
    return contribuicao;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FatorContribuinte that = (FatorContribuinte) o;
    return Double.compare(that.contribuicao, contribuicao) == 0 && Objects.equals(fator, that.fator);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fator, contribuicao);
  }

  @Override
  public String toString() {
    return "FatorContribuinte{" + fator + "=" + contribuicao + '}';
  }
}
