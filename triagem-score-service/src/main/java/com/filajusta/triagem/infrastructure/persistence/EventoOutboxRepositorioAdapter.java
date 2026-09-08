package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.command.EventoOutboxRepositorio;
import com.filajusta.triagem.domain.EventoOutbox;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Adapter que implementa a porta {@link EventoOutboxRepositorio}
 * (application/command) usando {@link EventoOutboxJpaRepository} (Spring
 * Data, schema {@code triagem_score}). {@link ObjectMapper} e o Jackson 3
 * compartilhado do Spring Boot 4.1 ({@code writeValueAsString} lanca
 * {@code tools.jackson.core.JacksonException}, unchecked -- sem try/catch
 * necessario, propaga como {@code 500} via {@code TriagemExceptionHandler}).
 */
@Component
class EventoOutboxRepositorioAdapter implements EventoOutboxRepositorio {

    private final EventoOutboxJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    EventoOutboxRepositorioAdapter(EventoOutboxJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void salvar(EventoOutbox evento) {
        String payloadJson = objectMapper.writeValueAsString(evento.getPayload());

        jpaRepository.save(new EventoOutboxJpaEntity(
                evento.getEventId(), evento.getEventType(), evento.getOccurredAt(), payloadJson));
    }
}
