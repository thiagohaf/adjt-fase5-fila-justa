package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.application.query.ConsultarAuditoriaAgendamento;
import com.confirmasus.auditoria.application.query.ConsultarAuditoriaPaciente;
import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Endpoints REST para Consulta de Auditoria (Stories 4.2 + 4.3).
 *
 * <p>Dois endpoints principais (com suporte a filtros opcionais):
 * <ul>
 *   <li>{@code GET /v1/auditoria/paciente/{id}} - histórico de um paciente
 *   <li>{@code GET /v1/auditoria/agendamento/{id}} - histórico de um agendamento
 * </ul>
 *
 * <p>Query parameters opcionais (Story 4.3):
 * <ul>
 *   <li>{@code startDate} - Data inicial (ISO-8601 UTC, ex.: 2026-09-21T00:00:00Z)
 *   <li>{@code endDate} - Data final (ISO-8601 UTC)
 *   <li>{@code tipoDecisao} - Tipo de decisão (enum)
 *   <li>{@code limit} - Quantidade máxima (default 50, máximo 200)
 *   <li>{@code offset} - Posição inicial (default 0)
 * </ul>
 *
 * <p>Ambos retornam array de decisões ordenadas por timestamp crescente (mais antigo primeiro).
 * Quando sem histórico, retorna array vazio (nunca erro 404).
 *
 * <p>Autenticação: qualquer usuário autenticado (sem RBAC nesta fase, FR-14).
 * {@code X-Correlation-Id} é propagado em logs estruturados (NFR-2).
 */
@RestController
public class AuditoriaController {

    private final ConsultarAuditoriaPaciente consultarAuditoriaPaciente;
    private final ConsultarAuditoriaAgendamento consultarAuditoriaAgendamento;

    /**
     * Construtor de injeção de dependências.
     *
     * @param consultarAuditoriaPaciente use case de consulta por paciente
     * @param consultarAuditoriaAgendamento use case de consulta por agendamento
     */
    public AuditoriaController(
            ConsultarAuditoriaPaciente consultarAuditoriaPaciente,
            ConsultarAuditoriaAgendamento consultarAuditoriaAgendamento
    ) {
        this.consultarAuditoriaPaciente = consultarAuditoriaPaciente;
        this.consultarAuditoriaAgendamento = consultarAuditoriaAgendamento;
    }

    /**
     * Consulta o histórico de decisões auditáveis de um paciente específico com filtros opcionais (Stories 4.2 + 4.3).
     *
     * <p>Propaga {@code X-Correlation-Id} do header da request para o MDC (Mapped Diagnostic Context)
     * para rastreamento distribuído conforme NFR-2.
     *
     * <p>Compatibilidade Story 4.2: sem filtros, retorna lista completa com paginação padrão (limit 50, offset 0).
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial do range (ISO-8601 UTC, nullable, inclusivo)
     * @param endDate data final do range (ISO-8601 UTC, nullable, inclusivo)
     * @param tipoDecisao tipo de decisão para filtrar (enum, nullable)
     * @param limit quantidade máxima de registros (default 50, máximo 200)
     * @param offset posição inicial para paginação (default 0)
     * @param correlationId o ID de correlação da request (opcional, header {@code X-Correlation-Id})
     * @return resposta paginada com items e metadados (total, limit, offset)
     *         (vazia se sem histórico, nunca erro 404)
     */
    @GetMapping("/v1/auditoria/paciente/{pacienteId}")
    public Object consultarPaciente(
            @PathVariable Long pacienteId,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "tipoDecisao", required = false) TipoDecisao tipoDecisao,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        // Se algum filtro foi fornecido, usa consulta com filtros (Story 4.3)
        if (startDate != null || endDate != null || tipoDecisao != null || limit != null || offset != null) {
            // Valida filtros no DTO (lançará IllegalArgumentException se inválido)
            AuditoriaFiltrosRequest filtros = AuditoriaFiltrosRequest.of(
                    startDate, endDate, tipoDecisao, limit, offset
            );

            // Executa consulta com filtros
            DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                    consultarAuditoriaPaciente.consultarComFiltros(
                            pacienteId,
                            filtros.startDate(),
                            filtros.endDate(),
                            filtros.tipoDecisao(),
                            filtros.getEffectiveLimit(),
                            filtros.getEffectiveOffset(),
                            Optional.ofNullable(correlationId)
                    );

            // Converte para DTO de resposta
            List<DecisaoAuditoriaResponse> items = resultado.items().stream()
                    .map(DecisaoAuditoriaResponse::de)
                    .toList();

            return (Object) AuditoriaPaginatedResponse.of(
                    items,
                    resultado.total(),
                    filtros.getEffectiveLimit(),
                    filtros.getEffectiveOffset()
            );
        }

