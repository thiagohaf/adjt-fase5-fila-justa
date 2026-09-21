package com.confirmasus.triagem.application.command;

import com.confirmasus.triagem.application.port.PacienteRepositorio;
import com.confirmasus.triagem.domain.Cpf;
import com.confirmasus.triagem.domain.Paciente;
import java.util.Objects;
import java.util.UUID;

/**
 * Use case (Command) para resolver um CPF para um Paciente existente ou criar um novo.
 * Patch: corrida de duas Triagens simultâneas para o mesmo CPF novo — a perdedora
 * do INSERT agora recupera o Paciente da vencedora em vez de 500 opaco
 * (constraint UNIQUE(cpf) como rede de segurança).
 */
public class ResolverOuCriarPaciente {
  private final PacienteRepositorio pacienteRepositorio;

  public ResolverOuCriarPaciente(PacienteRepositorio pacienteRepositorio) {
    this.pacienteRepositorio = Objects.requireNonNull(pacienteRepositorio, "pacienteRepositorio não pode ser nulo");
  }

  /**
   * Resolve um CPF para um UUID de Paciente, criando implicitamente se novo.
   * Transação é gerenciada pelo chamador (RegistrarTriagem).
   */
  public UUID executar(Cpf cpf) {
    Objects.requireNonNull(cpf, "cpf não pode ser nulo");

    // Tenta encontrar um Paciente existente pelo CPF
    var pacienteExistente = pacienteRepositorio.buscarPorCpf(cpf);
    if (pacienteExistente.isPresent()) {
      return pacienteExistente.get().id();
    }

    // Cria novo Paciente
    Paciente novoPaciente = Paciente.criar(cpf);
    try {
      pacienteRepositorio.salvar(novoPaciente);
      return novoPaciente.id();
    } catch (RuntimeException e) {
      // Constraint UNIQUE(cpf) violado: outra thread ganhou a corrida
      // Tenta recuperar o Paciente da vencedora
      var pacienteVencedor = pacienteRepositorio.buscarPorCpf(cpf);
      if (pacienteVencedor.isPresent()) {
        return pacienteVencedor.get().id();
      }
      // Se ainda não encontrou, a exceção é inesperada
      throw new RuntimeException("Falha ao resolver/criar Paciente para CPF " + cpf, e);
    }
  }
}
