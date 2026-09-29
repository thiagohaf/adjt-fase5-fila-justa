package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.command.PacienteRepositorio;
import com.confirmasus.matching.domain.Cpf;
import com.confirmasus.matching.domain.Paciente;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adaptador JPA para PacienteRepositorio (porta de saída).
 */
@Repository
public class PacienteRepositorioAdapter implements PacienteRepositorio {

    private final PacienteJpaRepository jpaRepository;

    public PacienteRepositorioAdapter(PacienteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Paciente> buscarPorCpf(Cpf cpf) {
        return jpaRepository.findByCpf(cpf.getNumero())
                .map(entity -> new Paciente(entity.getPacienteId(), cpf));
    }

    @Override
    public Paciente salvar(Paciente paciente) {
        PacienteJpaEntity entity = new PacienteJpaEntity(paciente.getCpf().getNumero());
        PacienteJpaEntity salva = jpaRepository.save(entity);
        return new Paciente(salva.getPacienteId(), paciente.getCpf());
    }
}
