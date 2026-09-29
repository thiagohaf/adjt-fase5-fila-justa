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

  /**
   * Deriva um long positivo estável a partir de um UUID de Paciente -- este
   * serviço identifica Paciente por UUID internamente (CPF nunca propaga
   * além dele), mas os consumidores externos do Score (ScoreCalculado via
   * SQS e {@code GET /internal/scores}, matching-alocacao-service) esperam
   * um {@code pacienteId} numérico. Não há hoje nenhum identificador
   * compartilhado entre os bounded contexts para a mesma pessoa
   * (agendamento-confirmacao-service e matching-alocacao-service também
   * resolvem Paciente por CPF de forma independente, com sequências
   * próprias) -- este valor só precisa ser estável para o mesmo UUID entre
   * chamadas, usado igualmente pelo publisher do evento ({@code
   * RegistrarTriagem}) e pelo bootstrap síncrono ({@code
   * ScoreInternalController}) para que as duas vias nunca divirjam para o
   * mesmo paciente. Mascara o bit de sinal (nunca negativo) e nunca
   * retorna zero (ScoreReplica rejeita pacienteId &lt;= 0).
   */
  public static long idNumerico(UUID id) {
    long valor = id.getMostSignificantBits() & Long.MAX_VALUE;
    return valor == 0 ? 1 : valor;
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
