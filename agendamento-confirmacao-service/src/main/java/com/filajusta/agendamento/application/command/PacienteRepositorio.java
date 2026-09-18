package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Cpf;
import com.filajusta.agendamento.domain.Paciente;

import java.util.Optional;

/**
 * Porta de saida para busca/persistencia de {@link Paciente}. Implementada
 * em {@code infrastructure/persistence} (JPA, schema {@code agendamento_confirmacao}).
 */
public interface PacienteRepositorio {

    Optional<Paciente> buscarPorCpf(Cpf cpf);

    Paciente salvar(Paciente paciente);
}
