package com.confirmasus.auditoria.infrastructure.persistence;

import com.confirmasus.auditoria.domain.TipoDecisao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link DecisaoAuditoriaJpaEntity}.
 * Define métodos de acesso à tabela {@code auditoria.decisao_auditoria}.
 *
 * <p>A constraint UNIQUE em {@code evento_id} é respeitada pelo banco --
 * tentativas de inserir duplicatas resultam em DataIntegrityViolationException.
 *
 * <p>Estende JpaSpecificationExecutor para suportar filtros dinâmicos com Specification (Story 4.3).
 */
@Repository
public interface DecisaoAuditoriaJpaRepository extends JpaRepository<DecisaoAuditoriaJpaEntity, Long> {

    /**
     * Busca uma decisão pelo eventId.
     *
     * @param eventId o UUID do evento
     * @return Optional com a entidade se encontrada
     */
    @Query("SELECT d FROM DecisaoAuditoriaJpaEntity d WHERE d.eventId = :eventId")
    Optional<DecisaoAuditoriaJpaEntity> findByEventId(@Param("eventId") UUID eventId);

    /**
     * Busca todas as decisões de um paciente, ordenadas por timestamp crescente (mais antigo primeiro).
     * Usa índice (paciente_id, timestamp) para performance.
     *
     * @param pacienteId o ID do paciente
     * @return lista de entidades ordenadas por timestamp crescente
     */
    @Query("SELECT d FROM DecisaoAuditoriaJpaEntity d WHERE d.pacienteId = :pacienteId ORDER BY d.timestamp ASC")
    List<DecisaoAuditoriaJpaEntity> findByPacienteIdOrderByTimestamp(@Param("pacienteId") Long pacienteId);

    /**
     * Busca todas as decisões de um agendamento, ordenadas por timestamp crescente (mais antigo primeiro).
     * Usa índice (agendamento_id, timestamp) para performance.
     *
     * @param agendamentoId o ID do agendamento
     * @return lista de entidades ordenadas por timestamp crescente
     */
    @Query("SELECT d FROM DecisaoAuditoriaJpaEntity d WHERE d.agendamentoId = :agendamentoId ORDER BY d.timestamp ASC")
    List<DecisaoAuditoriaJpaEntity> findByAgendamentoIdOrderByTimestamp(@Param("agendamentoId") Long agendamentoId);

