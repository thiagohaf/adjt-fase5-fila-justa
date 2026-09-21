package com.confirmasus.auditoria.application.port;

import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;

import java.time.Instant;
import java.util.List;

/**
 * Porta de persistência para a entidade DecisaoAuditoria.
 * Define o contrato entre a camada de aplicação e a infraestrutura (JPA).
 *
 * <p>Implementado por {@link com.confirmasus.auditoria.infrastructure.persistence.DecisaoAuditoriaRepositorioAdapter}.
 */
public interface DecisaoAuditoriaRepositorio {

    /**
     * Persiste uma decisão auditória na tabela {@code decisao_auditoria} (append-only).
     *
     * @param decisao a decisão a ser registrada
     * @throws org.springframework.dao.DataIntegrityViolationException se eventId já existe (dedup)
     */
    void salvar(DecisaoAuditoria decisao);

    /**
     * Busca uma decisão pelo eventId (para validação de idempotência).
     *
     * @param eventId o identificador do evento
     * @return a decisão se encontrada, null caso contrário
     */
    DecisaoAuditoria buscarPorEventId(java.util.UUID eventId);

    /**
     * Busca todas as decisões de um paciente, ordenadas por timestamp crescente (mais antigo primeiro).
     * Retorna lista vazia se nenhum registro for encontrado (nunca lança erro 404).
     *
     * @param pacienteId o ID do paciente
     * @return lista de decisões ordenadas por timestamp crescente
     */
    List<DecisaoAuditoria> findByPacienteIdOrderByTimestamp(Long pacienteId);

    /**
     * Busca todas as decisões de um agendamento, ordenadas por timestamp crescente (mais antigo primeiro).
     * Retorna lista vazia se nenhum registro for encontrado (nunca lança erro 404).
     *
     * @param agendamentoId o ID do agendamento
     * @return lista de decisões ordenadas por timestamp crescente
     */
    List<DecisaoAuditoria> findByAgendamentoIdOrderByTimestamp(Long agendamentoId);

    /**
     * Busca decisões de um paciente com filtros opcionais e paginação (Story 4.3).
     *
     * <p>Retorna resultado contendo lista paginada e total de registros que satisfazem os filtros.
     * Filtros são compostos com AND logic (todos devem ser satisfeitos).
     *
     * @param pacienteId o ID do paciente
     * @param startDate data inicial do range (nullable, inclusivo)
     * @param endDate data final do range (nullable, inclusivo)
     * @param tipoDecisao tipo de decisão para filtrar (nullable)
     * @param limit quantidade de registros a retornar (1-200)
     * @param offset posição inicial (0 = primeiro registro)
     * @return resultado com items paginados e total de registros
     */
    PaginatedResult<DecisaoAuditoria> findByPacienteIdWithFilters(Long pacienteId,
                                                                    Instant startDate,
                                                                    Instant endDate,
                                                                    TipoDecisao tipoDecisao,
                                                                    int limit,
                                                                    int offset);

    /**
     * Busca decisões de um agendamento com filtros opcionais e paginação (Story 4.3).
     *
     * <p>Retorna resultado contendo lista paginada e total de registros que satisfazem os filtros.
     * Filtros são compostos com AND logic (todos devem ser satisfeitos).
     *
     * @param agendamentoId o ID do agendamento
     * @param startDate data inicial do range (nullable, inclusivo)
     * @param endDate data final do range (nullable, inclusivo)
     * @param tipoDecisao tipo de decisão para filtrar (nullable)
     * @param limit quantidade de registros a retornar (1-200)
     * @param offset posição inicial (0 = primeiro registro)
     * @return resultado com items paginados e total de registros
     */
    PaginatedResult<DecisaoAuditoria> findByAgendamentoIdWithFilters(Long agendamentoId,
                                                                      Instant startDate,
                                                                      Instant endDate,
                                                                      TipoDecisao tipoDecisao,
                                                                      int limit,
                                                                      int offset);

    /**
     * Encapsula resultado de consulta paginada com metadados.
     *
     * @param <T> tipo do item
     * @param items lista de itens paginados
     * @param total quantidade total de registros que satisfazem os filtros
     */
    record PaginatedResult<T>(List<T> items, long total) {
        public PaginatedResult {
            if (items == null) {
                items = List.of();
            }
        }
    }
}

