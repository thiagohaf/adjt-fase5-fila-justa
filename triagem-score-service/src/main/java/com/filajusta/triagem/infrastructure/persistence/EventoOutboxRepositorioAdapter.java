package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.command.EventoOutboxRepositorio;
import com.filajusta.triagem.domain.EventoOutbox;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Adapter que implementa a porta {@link EventoOutboxRepositorio}
 * (application/command) usando {@link EventoOutboxJpaRepository} (Spring
 * Data, schema {@code triagem_score}). {@link ObjectMapper} e o Jackson 3
 * compartilhado do Spring Boot 4.1 ({@code writeValueAsString}/{@code
 * readValue} lancam {@code tools.jackson.core.JacksonException}, unchecked
 * -- sem try/catch necessario, propaga como {@code 500} via
 * {@code TriagemExceptionHandler} no caminho de escrita; no caminho de
 * leitura do relay, {@code RelaySnsPublisherJob} ja captura qualquer
 * {@code RuntimeException} por linha, ver Boundaries da spec 3.0).
 *
 * <p>{@link Clock} injetado (mesmo bean da raiz de composicao) em vez de
 * {@code Instant.now()} direto -- consistente com o resto do servico e
 * testavel em {@code marcarComoPublicado}.
 */
@Component
class EventoOutboxRepositorioAdapter implements EventoOutboxRepositorio {

    private final EventoOutboxJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EventoOutboxRepositorioAdapter(EventoOutboxJpaRepository jpaRepository, ObjectMapper objectMapper, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void salvar(EventoOutbox evento) {
        String payloadJson = objectMapper.writeValueAsString(evento.getPayload());

        jpaRepository.save(new EventoOutboxJpaEntity(
                evento.getEventId(), evento.getEventType(), evento.getOccurredAt(),
                evento.getVersion(), evento.getCorrelationId(), payloadJson));
    }

    @Override
    public List<EventoOutbox> buscarNaoPublicados(int limite) {
        // FOR UPDATE SKIP LOCKED (achado do code review, ver
        // EventoOutboxJpaRepository) -- so protege de fato contra corrida
        // entre instancias do job quando chamado dentro da mesma transacao
        // que tambem publica e marca (RelaySnsPublisherJob.publicarPendentes,
        // @Transactional); chamado fora de uma transacao ja aberta, o lock
        // e liberado assim que este metodo retorna.
        return jpaRepository.buscarPendentesParaAtualizar(limite)
                .stream()
                .map(this::paraDominio)
                .toList();
    }

    @Override
    @Transactional
    public boolean marcarComoPublicado(long id) {
        return jpaRepository.marcarPublicado(id, clock.instant()) > 0;
    }

    @SuppressWarnings("unchecked")
    private EventoOutbox paraDominio(EventoOutboxJpaEntity entity) {
        Map<String, Object> payload = objectMapper.readValue(entity.getPayload(), Map.class);
        return new EventoOutbox(entity.getId(), entity.getEventId(), entity.getEventType(),
                entity.getOccurredAt(), entity.getVersion(), entity.getCorrelationId(), payload);
    }
}
