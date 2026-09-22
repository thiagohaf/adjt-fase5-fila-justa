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

  public PacienteJpaEntity() {
  }

  public PacienteJpaEntity(UUID id, String cpf) {
    this.id = id;
    this.cpf = cpf;
    this.criadoEm = Instant.now();
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
}
