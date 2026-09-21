package com.confirmasus.auditoria.infrastructure.persistence;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link DecisaoAuditoriaRepositorio} (application)
 * usando {@link DecisaoAuditoriaJpaRepository} (Spring Data, schema {@code auditoria}).
 *
 * <p>Segue o padrão Clean Architecture: domínio ↔ porta ↔ adapter ↔ JPA.
 *
 * <p>{@link ObjectMapper} é o Jackson 3 compartilhado do Spring Boot 4.1.
 * {@link Clock} é injetado para testabilidade.
 */
@Component
class DecisaoAuditoriaRepositorioAdapter implements DecisaoAuditoriaRepositorio {

    private final DecisaoAuditoriaJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DecisaoAuditoriaRepositorioAdapter(DecisaoAuditoriaJpaRepository jpaRepository,
                                       ObjectMapper objectMapper,
                                       Clock clock) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void salvar(DecisaoAuditoria decisao) {
        // Converte domínio → JPA entity
        // BUG FIX #8: payloadBruto agora preenchido pelo domínio
        DecisaoAuditoriaJpaEntity entity = new DecisaoAuditoriaJpaEntity(
                decisao.getEventId(),
                decisao.getAgendamentoId(),
                decisao.getPacienteId(),
                decisao.getTipoDecisao(),
                decisao.getMotivo(),
                decisao.getTimestamp(),
                decisao.getCriadoEm(),
                decisao.getPayloadBruto()
        );
        // DataIntegrityViolationException é capturada no consumer job
        jpaRepository.save(entity);
    }

    @Override
    public DecisaoAuditoria buscarPorEventId(UUID eventId) {
        return jpaRepository.findByEventId(eventId)
                .map(this::paraDominio)
                .orElse(null);
    }

    @Override
    public List<DecisaoAuditoria> findByPacienteIdOrderByTimestamp(Long pacienteId) {
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByPacienteIdOrderByTimestamp(pacienteId);
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::paraDominio)
                .toList();
    }

    @Override
    public List<DecisaoAuditoria> findByAgendamentoIdOrderByTimestamp(Long agendamentoId) {
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByAgendamentoIdOrderByTimestamp(agendamentoId);
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::paraDominio)
                .toList();
    }

    private DecisaoAuditoria paraDominio(DecisaoAuditoriaJpaEntity entity) {
        return new DecisaoAuditoria(
                entity.getId(),
                entity.getEventId(),
                entity.getAgendamentoId(),
                entity.getPacienteId(),
                entity.getTipoDecisao(),
                entity.getMotivo(),
                entity.getTimestamp(),
                entity.getCriadoEm(),
                entity.getPayloadBruto()
        );
    }
}
