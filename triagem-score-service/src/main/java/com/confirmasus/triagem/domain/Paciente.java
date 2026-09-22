package com.confirmasus.triagem.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio representando um Paciente.
 * Criado implicitamente quando um CPF novo é visto (idempotência via CPF).
 * ID interno é UUID v4; CPF nunca propaga além deste serviço.
 */
public class Paciente {
  private final UUID id;
  private final Cpf cpf;

  public Paciente(UUID id, Cpf cpf) {
    Objects.requireNonNull(id, "ID não pode ser nulo");
    Objects.requireNonNull(cpf, "CPF não pode ser nulo");

    this.id = id;
    this.cpf = cpf;
  }

  /**
   * Factory para criar novo Paciente (gerando UUID).
   */
  public static Paciente criar(Cpf cpf) {
    return new Paciente(UUID.randomUUID(), cpf);
  }

  public UUID id() {
    return id;
  }

  public Cpf cpf() {
    return cpf;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Paciente paciente = (Paciente) o;
    return Objects.equals(id, paciente.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Paciente{id=" + id + '}';
  }
}
