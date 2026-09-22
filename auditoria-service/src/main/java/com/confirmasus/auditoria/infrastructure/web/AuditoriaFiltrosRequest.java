package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.domain.StatusAgendamento;
import com.confirmasus.auditoria.domain.TipoDecisao;
import com.confirmasus.auditoria.domain.TipoPaciente;

import java.time.Instant;
import java.util.Optional;

/**
 * DTO de entrada com filtros opcionais para consultas de auditoria (Story 4.3 + 4.4a + 4.4b).
 *
 * <p>Todos os campos são opcionais. Quando nenhum filtro é fornecido,
 * retorna a lista completa com paginação padrão (limit=50, offset=0).
 *
 * <p>Campos:
 * <ul>
 *   <li>{@code startDate} - Data inicial do range (ISO-8601, UTC, nullable)
 *   <li>{@code endDate} - Data final do range (ISO-8601, UTC, nullable)
 *   <li>{@code tipoDecisao} - Tipo de decisão para filtrar (enum nullable)
 *   <li>{@code statusAgendamento} - Status do agendamento para filtrar (enum nullable, Story 4.4a)
 *   <li>{@code tipoPaciente} - Tipo de paciente para filtrar (enum nullable, Story 4.4b)
 *   <li>{@code limit} - Quantidade máxima de registros (default=50, máximo=200)
 *   <li>{@code offset} - Posição inicial para paginação (default=0)
 * </ul>
 *
 * <p>Validações aplicadas no controller:
 * <ul>
 *   <li>Se startDate e endDate são fornecidos, startDate <= endDate (senão HTTP 400)
 *   <li>Se tipoDecisao é fornecido, deve ser enum válido (senão HTTP 400)
 *   <li>Se statusAgendamento é fornecido, deve ser enum válido (senão HTTP 400)
 *   <li>Se tipoPaciente é fornecido, deve ser enum válido (senão HTTP 400)
 *   <li>limit <= 200 (senão HTTP 400)
 *   <li>limit >= 1 e offset >= 0
 * </ul>
 */
record AuditoriaFiltrosRequest(
        Instant startDate,
        Instant endDate,
        TipoDecisao tipoDecisao,
        StatusAgendamento statusAgendamento,
        TipoPaciente tipoPaciente,
        Integer limit,
        Integer offset
) {

    // Valores padrão (constantes)
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;
    public static final int DEFAULT_OFFSET = 0;

    /**
     * Retorna o limit efetivo: valor fornecido ou DEFAULT_LIMIT se null.
     *
     * @return limit entre 1 e MAX_LIMIT
     */
    public int getEffectiveLimit() {
        return limit != null && limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
    }

    /**
     * Retorna o offset efetivo: valor fornecido ou DEFAULT_OFFSET se null.
     *
     * @return offset >= 0
     */
    public int getEffectiveOffset() {
        return offset != null && offset >= 0 ? offset : DEFAULT_OFFSET;
    }

    /**
     * Retorna startDate como Optional.
     *
     * @return Optional.of(startDate) ou Optional.empty()
     */
    public Optional<Instant> getStartDateOptional() {
        return Optional.ofNullable(startDate);
    }

    /**
     * Retorna endDate como Optional.
     *
     * @return Optional.of(endDate) ou Optional.empty()
     */
    public Optional<Instant> getEndDateOptional() {
        return Optional.ofNullable(endDate);
    }

    /**
     * Retorna tipoDecisao como Optional.
     *
     * @return Optional.of(tipoDecisao) ou Optional.empty()
     */
    public Optional<TipoDecisao> getTipoDecisaoOptional() {
        return Optional.ofNullable(tipoDecisao);
    }

    /**
     * Retorna statusAgendamento como Optional.
     *
     * @return Optional.of(statusAgendamento) ou Optional.empty()
     */
    public Optional<StatusAgendamento> getStatusAgendamentoOptional() {
        return Optional.ofNullable(statusAgendamento);
    }

    /**
     * Retorna tipoPaciente como Optional.
     *
     * @return Optional.of(tipoPaciente) ou Optional.empty()
     */
    public Optional<TipoPaciente> getTipoPacienteOptional() {
        return Optional.ofNullable(tipoPaciente);
    }

    /**
     * Factory method para construir com validação básica.
     *
     * @param startDate data inicial (nullable)
     * @param endDate data final (nullable)
     * @param tipoDecisao tipo de decisão (nullable)
     * @param statusAgendamento status do agendamento (nullable)
     * @param tipoPaciente tipo de paciente (nullable)
     * @param limit quantidade de registros (nullable)
     * @param offset posição inicial (nullable)
     * @return AuditoriaFiltrosRequest validado
     * @throws IllegalArgumentException se startDate > endDate
     * @throws IllegalArgumentException se limit > MAX_LIMIT
     */
    public static AuditoriaFiltrosRequest of(Instant startDate,
                                              Instant endDate,
                                              TipoDecisao tipoDecisao,
                                              StatusAgendamento statusAgendamento,
                                              TipoPaciente tipoPaciente,
                                              Integer limit,
                                              Integer offset) {
        // Validação de range de datas
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Data inicial não pode ser maior que data final");
        }

        // Validação de limit
        if (limit != null && limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit máximo é " + MAX_LIMIT);
        }

        return new AuditoriaFiltrosRequest(startDate, endDate, tipoDecisao, statusAgendamento, tipoPaciente, limit, offset);
    }
}
