package com.filajusta.triagem.application.port;

import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.Paciente;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta para persistência de Pacientes.
 */
public interface PacienteRepositorio {
  /**
   * Busca um Paciente pelo CPF.
   */
  Optional<Paciente> buscarPorCpf(Cpf cpf);

  /**
   * Busca um Paciente pelo ID.
   */
  Optional<Paciente> buscarPorId(UUID id);

  /**
   * Persiste um novo Paciente.
   */
  void salvar(Paciente paciente);
}
