package com.filajusta.matching.application.query;

import java.util.Set;
import java.util.UUID;

/**
 * Porta de leitura de {@code sugestao_recusada} (Story 3-3c2a) -- lado de
 * consulta do CQRS lógico da arquitetura, irmã de
 * {@link com.filajusta.matching.application.command.SugestaoRecusadaRepositorio}
 * (que só faz upsert, Story 3-3c1), mesmo split de
 * {@link com.filajusta.matching.application.command.AlocacaoRepositorio} vs
 * {@link AlocacaoConsultaRepositorio}. Implementada em
 * {@code infrastructure.persistence} reaproveitando o {@code
 * SugestaoRecusadaJpaRepository} já existente, com uma query nativa nova.
 */
public interface SugestaoRecusadaConsultaRepositorio {

    /**
     * {@code pacienteId} de todo par recusado para {@code recursoId} -- {@code
     * Set} vazio quando não há nenhum (nunca exceção, mesmo Boundaries de
     * {@link AlocacaoConsultaRepositorio#pacientesComAlocacaoAtiva()}).
     */
    Set<Long> recusadosPara(UUID recursoId);
}
