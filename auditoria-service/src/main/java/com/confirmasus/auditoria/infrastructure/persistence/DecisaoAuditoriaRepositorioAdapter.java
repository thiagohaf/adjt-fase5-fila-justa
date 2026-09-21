package com.confirmasus.auditoria.infrastructure.persistence;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
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
        DecisaoAuditoriaJpaEntity entity = new DecisaoAuditoriaJpaEntity(
                decisao.getEventId(),
                decisao.getAgendamentoId(),
                decisao.getPacienteId(),
                decisao.getTipoDecisao(),
                decisao.getMotivo(),
                decisao.getTimestamp(),
                decisao.getCriadoEm(),
                null // payloadBruto preenchido pelo consumer job antes de chamar salvar
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

    private DecisaoAuditoria paraDominio(DecisaoAuditoriaJpaEntity entity) {
        return new DecisaoAuditoria(
                entity.getId(),
                entity.getEventId(),
                entity.getAgendamentoId(),
                entity.getPacienteId(),
                entity.getTipoDecisao(),
                entity.getMotivo(),
                entity.getTimestamp(),
                entity.getCriadoEm()
        );
    }
}
