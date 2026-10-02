package com.confirmasus.agendamento.infrastructure.web;

import com.confirmasus.agendamento.infrastructure.persistence.PacienteJpaEntity;
import com.confirmasus.agendamento.infrastructure.persistence.PacienteJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/pacientes")
public class PacienteInternalController {

  private final PacienteJpaRepository pacienteRepository;

  public PacienteInternalController(PacienteJpaRepository pacienteRepository) {
    this.pacienteRepository = pacienteRepository;
  }

  @GetMapping("/{id}")
  public ResponseEntity<PacienteInternalResponse> obterPaciente(@PathVariable Long id) {
    return pacienteRepository.findById(id)
        .map(this::convertToResponse)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  private PacienteInternalResponse convertToResponse(PacienteJpaEntity entity) {
    return new PacienteInternalResponse(
        entity.getId(),
        entity.getNome(),
        entity.getCpf(),
        entity.getTipoPaciente() != null ? entity.getTipoPaciente() : "REGULAR"
    );
  }
}
