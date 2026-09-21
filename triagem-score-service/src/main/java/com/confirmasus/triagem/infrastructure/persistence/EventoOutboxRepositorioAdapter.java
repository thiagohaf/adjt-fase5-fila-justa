package com.confirmasus.triagem.infrastructure.persistence;

import com.confirmasus.triagem.application.port.EventoOutboxRepositorio;
import com.confirmasus.triagem.domain.EventoOutbox;
import org.springframework.stereotype.Component;

/**
 * Adapter que implementa a porta EventoOutboxRepositorio usando Spring Data JPA.
 */
@Component
public class EventoOutboxRepositorioAdapter implements EventoOutboxRepositorio {
  private final EventoOutboxJpaRepository jpaRepository;

  public EventoOutboxRepositorioAdapter(EventoOutboxJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void salvar(EventoOutbox evento) {
    var entity = new EventoOutboxJpaEntity(
      evento.eventId(),
      evento.eventType(),
      evento.occurredAt(),
      evento.payload()
    );
    jpaRepository.save(entity);
  }
}
