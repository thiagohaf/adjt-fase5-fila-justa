package com.confirmasus.auditoria.application.query;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.StatusAgendamento;
import com.confirmasus.auditoria.domain.TipoDecisao;
import com.confirmasus.auditoria.domain.TipoPaciente;
import com.confirmasus.auditoria.infrastructure.client.PacienteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Query use case: consultar o histórico de decisões auditáveis
 * de um agendamento específico, com suporte a filtros opcionais e paginação (Stories 4.2 + 4.3).
 *
 * <p>Implementa:
 * <ul>
 *   <li>Story 4.2: GET {@code /v1/auditoria/agendamento/{agendamentoId}} (sem filtros, retorna tudo)
 *   <li>Story 4.3: GET {@code /v1/auditoria/agendamento/{agendamentoId}?startDate=...&endDate=...&tipoDecisao=...&limit=...&offset=...}
 * </ul>
 *
 * <p>Retorna lista vazia (nunca erro 404) se o agendamento não tiver histórico.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Valida entrada (agendamentoId > 0)
 *   <li>Propaga {@code X-Correlation-Id} ao MDC para structured logging (NFR-2)
 *   <li>Delega busca ao repositório com filtros opcionais
 *   <li>Retorna DTOs prontos para serialização REST
 * </ul>
 */
@Component
public class ConsultarAuditoriaAgendamento {

    private static final Logger logger = LoggerFactory.getLogger(ConsultarAuditoriaAgendamento.class);
    private final DecisaoAuditoriaRepositorio decisaoAuditoriaRepositorio;
    private final PacienteClient pacienteClient;

    /**
     * Construtor de injeção de dependências.
     *
     * @param decisaoAuditoriaRepositorio porta de persistência de decisões auditáveis
     * @param pacienteClient cliente para resolver dados de paciente (Story 4.4b)
     */
    public ConsultarAuditoriaAgendamento(DecisaoAuditoriaRepositorio decisaoAuditoriaRepositorio,
                                         PacienteClient pacienteClient) {
        this.decisaoAuditoriaRepositorio = decisaoAuditoriaRepositorio;
        this.pacienteClient = pacienteClient;
    }

    /**
     * Consulta o histórico de decisões auditáveis de um agendamento (Story 4.2 - sem filtros).
     *
     * <p>Propaga {@code X-Correlation-Id} ao MDC para structured logging conforme NFR-2.
     *
     * @param agendamentoId o ID do agendamento (validado antes de chegar aqui)
     * @param correlationId o ID de correlação opcional para rastreamento distribuído
     * @return lista de decisões ordenadas por timestamp crescente (vazia se sem histórico)
     */
    @Transactional(readOnly = true)
    public List<DecisaoAuditoria> consultar(Long agendamentoId, Optional<String> correlationId) {
        // Propaga X-Correlation-Id ao MDC
        correlationId.ifPresentOrElse(
                id -> MDC.put("X-Correlation-Id", id),
                () -> MDC.remove("X-Correlation-Id")
        );

        try {
            // Valida entrada
            if (agendamentoId == null || agendamentoId <= 0) {
                return List.of();
            }

            // Busca histórico (já vem ordenado do repositório)
            List<DecisaoAuditoria> resultado = decisaoAuditoriaRepositorio.findByAgendamentoIdOrderByTimestamp(agendamentoId);

            // Null-check defensivo
            return resultado != null ? resultado : List.of();
        } finally {
            // Limpa MDC após processamento
            MDC.remove("X-Correlation-Id");
        }
    }

    /**
     * Consulta o histórico de decisões auditáveis de um agendamento (overload sem correlationId).
     *
     * @param agendamentoId o ID do agendamento
     * @return lista de decisões ordenadas por timestamp crescente (vazia se sem histórico)
     */
    @Transactional(readOnly = true)
    public List<DecisaoAuditoria> consultar(Long agendamentoId) {
        return consultar(agendamentoId, Optional.empty());
    }

    /**
     * Consulta com filtros opcionais e paginação (Story 4.3).
     *
     * <p>Propaga {@code X-Correlation-Id} ao MDC para structured logging conforme NFR-2.
     *
     * <p>Filtros são compostos com AND logic: todos os critérios fornecidos devem ser satisfeitos.
     * Quando nenhum filtro é fornecido, comporta-se igual à Story 4.2.
     *
     * @param agendamentoId o ID do agendamento (validado antes de chegar aqui)
     * @param startDate data inicial do range (inclusive, nullable)
     * @param endDate data final do range (inclusive, nullable)
     * @param tipoDecisao tipo de decisão para filtrar (nullable)
     * @param limit quantidade máxima de registros (1-200, já validado no controller)
     * @param offset posição inicial para paginação (>= 0, já validado no controller)
     * @param correlationId o ID de correlação opcional para rastreamento distribuído
     * @return resultado paginado com items e total
     */
    @Transactional(readOnly = true)
    public DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> consultarComFiltros(
            Long agendamentoId,
            Instant startDate,
            Instant endDate,
            TipoDecisao tipoDecisao,
            int limit,
            int offset,
            Optional<String> correlationId
    ) {
        // Propaga X-Correlation-Id ao MDC
        correlationId.ifPresentOrElse(
                id -> MDC.put("X-Correlation-Id", id),
                () -> MDC.remove("X-Correlation-Id")
        );

        try {
            // Valida entrada
            if (agendamentoId == null || agendamentoId <= 0) {
                return new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
            }

            // Delega ao repositório com filtros
            DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                    decisaoAuditoriaRepositorio.findByAgendamentoIdWithFilters(
                            agendamentoId, startDate, endDate, tipoDecisao, limit, offset
                    );

            // Null-check defensivo
            return resultado != null ? resultado : new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        } finally {
            // Limpa MDC após processamento
            MDC.remove("X-Correlation-Id");
        }
    }

