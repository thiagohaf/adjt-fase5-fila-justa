package com.filajusta.triagem.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio representando uma Triagem registrada.
 * Patch: elemento null em sintomas agora vira exceção, em vez de NPE.
 */
public class Triagem {
  private final UUID id;
  private final UUID pacienteId;
  private final SinaisVitais sinaisVitais;
  private final GravidadePercebida gravidade;
  private final List<String> sintomas;
  private final Score score;
  private final Instant registradoEm;

  public Triagem(
    UUID id,
    UUID pacienteId,
    SinaisVitais sinaisVitais,
    GravidadePercebida gravidade,
    List<String> sintomas,
    Score score,
    Instant registradoEm
  ) {
    Objects.requireNonNull(id, "ID não pode ser nulo");
    Objects.requireNonNull(pacienteId, "pacienteId não pode ser nulo");
    Objects.requireNonNull(sinaisVitais, "Sinais vitais não podem ser nulos");
    Objects.requireNonNull(gravidade, "Gravidade não pode ser nula");
    Objects.requireNonNull(score, "Score não pode ser nulo");
    Objects.requireNonNull(registradoEm, "registradoEm não pode ser nulo");

    if (sintomas != null) {
      for (String sintoma : sintomas) {
        if (sintoma == null) {
          throw new IllegalArgumentException("Nenhum sintoma pode ser nulo");
        }
      }
    }

    this.id = id;
    this.pacienteId = pacienteId;
    this.sinaisVitais = sinaisVitais;
    this.gravidade = gravidade;
    this.sintomas = sintomas != null ? Collections.unmodifiableList(sintomas) : Collections.emptyList();
    this.score = score;
    this.registradoEm = registradoEm;
  }

  /**
   * Factory para criar nova Triagem (gerando UUID, usando Instant.now()).
   */
  public static Triagem criar(
    UUID pacienteId,
    SinaisVitais sinaisVitais,
    GravidadePercebida gravidade,
    List<String> sintomas,
    Score score
  ) {
    return new Triagem(
      UUID.randomUUID(),
      pacienteId,
      sinaisVitais,
      gravidade,
      sintomas,
      score,
      Instant.now()
    );
  }

  public UUID id() {
    return id;
  }

  public UUID pacienteId() {
    return pacienteId;
  }

  public SinaisVitais sinaisVitais() {
    return sinaisVitais;
  }

  public GravidadePercebida gravidade() {
    return gravidade;
  }

  public List<String> sintomas() {
    return sintomas;
  }

  public Score score() {
    return score;
  }

  public Instant registradoEm() {
    return registradoEm;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Triagem triagem = (Triagem) o;
    return Objects.equals(id, triagem.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Triagem{id=" + id + ", pacienteId=" + pacienteId + ", score=" + score.valor() + '}';
  }
}
