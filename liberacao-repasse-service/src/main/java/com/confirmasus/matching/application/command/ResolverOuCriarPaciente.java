package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.Cpf;
import com.confirmasus.matching.domain.Paciente;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Resolve o Paciente de um CPF de forma idempotente: busca antes de inserir,
 * sempre dentro da mesma transação do comando chamador. Trata race condition
 * com constraint UNIQUE(cpf).
 */
public class ResolverOuCriarPaciente {

    private final PacienteRepositorio pacienteRepositorio;

    public ResolverOuCriarPaciente(PacienteRepositorio pacienteRepositorio) {
        this.pacienteRepositorio = pacienteRepositorio;
    }

    public Paciente resolver(Cpf cpf) {
        return pacienteRepositorio.buscarPorCpf(cpf)
                .orElseGet(() -> criarOuReconsultarAposCorrida(cpf));
    }

    private Paciente criarOuReconsultarAposCorrida(Cpf cpf) {
        try {
            return pacienteRepositorio.salvar(new Paciente(null, cpf));
        } catch (DataIntegrityViolationException e) {
            return pacienteRepositorio.buscarPorCpf(cpf).orElseThrow(() -> e);
        }
    }
}
