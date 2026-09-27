package com.confirmasus.triagem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pacientes", schema = "triagem_score")
public class PacienteJpaEntity {
  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 11)
  private String cpf;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(length = 255)
  private String nome;

  @Column(name = "tipo_paciente", length = 50)
  private String tipoPaciente;

  public PacienteJpaEntity() {
  }

  public PacienteJpaEntity(UUID id, String cpf) {
    this.id = id;
    this.cpf = cpf;
    this.criadoEm = Instant.now();
    this.tipoPaciente = "REGULAR";
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCpf() {
    return cpf;
  }

  public void setCpf(String cpf) {
    this.cpf = cpf;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public void setCriadoEm(Instant criadoEm) {
    this.criadoEm = criadoEm;
  }

  public String getNome() {
    return nome;
  }

  public void setNome(String nome) {
    this.nome = nome;
  }

  public String getTipoPaciente() {
    return tipoPaciente;
  }

  public void setTipoPaciente(String tipoPaciente) {
    this.tipoPaciente = tipoPaciente;
  }
}