    /**
     * Consulta com filtros opcionais, incluindo status de agendamento, e paginação (Story 4.4a).
     *
     * <p>Propaga {@code X-Correlation-Id} ao MDC para structured logging conforme NFR-2.
     *
     * <p>Filtros são compostos com AND logic: todos os critérios fornecidos devem ser satisfeitos.
     * Se agendamento foi deletado, não retorna o registro.
     *
     * @param agendamentoId o ID do agendamento (validado antes de chegar aqui)
     * @param startDate data inicial do range (inclusive, nullable)
     * @param endDate data final do range (inclusive, nullable)
     * @param tipoDecisao tipo de decisão para filtrar (nullable)
     * @param statusAgendamento status do agendamento para filtrar (nullable)
     * @param limit quantidade máxima de registros (1-200, já validado no controller)
     * @param offset posição inicial para paginação (>= 0, já validado no controller)
     * @param correlationId o ID de correlação opcional para rastreamento distribuído
     * @return resultado paginado com items e total
     */
    @Transactional(readOnly = true)
    public DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> consultarComFiltrosEStatusAgendamento(
            Long agendamentoId,
            Instant startDate,
            Instant endDate,
            TipoDecisao tipoDecisao,
            StatusAgendamento statusAgendamento,
            int limit,
            int offset,
            Optional<String> correlationId
    ) {
        // Propaga X-Correlation-Id ao MDC
        correlationId.ifPresentOrElse(
                id -> MDC.put("X-Correlation-Id", id),
                () -> MDC.remove("X-Correlation-Id")
        );

        try {
            // Valida entrada
            if (agendamentoId == null || agendamentoId <= 0) {
                return new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
            }

            // Delega ao repositório com filtros
            DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                    decisaoAuditoriaRepositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                            agendamentoId, startDate, endDate, tipoDecisao, statusAgendamento, limit, offset
                    );

            // Null-check defensivo
            return resultado != null ? resultado : new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        } finally {
            // Limpa MDC após processamento
            MDC.remove("X-Correlation-Id");
        }
    }

    /**
     * Consulta com filtros opcionais incluindo filtros de paciente (Story 4.4b).
     *
     * <p>Análogo a {@link ConsultarAuditoriaPaciente#consultarComFiltrosPaciente}, mas para agendamento.
     * Filtragem de paciente ocorre em memória após busca no repositório.
     */
    @Transactional(readOnly = true)
    public DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> consultarComFiltrosPaciente(
            Long agendamentoId,
            Instant startDate,
            Instant endDate,
            TipoDecisao tipoDecisao,
            StatusAgendamento statusAgendamento,
            TipoPaciente tipoPaciente,
            String nomePaciente,
            String cpfPaciente,
            int limit,
            int offset,
            Optional<String> correlationId
    ) {
        // Propaga X-Correlation-Id ao MDC
        correlationId.ifPresentOrElse(
                id -> MDC.put("X-Correlation-Id", id),
                () -> MDC.remove("X-Correlation-Id")
        );

        try {
            // Valida entrada
            if (agendamentoId == null || agendamentoId <= 0) {
                return new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
            }

            // Busca com filtros de data/decisão/agendamento (sem paginação ainda)
            List<DecisaoAuditoria> todosResultados =
                    decisaoAuditoriaRepositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                            agendamentoId, startDate, endDate, tipoDecisao, statusAgendamento,
                            Integer.MAX_VALUE, 0  // sem limite para depois filtrar e paginar
                    ).items();

            // Filtra por dados de paciente (resolve via PacienteClient)
            List<DecisaoAuditoria> filtrados = todosResultados.stream()
                    .filter(decisao -> matchesFiltrosPaciente(decisao, tipoPaciente, nomePaciente, cpfPaciente))
                    .toList();

            // Total após filtros de paciente
            long total = filtrados.size();

            // Aplica paginação após filtros
            List<DecisaoAuditoria> paginados = filtrados.stream()
                    .skip(offset)
                    .limit(limit)
                    .toList();

            return new DecisaoAuditoriaRepositorio.PaginatedResult<>(paginados, total);
        } finally {
            // Limpa MDC após processamento
            MDC.remove("X-Correlation-Id");
        }
    }

    private boolean matchesFiltrosPaciente(DecisaoAuditoria decisao, TipoPaciente tipoPaciente,
                                           String nomePaciente, String cpfPaciente) {
        // Se nenhum filtro de paciente, retorna true (não filtra)
        if (tipoPaciente == null && nomePaciente == null && cpfPaciente == null) {
            return true;
        }

        // Resolve dados do paciente
        var pacienteDados = pacienteClient.obterPaciente(decisao.getPacienteId());
        if (pacienteDados.isEmpty()) {
            // Paciente não encontrado em nenhum serviço - não retorna
            return false;
        }

        var paciente = pacienteDados.get();

        // Filtro tipoPaciente
        if (tipoPaciente != null && !tipoPaciente.name().equals(paciente.getTipoPaciente())) {
            return false;
        }

        // Filtro nomePaciente (LIKE case-insensitive)
        if (nomePaciente != null && paciente.getNome() != null) {
            if (!paciente.getNome().toLowerCase().contains(nomePaciente.toLowerCase())) {
                return false;
            }
        }

        // Filtro cpfPaciente (match exato)
        if (cpfPaciente != null && !cpfPaciente.equals(paciente.getCpf())) {
            return false;
        }

        return true;
    }

}
