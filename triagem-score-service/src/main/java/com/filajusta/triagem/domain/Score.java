package com.filajusta.triagem.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Value object para o Score calculado com seus detalhes.
 * Armazena versão do algoritmo, valor final (0-100) e detalhamento por fator.
 */
public class Score {
  private final String versao;
  private final int valor;
  private final List<FatorContribuinte> fatores;

  public Score(String versao, int valor, List<FatorContribuinte> fatores) {
    Objects.requireNonNull(versao, "Versão não pode ser nula");

    if (valor < 0 || valor > 100) {
      throw new IllegalArgumentException("Score deve estar entre 0 e 100, recebido " + valor);
    }

    Objects.requireNonNull(fatores, "Fatores não podem ser nulos");

    this.versao = versao;
    this.valor = valor;
    this.fatores = Collections.unmodifiableList(fatores);
  }

  public String versao() {
    return versao;
  }

  public int valor() {
    return valor;
  }

  public List<FatorContribuinte> fatores() {
    return fatores;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Score score = (Score) o;
    return valor == score.valor && Objects.equals(versao, score.versao) && Objects.equals(fatores, score.fatores);
  }

  @Override
  public int hashCode() {
    return Objects.hash(versao, valor, fatores);
  }

  @Override
  public String toString() {
    return "Score{v=" + versao + ", valor=" + valor + ", fatores=" + fatores.size() + '}';
  }
}