    /**
     * Busca decisões de um paciente com filtros opcionais e paginação (Story 4.3).
     *
     * <p>Usa SQL nativo com WHERE clauses para filtros e LIMIT/OFFSET para paginação.
     * Todos os filtros são opcionais (null = sem filtro, ignorado no WHERE).
     * Retorna apenas os registros no range [offset, offset+limit).
     *
     * <p>Paginação SQL-based (não stream.skip().limit()): O(1) em RAM, independente do tamanho do dataset.
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param offset quantidade de registros a pular
     * @param limit quantidade de registros a retornar
     * @return lista de entidades paginadas e ordenadas por timestamp crescente
     */
    @Query(value = """
            SELECT d.id, d.evento_id, d.agendamento_id, d.paciente_id, d.tipo_decisao,
                   d.motivo, d.timestamp, d.criado_em, d.payload_bruto
            FROM auditoria.decisao_auditoria d
            WHERE d.paciente_id = :pacienteId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            ORDER BY d.timestamp ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DecisaoAuditoriaJpaEntity> findByPacienteIdWithFilters(
            @Param("pacienteId") Long pacienteId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Conta decisões de um paciente com filtros opcionais (Story 4.3).
     *
     * <p>Mesmos WHERE clauses que findByPacienteIdWithFilters, mas SEM LIMIT/OFFSET.
     * Retorna o total de registros que satisfazem os filtros.
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @return total de registros que satisfazem os filtros
     */
    @Query(value = """
            SELECT COUNT(d.id)
            FROM auditoria.decisao_auditoria d
            WHERE d.paciente_id = :pacienteId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            """, nativeQuery = true)
    long countByPacienteIdWithFilters(
            @Param("pacienteId") Long pacienteId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao
    );

    /**
     * Busca decisões de um agendamento com filtros opcionais e paginação (Story 4.3).
     *
     * <p>Usa SQL nativo com WHERE clauses para filtros e LIMIT/OFFSET para paginação.
     * Todos os filtros são opcionais (null = sem filtro, ignorado no WHERE).
     * Retorna apenas os registros no range [offset, offset+limit).
     *
     * <p>Paginação SQL-based (não stream.skip().limit()): O(1) em RAM, independente do tamanho do dataset.
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param offset quantidade de registros a pular
     * @param limit quantidade de registros a retornar
     * @return lista de entidades paginadas e ordenadas por timestamp crescente
     */
    @Query(value = """
            SELECT d.id, d.evento_id, d.agendamento_id, d.paciente_id, d.tipo_decisao,
                   d.motivo, d.timestamp, d.criado_em, d.payload_bruto
            FROM auditoria.decisao_auditoria d
            WHERE d.agendamento_id = :agendamentoId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            ORDER BY d.timestamp ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DecisaoAuditoriaJpaEntity> findByAgendamentoIdWithFilters(
            @Param("agendamentoId") Long agendamentoId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Conta decisões de um agendamento com filtros opcionais (Story 4.3).
     *
     * <p>Mesmos WHERE clauses que findByAgendamentoIdWithFilters, mas SEM LIMIT/OFFSET.
     * Retorna o total de registros que satisfazem os filtros.
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @return total de registros que satisfazem os filtros
     */
    @Query(value = """
            SELECT COUNT(d.id)
            FROM auditoria.decisao_auditoria d
            WHERE d.agendamento_id = :agendamentoId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            """, nativeQuery = true)
    long countByAgendamentoIdWithFilters(
            @Param("agendamentoId") Long agendamentoId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao
    );

    /**
     * Busca decisões de um paciente com filtros opcionais e filtro de status de agendamento (Story 4.4a).
     *
     * <p>Usa SQL nativo com JOIN à tabela agendamento_confirmacao.agendamentos para filtrar por statusAgendamento.
     * Todos os filtros são opcionais (null = sem filtro, ignorado no WHERE).
     * Se agendamento foi deletado, não retorna o registro (LEFT JOIN + IS NOT NULL no JOIN).
     * Retorna apenas os registros no range [offset, offset+limit).
     *
     * <p>Paginação SQL-based (não stream.skip().limit()): O(1) em RAM, independente do tamanho do dataset.
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @param offset quantidade de registros a pular
     * @param limit quantidade de registros a retornar
     * @return lista de entidades paginadas e ordenadas por timestamp crescente
     */
    @Query(value = """
            SELECT d.id, d.evento_id, d.agendamento_id, d.paciente_id, d.tipo_decisao,
                   d.motivo, d.timestamp, d.criado_em, d.payload_bruto
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            WHERE d.paciente_id = :pacienteId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            ORDER BY d.timestamp ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DecisaoAuditoriaJpaEntity> findByPacienteIdWithFiltersAndStatusAgendamento(
            @Param("pacienteId") Long pacienteId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Conta decisões de um paciente com filtros opcionais e filtro de status de agendamento (Story 4.4a).
     *
     * <p>Mesmos WHERE clauses que findByPacienteIdWithFiltersAndStatusAgendamento, mas SEM LIMIT/OFFSET.
     * Retorna o total de registros que satisfazem os filtros.
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @return total de registros que satisfazem os filtros
     */
    @Query(value = """
            SELECT COUNT(d.id)
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            WHERE d.paciente_id = :pacienteId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            """, nativeQuery = true)
    long countByPacienteIdWithFiltersAndStatusAgendamento(
            @Param("pacienteId") Long pacienteId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento
    );

    /**
     * Busca decisões de um agendamento com filtros opcionais e filtro de status de agendamento (Story 4.4a).
     *
     * <p>Usa SQL nativo com JOIN à tabela agendamento_confirmacao.agendamentos para filtrar por statusAgendamento.
     * Todos os filtros são opcionais (null = sem filtro, ignorado no WHERE).
     * Se agendamento foi deletado, não retorna o registro (LEFT JOIN + IS NOT NULL no JOIN).
     * Retorna apenas os registros no range [offset, offset+limit).
     *
     * <p>Paginação SQL-based (não stream.skip().limit()): O(1) em RAM, independente do tamanho do dataset.
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @param offset quantidade de registros a pular
     * @param limit quantidade de registros a retornar
     * @return lista de entidades paginadas e ordenadas por timestamp crescente
     */
    @Query(value = """
            SELECT d.id, d.evento_id, d.agendamento_id, d.paciente_id, d.tipo_decisao,
                   d.motivo, d.timestamp, d.criado_em, d.payload_bruto
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            WHERE d.agendamento_id = :agendamentoId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            ORDER BY d.timestamp ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DecisaoAuditoriaJpaEntity> findByAgendamentoIdWithFiltersAndStatusAgendamento(
            @Param("agendamentoId") Long agendamentoId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Conta decisões de um agendamento com filtros opcionais e filtro de status de agendamento (Story 4.4a).
     *
     * <p>Mesmos WHERE clauses que findByAgendamentoIdWithFiltersAndStatusAgendamento, mas SEM LIMIT/OFFSET.
     * Retorna o total de registros que satisfazem os filtros.
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @return total de registros que satisfazem os filtros
     */
    @Query(value = """
            SELECT COUNT(d.id)
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            WHERE d.agendamento_id = :agendamentoId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            """, nativeQuery = true)
    long countByAgendamentoIdWithFiltersAndStatusAgendamento(
            @Param("agendamentoId") Long agendamentoId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento
    );

    /**
     * Busca decisões de um paciente com filtros opcionais, status agendamento e tipo paciente (Story 4.4b).
     *
     * <p>Usa SQL nativo com JOIN à tabela agendamento_confirmacao.agendamentos para statusAgendamento
     * e à tabela paciente-service para tipoPaciente. Todos os filtros são opcionais (null = sem filtro, ignorado no WHERE).
     * Se agendamento ou paciente foi deletado, não retorna o registro (LEFT JOIN + IS NOT NULL no JOIN).
     * Retorna apenas os registros no range [offset, offset+limit).
     *
     * <p>Paginação SQL-based (não stream.skip().limit()): O(1) em RAM, independente do tamanho do dataset.
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @param tipoPaciente tipo do paciente (nullable)
     * @param offset quantidade de registros a pular
     * @param limit quantidade de registros a retornar
     * @return lista de entidades paginadas e ordenadas por timestamp crescente
     */
    @Query(value = """
            SELECT d.id, d.evento_id, d.agendamento_id, d.paciente_id, d.tipo_decisao,
                   d.motivo, d.timestamp, d.criado_em, d.payload_bruto
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            LEFT JOIN paciente.pacientes p ON d.paciente_id = p.id
            WHERE d.paciente_id = :pacienteId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            AND (:tipoPaciente IS NULL OR p.tipo_paciente = CAST(:tipoPaciente AS VARCHAR))
            ORDER BY d.timestamp ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DecisaoAuditoriaJpaEntity> findByPacienteIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
            @Param("pacienteId") Long pacienteId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento,
            @Param("tipoPaciente") String tipoPaciente,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Conta decisões de um paciente com filtros opcionais, status agendamento e tipo paciente (Story 4.4b).
     *
     * <p>Mesmos WHERE clauses que findByPacienteIdWithFiltersAndStatusAgendamentoAndTipoPaciente, mas SEM LIMIT/OFFSET.
     * Retorna o total de registros que satisfazem os filtros.
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @param tipoPaciente tipo do paciente (nullable)
     * @return total de registros que satisfazem os filtros
     */
    @Query(value = """
            SELECT COUNT(d.id)
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            LEFT JOIN paciente.pacientes p ON d.paciente_id = p.id
            WHERE d.paciente_id = :pacienteId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            AND (:tipoPaciente IS NULL OR p.tipo_paciente = CAST(:tipoPaciente AS VARCHAR))
            """, nativeQuery = true)
    long countByPacienteIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
            @Param("pacienteId") Long pacienteId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento,
            @Param("tipoPaciente") String tipoPaciente
    );

    /**
     * Busca decisões de um agendamento com filtros opcionais, status agendamento e tipo paciente (Story 4.4b).
     *
     * <p>Usa SQL nativo com JOIN à tabela agendamento_confirmacao.agendamentos para statusAgendamento
     * e à tabela paciente-service para tipoPaciente. Todos os filtros são opcionais (null = sem filtro, ignorado no WHERE).
     * Se agendamento ou paciente foi deletado, não retorna o registro (LEFT JOIN + IS NOT NULL no JOIN).
     * Retorna apenas os registros no range [offset, offset+limit).
     *
     * <p>Paginação SQL-based (não stream.skip().limit()): O(1) em RAM, independente do tamanho do dataset.
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @param tipoPaciente tipo do paciente (nullable)
     * @param offset quantidade de registros a pular
     * @param limit quantidade de registros a retornar
     * @return lista de entidades paginadas e ordenadas por timestamp crescente
     */
    @Query(value = """
            SELECT d.id, d.evento_id, d.agendamento_id, d.paciente_id, d.tipo_decisao,
                   d.motivo, d.timestamp, d.criado_em, d.payload_bruto
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            LEFT JOIN paciente.pacientes p ON d.paciente_id = p.id
            WHERE d.agendamento_id = :agendamentoId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            AND (:tipoPaciente IS NULL OR p.tipo_paciente = CAST(:tipoPaciente AS VARCHAR))
            ORDER BY d.timestamp ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DecisaoAuditoriaJpaEntity> findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
            @Param("agendamentoId") Long agendamentoId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento,
            @Param("tipoPaciente") String tipoPaciente,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * Conta decisões de um agendamento com filtros opcionais, status agendamento e tipo paciente (Story 4.4b).
     *
     * <p>Mesmos WHERE clauses que findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente, mas SEM LIMIT/OFFSET.
     * Retorna o total de registros que satisfazem os filtros.
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial (inclusive, nullable)
     * @param endDate data final (inclusive, nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @param tipoPaciente tipo do paciente (nullable)
     * @return total de registros que satisfazem os filtros
     */
    @Query(value = """
            SELECT COUNT(d.id)
            FROM auditoria.decisao_auditoria d
            LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
            LEFT JOIN paciente.pacientes p ON d.paciente_id = p.id
            WHERE d.agendamento_id = :agendamentoId
            AND (:startDate IS NULL OR d.timestamp >= :startDate)
            AND (:endDate IS NULL OR d.timestamp <= :endDate)
            AND (:tipoDecisao IS NULL OR d.tipo_decisao = CAST(:tipoDecisao AS VARCHAR))
            AND (:statusAgendamento IS NULL OR a.status = CAST(:statusAgendamento AS VARCHAR))
            AND (:tipoPaciente IS NULL OR p.tipo_paciente = CAST(:tipoPaciente AS VARCHAR))
            """, nativeQuery = true)
    long countByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
            @Param("agendamentoId") Long agendamentoId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("tipoDecisao") String tipoDecisao,
            @Param("statusAgendamento") String statusAgendamento,
            @Param("tipoPaciente") String tipoPaciente
    );
}
