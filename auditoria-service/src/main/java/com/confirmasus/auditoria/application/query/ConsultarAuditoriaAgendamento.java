package com.confirmasus.auditoria.application.query;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Query use case: consultar o histórico completo de decisões auditáveis
 * de um agendamento específico, ordenado cronologicamente (timestamp crescente).
 *
 * <p>Implementa o contrato de Story 4.2: GET {@code /v1/auditoria/agendamento/{agendamentoId}}.
 * Retorna lista vazia (nunca erro 404) se o agendamento não tiver histórico.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Valida entrada (agendamentoId > 0)
 *   <li>Propaga {@code X-Correlation-Id} ao MDC para structured logging (NFR-2)
 *   <li>Delega busca ao repositório (que já retorna ordenado)
 *   <li>Retorna DTOs prontos para serialização REST
 * </ul>
 */
@Component
public class ConsultarAuditoriaAgendamento {

    private final DecisaoAuditoriaRepositorio decisaoAuditoriaRepositorio;

    /**
     * Construtor de injeção de dependências.
     *
     * @param decisaoAuditoriaRepositorio porta de persistência de decisões auditáveis
     */
    public ConsultarAuditoriaAgendamento(DecisaoAuditoriaRepositorio decisaoAuditoriaRepositorio) {
        this.decisaoAuditoriaRepositorio = decisaoAuditoriaRepositorio;
    }

    /**
     * Consulta o histórico de decisões auditáveis de um agendamento.
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
}
