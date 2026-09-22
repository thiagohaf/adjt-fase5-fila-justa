package com.confirmasus.triagem.application.command;

import com.confirmasus.triagem.domain.FatorContribuinte;
import com.confirmasus.triagem.domain.Score;
import com.confirmasus.triagem.domain.Triagem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DTO de resposta para Triagem registrada.
 * Nunca retorna CPF (apenas pacienteId).
 */
public class TriagemRegistradaResponse {
  private final UUID triagemId;
  private final UUID pacienteId;
  private final int score;
  private final String scoreVersao;
  private final String gravidade;
  private final List<FatorDTO> fatores;
  private final List<String> sintomas;
  private final Instant registradoEm;

  public TriagemRegistradaResponse(Triagem triagem, Score score) {
    this.triagemId = triagem.id();
    this.pacienteId = triagem.pacienteId();
    this.score = score.valor();
    this.scoreVersao = score.versao();
    this.gravidade = triagem.gravidade().toString();
    this.fatores = score.fatores().stream()
      .map(f -> new FatorDTO(f.fator(), f.contribuicao()))
      .collect(Collectors.toList());
    this.sintomas = triagem.sintomas();
    this.registradoEm = triagem.registradoEm();
  }

  public UUID getTriagemId() {
    return triagemId;
  }

  public UUID getPacienteId() {
    return pacienteId;
  }

  public int getScore() {
    return score;
  }

  public String getScoreVersao() {
    return scoreVersao;
  }

  public String getGravidade() {
    return gravidade;
  }

  public List<FatorDTO> getFatores() {
    return fatores;
  }

  public List<String> getSintomas() {
    return sintomas;
  }

  public Instant getRegistradoEm() {
    return registradoEm;
  }

  public static class FatorDTO {
    private final String fator;
    private final double contribuicao;

    public FatorDTO(String fator, double contribuicao) {
      this.fator = fator;
      this.contribuicao = contribuicao;
    }

    public String getFator() {
      return fator;
    }

    public double getContribuicao() {
      return contribuicao;
    }
  }
}
