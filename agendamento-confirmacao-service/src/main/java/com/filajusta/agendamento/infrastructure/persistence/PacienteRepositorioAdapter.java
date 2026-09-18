package com.filajusta.agendamento.infrastructure.persistence;

import com.filajusta.agendamento.application.command.PacienteRepositorio;
import com.filajusta.agendamento.domain.Cpf;
import com.filajusta.agendamento.domain.Paciente;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter que implementa a porta {@link PacienteRepositorio}
 * (application/command) usando {@link PacienteJpaRepository} (Spring Data,
 * schema {@code agendamento_confirmacao}).
 */
@Component
class PacienteRepositorioAdapter implements PacienteRepositorio {

    private final PacienteJpaRepository jpaRepository;

    PacienteRepositorioAdapter(PacienteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Paciente> buscarPorCpf(Cpf cpf) {
        return jpaRepository.findByCpf(cpf.getNumero())
                .map(entity -> new Paciente(entity.getId(), cpf));
    }

    @Override
    public Paciente salvar(Paciente paciente) {
        PacienteJpaEntity entity = new PacienteJpaEntity(paciente.getCpf().getNumero());
        PacienteJpaEntity salvo = jpaRepository.save(entity);
        return new Paciente(salvo.getId(), paciente.getCpf());
    }
}
