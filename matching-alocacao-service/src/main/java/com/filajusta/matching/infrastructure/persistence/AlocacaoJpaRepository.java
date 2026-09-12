package com.filajusta.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

interface AlocacaoJpaRepository extends JpaRepository<AlocacaoJpaEntity, UUID> {

    // INSERT simples (sem ON CONFLICT): ao contrario de Recurso, alocacao_id
    // e gerado pela aplicacao e nunca reaproveitado num "upsert" -- uma
    // segunda tentativa para o mesmo recurso_id/paciente_id ATIVO sempre
    // deve FALHAR (409), nunca sobrescrever. Os 2 indices unicos parciais
    // (V5__create_alocacao.sql) sao quem decide isso via violacao de
    // constraint, traduzida por AlocacaoRepositorioAdapter#confirmar.
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.alocacao "
            + "(alocacao_id, recurso_id, paciente_id, status, confirmado_em) "
            + "VALUES (:alocacaoId, :recursoId, :pacienteId, :status, :confirmadoEm)",
            nativeQuery = true)
    void inserir(@Param("alocacaoId") UUID alocacaoId,
                 @Param("recursoId") UUID recursoId,
                 @Param("pacienteId") long pacienteId,
                 @Param("status") String status,
                 @Param("confirmadoEm") Instant confirmadoEm);

    // Story 3-3b2a (AlocacaoConsultaRepositorio): fonte dos paciente_id com
    // Alocacao ATIVA -- mesmo padrao 100% nativo do INSERT acima. Sem
    // nenhuma linha :status -> Set vazio (comportamento padrao do Spring
    // Data para uma projecao de colecao sem resultados), nunca excecao
    // (Boundaries "Always" da spec 3-3b2a).
    @Query(value = "SELECT paciente_id FROM matching_alocacao.alocacao WHERE status = :status",
            nativeQuery = true)
    Set<Long> pacientesComAlocacaoAtiva(@Param("status") String status);
}
