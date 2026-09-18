package com.filajusta.agendamento.infrastructure.persistence;

import com.filajusta.agendamento.application.command.AgendamentoRepositorio;
import com.filajusta.agendamento.domain.Agendamento;
import org.springframework.stereotype.Component;

/**
 * Adapter que implementa a porta {@link AgendamentoRepositorio}
 * (application/command) usando {@link AgendamentoJpaRepository} (Spring
 * Data, schema {@code agendamento_confirmacao}).
 */
@Component
class AgendamentoRepositorioAdapter implements AgendamentoRepositorio {

    private final AgendamentoJpaRepository jpaRepository;

    AgendamentoRepositorioAdapter(AgendamentoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Agendamento salvar(Agendamento agendamento) {
        AgendamentoJpaEntity entity = new AgendamentoJpaEntity(
                agendamento.getPacienteId(),
                agendamento.getRecursoId(),
                agendamento.getDataHoraAgendamento(),
                agendamento.getStatus(),
                agendamento.getCriadoEm());
        AgendamentoJpaEntity salvo = jpaRepository.save(entity);
        return new Agendamento(
                salvo.getId(),
                salvo.getPacienteId(),
                salvo.getRecursoId(),
                salvo.getDataHoraAgendamento(),
                salvo.getStatus(),
                salvo.getCriadoEm());
    }
}
