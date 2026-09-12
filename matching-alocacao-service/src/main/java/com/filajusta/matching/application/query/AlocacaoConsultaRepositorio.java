package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.Alocacao;

import java.util.Set;

/**
 * Porta de leitura de {@link Alocacao} (Story 3-3b2a) -- lado de consulta do
 * CQRS lógico da arquitetura, irmã de
 * {@link com.filajusta.matching.application.command.AlocacaoRepositorio}
 * (que só faz insert, Story 3-3b1), mesmo split de
 * {@link com.filajusta.matching.application.command.RecursoRepositorio} vs
 * {@link RecursoConsultaRepositorio}. Implementada em
 * {@code infrastructure.persistence} reaproveitando o {@code
 * AlocacaoJpaRepository} já existente, com um método novo de query nativa
 * para os {@code paciente_id} com {@link Alocacao#STATUS_ATIVA} (Code Map da
 * spec 3-3b2a).
 *
 * <p>Porta puramente aditiva: nenhum consumidor está ligado a ela ainda --
 * ligar {@code ConsultarFilaPriorizada} (ou qualquer outro) a este porto é a
 * Story 3-3b2b, deferida (Boundaries "Never" da spec 3-3b2a).
 */
public interface AlocacaoConsultaRepositorio {

    /**
     * {@code pacienteId} de toda {@link Alocacao} com
     * {@link Alocacao#STATUS_ATIVA} persistida -- {@code Set} vazio quando
     * não há nenhuma (tabela vazia ou nenhuma linha ATIVA), nunca exceção
     * (Boundaries "Always" da spec 3-3b2a).
     */
    Set<Long> pacientesComAlocacaoAtiva();
}
