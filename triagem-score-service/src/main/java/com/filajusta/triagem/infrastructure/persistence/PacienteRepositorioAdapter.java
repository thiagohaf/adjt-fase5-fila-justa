package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.command.PacienteRepositorio;
import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.Paciente;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter que implementa a porta {@link PacienteRepositorio}
 * (application/command) usando {@link PacienteJpaRepository} (Spring Data,
 * schema {@code triagem_score}).
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
