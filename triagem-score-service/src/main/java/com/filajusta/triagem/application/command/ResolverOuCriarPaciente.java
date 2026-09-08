package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.Paciente;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Resolve o {@link Paciente} de um CPF de forma idempotente (FR-2):
 * {@code SELECT} antes de {@code INSERT}, sempre dentro da mesma transacao
 * de {@link RegistrarTriagem} -- evita que duas Triagens simultaneas para o
 * mesmo CPF criem dois Pacientes (constraint {@code UNIQUE(cpf)} como rede
 * de seguranca, Design Notes da spec 2.1).
 *
 * <p>Sob corrida real (dois requests concorrentes para o mesmo CPF novo
 * passam ambos pelo {@code SELECT} antes de qualquer {@code INSERT}), o
 * perdedor do {@code INSERT} recebe {@link DataIntegrityViolationException}
 * da constraint {@code UNIQUE(cpf)} -- tratado aqui reconsultando o
 * Paciente ja persistido pelo vencedor, para que a resposta ainda seja
 * {@code 201} reutilizando o mesmo {@code pacienteId} (Boundaries da spec
 * 2.1: "CPF ja visto sempre reutiliza o mesmo Paciente").
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
