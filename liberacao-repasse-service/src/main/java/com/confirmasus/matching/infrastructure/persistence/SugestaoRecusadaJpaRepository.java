package com.confirmasus.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

interface SugestaoRecusadaJpaRepository extends JpaRepository<SugestaoRecusadaJpaEntity, SugestaoRecusadaJpaEntity.Id> {

    // Leitura em massa dos pacientes recusados para um Recurso (Story
    // 3-3c2a) -- nenhuma linha para :recursoId -> Set vazio (comportamento
    // padrao do Spring Data para uma projecao de colecao sem resultados),
    // nunca excecao, mesmo estilo de AlocacaoJpaRepository#pacientesComAlocacaoAtiva.
    @Query(value = "SELECT paciente_id FROM matching_alocacao.sugestao_recusada WHERE agendamento_id = :agendamentoId",
            nativeQuery = true)
    Set<Long> buscarPacientesRecusados(@Param("agendamentoId") long agendamentoId);

    // Upsert direto e idempotente pela PK composta (recurso_id, paciente_id)
    // (Boundaries "Always" da spec 3-3c1, Design Notes) -- mesmo estilo do
    // upsert sem WHERE temporal de RecursoJpaRepository#upsert: o DO UPDATE
    // sempre aplica num conflito, last-write-wins simples (sugestao_recusada
    // nao tem occurred_at/event_id para comparar, ao contrario de
    // ScoreReplicaJpaRepository#upsertSeMaisRecente). Evita a corrida de
    // findById + save em duas etapas.
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.sugestao_recusada "
            + "(recurso_id, agendamento_id, paciente_id, motivo, recusado_em) "
            + "VALUES (:recursoId, :agendamentoId, :pacienteId, :motivo, :recusadoEm) "
            + "ON CONFLICT (agendamento_id, paciente_id) DO UPDATE SET "
            + "motivo = excluded.motivo, "
            + "recusado_em = excluded.recusado_em",
            nativeQuery = true)
    void upsert(@Param("recursoId") UUID recursoId,
                @Param("agendamentoId") long agendamentoId,
                @Param("pacienteId") long pacienteId,
                @Param("motivo") String motivo,
                @Param("recusadoEm") Instant recusadoEm);
}
