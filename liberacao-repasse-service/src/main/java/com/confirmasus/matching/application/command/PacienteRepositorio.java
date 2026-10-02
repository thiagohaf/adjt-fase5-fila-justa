package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.Cpf;
import com.confirmasus.matching.domain.Paciente;

import java.util.Optional;

/**
 * Porta de saída para busca/persistência de Paciente.
 */
public interface PacienteRepositorio {

    Optional<Paciente> buscarPorCpf(Cpf cpf);

    Paciente salvar(Paciente paciente);
}
