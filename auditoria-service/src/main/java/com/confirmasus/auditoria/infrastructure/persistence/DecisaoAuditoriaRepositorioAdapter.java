package com.confirmasus.auditoria.infrastructure.persistence;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.StatusAgendamento;
import com.confirmasus.auditoria.domain.TipoDecisao;
import com.confirmasus.auditoria.domain.TipoPaciente;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
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

    @Override
    public PaginatedResult<DecisaoAuditoria> findByPacienteIdWithFilters(Long pacienteId,
                                                                           Instant startDate,
                                                                           Instant endDate,
                                                                           TipoDecisao tipoDecisao,
                                                                           int limit,
                                                                           int offset) {
        // Valida entrada
        if (pacienteId == null || pacienteId <= 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Converte TipoDecisao para String para SQL nativo (null se não filtrado)
        String tipoDecisaoStr = tipoDecisao != null ? tipoDecisao.name() : null;

        // Conta total com filtros (SEM paginação)
        long total = jpaRepository.countByPacienteIdWithFilters(pacienteId, startDate, endDate, tipoDecisaoStr);

        // Se total é 0, retorna vazio sem fazer select
        if (total == 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Busca itens paginados com filtros (paginação via SQL LIMIT/OFFSET)
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByPacienteIdWithFilters(
                pacienteId, startDate, endDate, tipoDecisaoStr, offset, limit
        );

        // Converte entidades para domínio
        List<DecisaoAuditoria> items = entities.stream()
                .map(this::paraDominio)
                .toList();

        return new PaginatedResult<>(items, total);
    }

    @Override
    public PaginatedResult<DecisaoAuditoria> findByAgendamentoIdWithFilters(Long agendamentoId,
                                                                              Instant startDate,
                                                                              Instant endDate,
                                                                              TipoDecisao tipoDecisao,
                                                                              int limit,
                                                                              int offset) {
        // Valida entrada
        if (agendamentoId == null || agendamentoId <= 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Converte TipoDecisao para String para SQL nativo (null se não filtrado)
        String tipoDecisaoStr = tipoDecisao != null ? tipoDecisao.name() : null;

        // Conta total com filtros (SEM paginação)
        long total = jpaRepository.countByAgendamentoIdWithFilters(agendamentoId, startDate, endDate, tipoDecisaoStr);

        // Se total é 0, retorna vazio sem fazer select
        if (total == 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Busca itens paginados com filtros (paginação via SQL LIMIT/OFFSET)
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByAgendamentoIdWithFilters(
                agendamentoId, startDate, endDate, tipoDecisaoStr, offset, limit
        );

        // Converte entidades para domínio
        List<DecisaoAuditoria> items = entities.stream()
                .map(this::paraDominio)
                .toList();

        return new PaginatedResult<>(items, total);
    }

    @Override
    public PaginatedResult<DecisaoAuditoria> findByPacienteIdWithFiltersAndStatusAgendamento(Long pacienteId,
                                                                                               Instant startDate,
                                                                                               Instant endDate,
                                                                                               TipoDecisao tipoDecisao,
                                                                                               StatusAgendamento statusAgendamento,
                                                                                               int limit,
                                                                                               int offset) {
        // Valida entrada
        if (pacienteId == null || pacienteId <= 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Converte TipoDecisao para String para SQL nativo (null se não filtrado)
        String tipoDecisaoStr = tipoDecisao != null ? tipoDecisao.name() : null;

        // Converte StatusAgendamento para String para SQL nativo (null se não filtrado)
        String statusAgendamentoStr = statusAgendamento != null ? statusAgendamento.name() : null;

        // Conta total com filtros (SEM paginação)
        long total = jpaRepository.countByPacienteIdWithFiltersAndStatusAgendamento(
                pacienteId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr
        );

        // Se total é 0, retorna vazio sem fazer select
        if (total == 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Busca itens paginados com filtros (paginação via SQL LIMIT/OFFSET)
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByPacienteIdWithFiltersAndStatusAgendamento(
                pacienteId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr, offset, limit
        );

        // Converte entidades para domínio
        List<DecisaoAuditoria> items = entities.stream()
                .map(this::paraDominio)
                .toList();

        return new PaginatedResult<>(items, total);
    }

    @Override
    public PaginatedResult<DecisaoAuditoria> findByAgendamentoIdWithFiltersAndStatusAgendamento(Long agendamentoId,
                                                                                                  Instant startDate,
                                                                                                  Instant endDate,
                                                                                                  TipoDecisao tipoDecisao,
                                                                                                  StatusAgendamento statusAgendamento,
                                                                                                  int limit,
                                                                                                  int offset) {
        // Valida entrada
        if (agendamentoId == null || agendamentoId <= 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Converte TipoDecisao para String para SQL nativo (null se não filtrado)
        String tipoDecisaoStr = tipoDecisao != null ? tipoDecisao.name() : null;

        // Converte StatusAgendamento para String para SQL nativo (null se não filtrado)
        String statusAgendamentoStr = statusAgendamento != null ? statusAgendamento.name() : null;

        // Conta total com filtros (SEM paginação)
        long total = jpaRepository.countByAgendamentoIdWithFiltersAndStatusAgendamento(
                agendamentoId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr
        );

        // Se total é 0, retorna vazio sem fazer select
        if (total == 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Busca itens paginados com filtros (paginação via SQL LIMIT/OFFSET)
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                agendamentoId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr, offset, limit
        );

        // Converte entidades para domínio
        List<DecisaoAuditoria> items = entities.stream()
                .map(this::paraDominio)
                .toList();

        return new PaginatedResult<>(items, total);
    }

    @Override
    public PaginatedResult<DecisaoAuditoria> findByPacienteIdWithFiltersAndStatusAgendamentoAndTipoPaciente(Long pacienteId,
                                                                                                              Instant startDate,
                                                                                                              Instant endDate,
                                                                                                              TipoDecisao tipoDecisao,
                                                                                                              StatusAgendamento statusAgendamento,
                                                                                                              TipoPaciente tipoPaciente,
                                                                                                              int limit,
                                                                                                              int offset) {
        // Valida entrada
        if (pacienteId == null || pacienteId <= 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Converte TipoDecisao para String para SQL nativo (null se não filtrado)
        String tipoDecisaoStr = tipoDecisao != null ? tipoDecisao.name() : null;

        // Converte StatusAgendamento para String para SQL nativo (null se não filtrado)
        String statusAgendamentoStr = statusAgendamento != null ? statusAgendamento.name() : null;

        // Converte TipoPaciente para String para SQL nativo (null se não filtrado)
        String tipoPacienteStr = tipoPaciente != null ? tipoPaciente.name() : null;

        // Conta total com filtros (SEM paginação)
        long total = jpaRepository.countByPacienteIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                pacienteId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr, tipoPacienteStr
        );

        // Se total é 0, retorna vazio sem fazer select
        if (total == 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Busca itens paginados com filtros (paginação via SQL LIMIT/OFFSET)
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByPacienteIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                pacienteId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr, tipoPacienteStr, offset, limit
        );

        // Converte entidades para domínio
        List<DecisaoAuditoria> items = entities.stream()
                .map(this::paraDominio)
                .toList();

        return new PaginatedResult<>(items, total);
    }

    @Override
    public PaginatedResult<DecisaoAuditoria> findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(Long agendamentoId,
                                                                                                                 Instant startDate,
                                                                                                                 Instant endDate,
                                                                                                                 TipoDecisao tipoDecisao,
                                                                                                                 StatusAgendamento statusAgendamento,
                                                                                                                 TipoPaciente tipoPaciente,
                                                                                                                 int limit,
                                                                                                                 int offset) {
        // Valida entrada
        if (agendamentoId == null || agendamentoId <= 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Converte TipoDecisao para String para SQL nativo (null se não filtrado)
        String tipoDecisaoStr = tipoDecisao != null ? tipoDecisao.name() : null;

        // Converte StatusAgendamento para String para SQL nativo (null se não filtrado)
        String statusAgendamentoStr = statusAgendamento != null ? statusAgendamento.name() : null;

        // Converte TipoPaciente para String para SQL nativo (null se não filtrado)
        String tipoPacienteStr = tipoPaciente != null ? tipoPaciente.name() : null;

        // Conta total com filtros (SEM paginação)
        long total = jpaRepository.countByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                agendamentoId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr, tipoPacienteStr
        );

        // Se total é 0, retorna vazio sem fazer select
        if (total == 0) {
            return new PaginatedResult<>(List.of(), 0L);
        }

        // Busca itens paginados com filtros (paginação via SQL LIMIT/OFFSET)
        List<DecisaoAuditoriaJpaEntity> entities = jpaRepository.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                agendamentoId, startDate, endDate, tipoDecisaoStr, statusAgendamentoStr, tipoPacienteStr, offset, limit
        );

        // Converte entidades para domínio
        List<DecisaoAuditoria> items = entities.stream()
                .map(this::paraDominio)
                .toList();

        return new PaginatedResult<>(items, total);
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
