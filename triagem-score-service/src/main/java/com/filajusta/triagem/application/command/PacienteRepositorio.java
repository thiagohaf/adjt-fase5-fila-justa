package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.Paciente;

import java.util.Optional;

/**
 * Porta de saida para busca/persistencia de {@link Paciente}. Implementada
 * em {@code infrastructure/persistence} (JPA, schema {@code triagem_score}).
 */
public interface PacienteRepositorio {

    Optional<Paciente> buscarPorCpf(Cpf cpf);

    Paciente salvar(Paciente paciente);
}