        // Compatibilidade Story 4.2: sem filtros, retorna lista simples
        List<DecisaoAuditoriaResponse> items = consultarAuditoriaPaciente.consultar(
                pacienteId, Optional.ofNullable(correlationId)
        ).stream()
                .map(DecisaoAuditoriaResponse::de)
                .toList();

        return items;
    }

    /**
     * Consulta o histórico de decisões auditáveis de um agendamento específico com filtros opcionais (Stories 4.2 + 4.3).
     *
     * <p>Propaga {@code X-Correlation-Id} do header da request para o MDC (Mapped Diagnostic Context)
     * para rastreamento distribuído conforme NFR-2.
     *
     * <p>Compatibilidade Story 4.2: sem filtros, retorna lista completa com paginação padrão (limit 50, offset 0).
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial do range (ISO-8601 UTC, nullable, inclusivo)
     * @param endDate data final do range (ISO-8601 UTC, nullable, inclusivo)
     * @param tipoDecisao tipo de decisão para filtrar (enum, nullable)
     * @param limit quantidade máxima de registros (default 50, máximo 200)
     * @param offset posição inicial para paginação (default 0)
     * @param correlationId o ID de correlação da request (opcional, header {@code X-Correlation-Id})
     * @return resposta paginada com items e metadados (total, limit, offset)
     *         (vazia se sem histórico, nunca erro 404)
     */
    @GetMapping("/v1/auditoria/agendamento/{agendamentoId}")
    public Object consultarAgendamento(
            @PathVariable Long agendamentoId,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "tipoDecisao", required = false) TipoDecisao tipoDecisao,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        // Se algum filtro foi fornecido, usa consulta com filtros (Story 4.3)
        if (startDate != null || endDate != null || tipoDecisao != null || limit != null || offset != null) {
            // Valida filtros no DTO (lançará IllegalArgumentException se inválido)
            AuditoriaFiltrosRequest filtros = AuditoriaFiltrosRequest.of(
                    startDate, endDate, tipoDecisao, limit, offset
            );

            // Executa consulta com filtros
            DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                    consultarAuditoriaAgendamento.consultarComFiltros(
                            agendamentoId,
                            filtros.startDate(),
                            filtros.endDate(),
                            filtros.tipoDecisao(),
                            filtros.getEffectiveLimit(),
                            filtros.getEffectiveOffset(),
                            Optional.ofNullable(correlationId)
                    );

            // Converte para DTO de resposta
            List<DecisaoAuditoriaResponse> items = resultado.items().stream()
                    .map(DecisaoAuditoriaResponse::de)
                    .toList();

            return (Object) AuditoriaPaginatedResponse.of(
                    items,
                    resultado.total(),
                    filtros.getEffectiveLimit(),
                    filtros.getEffectiveOffset()
            );
        }

        // Compatibilidade Story 4.2: sem filtros, retorna lista simples
        List<DecisaoAuditoriaResponse> items = consultarAuditoriaAgendamento.consultar(
                agendamentoId, Optional.ofNullable(correlationId)
        ).stream()
                .map(DecisaoAuditoriaResponse::de)
                .toList();

        return items;
    }
}
