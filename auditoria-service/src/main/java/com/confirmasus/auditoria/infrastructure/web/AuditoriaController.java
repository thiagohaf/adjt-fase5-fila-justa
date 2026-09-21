package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.application.query.ConsultarAuditoriaAgendamento;
import com.confirmasus.auditoria.application.query.ConsultarAuditoriaPaciente;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Endpoints REST da Story 4.2: Consulta de Auditoria por Paciente ou Agendamento.
 *
 * <p>Dois endpoints:
 * <ul>
 *   <li>{@code GET /v1/auditoria/paciente/{id}} - histórico de um paciente
 *   <li>{@code GET /v1/auditoria/agendamento/{id}} - histórico de um agendamento
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
     * Consulta o histórico de decisões auditáveis de um paciente específico.
     *
     * <p>Propaga {@code X-Correlation-Id} do header da request para o MDC (Mapped Diagnostic Context)
     * para rastreamento distribuído conforme NFR-2.
     *
     * @param pacienteId o ID do paciente
     * @param correlationId o ID de correlação da request (opcional, header {@code X-Correlation-Id})
     * @return lista de decisões ordenadas por timestamp crescente
     *         (vazia se sem histórico, nunca erro 404)
     */
    @GetMapping("/v1/auditoria/paciente/{pacienteId}")
    public List<DecisaoAuditoriaResponse> consultarPaciente(
            @PathVariable Long pacienteId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return consultarAuditoriaPaciente.consultar(pacienteId, Optional.ofNullable(correlationId))
                .stream()
                .map(DecisaoAuditoriaResponse::de)
                .toList();
    }

    /**
     * Consulta o histórico de decisões auditáveis de um agendamento específico.
     *
     * <p>Propaga {@code X-Correlation-Id} do header da request para o MDC (Mapped Diagnostic Context)
     * para rastreamento distribuído conforme NFR-2.
     *
     * @param agendamentoId o ID do agendamento
     * @param correlationId o ID de correlação da request (opcional, header {@code X-Correlation-Id})
     * @return lista de decisões ordenadas por timestamp crescente
     *         (vazia se sem histórico, nunca erro 404)
     */
    @GetMapping("/v1/auditoria/agendamento/{agendamentoId}")
    public List<DecisaoAuditoriaResponse> consultarAgendamento(
            @PathVariable Long agendamentoId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return consultarAuditoriaAgendamento.consultar(agendamentoId, Optional.ofNullable(correlationId))
                .stream()
                .map(DecisaoAuditoriaResponse::de)
                .toList();
    }
}
