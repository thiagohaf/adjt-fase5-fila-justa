package com.confirmasus.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SugestaoRepasseJpaRepository extends JpaRepository<SugestaoRepasseJpaEntity, UUID> {

    // Idempotencia por agendamento_id (AD-6): 0 linhas = Vaga ja tratada.
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.sugestao_repasse "
            + "(sugestao_id, agendamento_id, recurso_id, paciente_id, status, criado_em) "
            + "VALUES (:sugestaoId, :agendamentoId, :recursoId, :pacienteId, :status, :criadoEm) "
            + "ON CONFLICT (agendamento_id) DO NOTHING", nativeQuery = true)
    int inserirSeAusente(@Param("sugestaoId") UUID sugestaoId,
                         @Param("agendamentoId") long agendamentoId,
                         @Param("recursoId") UUID recursoId,
                         @Param("pacienteId") Long pacienteId,
                         @Param("status") String status,
                         @Param("criadoEm") Instant criadoEm);

    @Modifying
    @Query(value = "UPDATE matching_alocacao.sugestao_repasse "
            + "SET status = 'CONFIRMADA', decidido_em = :decididoEm "
            + "WHERE sugestao_id = :sugestaoId AND status = 'PENDENTE'", nativeQuery = true)
    int confirmar(@Param("sugestaoId") UUID sugestaoId, @Param("decididoEm") Instant decididoEm);

    @Modifying
    @Query(value = "UPDATE matching_alocacao.sugestao_repasse "
            + "SET paciente_id = CAST(:proximo AS BIGINT), "
            + "status = CASE WHEN CAST(:proximo AS BIGINT) IS NULL THEN 'ESGOTADA' ELSE 'PENDENTE' END, "
            + "decidido_em = CASE WHEN CAST(:proximo AS BIGINT) IS NULL THEN CAST(:decididoEm AS TIMESTAMPTZ) ELSE NULL END "
            + "WHERE sugestao_id = :sugestaoId AND status = 'PENDENTE' AND paciente_id = :pacienteAtual",
            nativeQuery = true)
    int reatribuir(@Param("sugestaoId") UUID sugestaoId,
                   @Param("pacienteAtual") long pacienteAtual,
                   @Param("proximo") Long proximo,
                   @Param("decididoEm") Instant decididoEm);

    @Query(value = "SELECT * FROM matching_alocacao.sugestao_repasse "
            + "WHERE recurso_id = :recursoId AND status = 'PENDENTE' "
            + "ORDER BY criado_em DESC LIMIT 1", nativeQuery = true)
    List<SugestaoRepasseJpaEntity> pendentesPorRecurso(@Param("recursoId") UUID recursoId);

    @Query(value = "SELECT * FROM matching_alocacao.sugestao_repasse "
            + "WHERE recurso_id = :recursoId "
            + "ORDER BY criado_em DESC LIMIT 1", nativeQuery = true)
    List<SugestaoRepasseJpaEntity> maisRecentePorRecurso(@Param("recursoId") UUID recursoId);
}
