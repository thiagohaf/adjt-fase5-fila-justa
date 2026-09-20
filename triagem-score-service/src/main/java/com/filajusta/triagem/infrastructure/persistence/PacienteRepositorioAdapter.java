package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.port.PacienteRepositorio;
import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.Paciente;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que implementa a porta PacienteRepositorio usando Spring Data JPA.
 */
@Component
public class PacienteRepositorioAdapter implements PacienteRepositorio {
  private final PacienteJpaRepository jpaRepository;

  public PacienteRepositorioAdapter(PacienteJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Optional<Paciente> buscarPorCpf(Cpf cpf) {
    return jpaRepository.findByCpf(cpf.valor())
      .map(entity -> new Paciente(entity.getId(), new Cpf(entity.getCpf())));
  }

  @Override
  public Optional<Paciente> buscarPorId(UUID id) {
    return jpaRepository.findById(id)
      .map(entity -> new Paciente(entity.getId(), new Cpf(entity.getCpf())));
  }

  @Override
  public void salvar(Paciente paciente) {
    var entity = new PacienteJpaEntity(paciente.id(), paciente.cpf().valor());
    jpaRepository.save(entity);
  }
}
