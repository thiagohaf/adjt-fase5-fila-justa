package com.filajusta.triagem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "triagens", schema = "triagem_score")
public class TriagemJpaEntity {
  @Id
  private UUID id;

  @Column(name = "paciente_id", nullable = false)
  private UUID pacienteId;

  @Column(nullable = false, length = 20)
  private String gravidade;

  @Column(name = "sinais_vitais", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private String sinaisVitais; // JSON como string

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private String sintomas; // JSON array como string

  @Column(name = "score_versao", nullable = false, length = 10)
  private String scoreVersao;

  @Column(name = "score_valor", nullable = false)
  private Integer scoreValor;

  @Column(name = "score_fatores", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private String scoreFactores; // JSON array como string

  @Column(name = "registrado_em", nullable = false)
  private Instant registradoEm;

  public TriagemJpaEntity() {
  }

  public TriagemJpaEntity(UUID id, UUID pacienteId, String gravidade, String sinaisVitais,
                         String sintomas, String scoreVersao, Integer scoreValor,
                         String scoreFactores, Instant registradoEm) {
    this.id = id;
    this.pacienteId = pacienteId;
    this.gravidade = gravidade;
    this.sinaisVitais = sinaisVitais;
    this.sintomas = sintomas;
    this.scoreVersao = scoreVersao;
    this.scoreValor = scoreValor;
    this.scoreFactores = scoreFactores;
    this.registradoEm = registradoEm;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getPacienteId() {
    return pacienteId;
  }

  public void setPacienteId(UUID pacienteId) {
    this.pacienteId = pacienteId;
  }

  public String getGravidade() {
    return gravidade;
  }

  public void setGravidade(String gravidade) {
    this.gravidade = gravidade;
  }

  public String getSinaisVitais() {
    return sinaisVitais;
  }

  public void setSinaisVitais(String sinaisVitais) {
    this.sinaisVitais = sinaisVitais;
  }

  public String getSintomas() {
    return sintomas;
  }

  public void setSintomas(String sintomas) {
    this.sintomas = sintomas;
  }

  public String getScoreVersao() {
    return scoreVersao;
  }

  public void setScoreVersao(String scoreVersao) {
    this.scoreVersao = scoreVersao;
  }

  public Integer getScoreValor() {
    return scoreValor;
  }

  public void setScoreValor(Integer scoreValor) {
    this.scoreValor = scoreValor;
  }

  public String getScoreFactores() {
    return scoreFactores;
  }

  public void setScoreFactores(String scoreFactores) {
    this.scoreFactores = scoreFactores;
  }

  public Instant getRegistradoEm() {
    return registradoEm;
  }

  public void setRegistradoEm(Instant registradoEm) {
    this.registradoEm = registradoEm;
  }
}
