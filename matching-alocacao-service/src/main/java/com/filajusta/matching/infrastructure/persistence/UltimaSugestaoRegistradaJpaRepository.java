package com.filajusta.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface UltimaSugestaoRegistradaJpaRepository extends JpaRepository<UltimaSugestaoRegistradaJpaEntity, UUID> {

    // Leitura herdada via findById(UUID) cobre a I/O Matrix (Optional.empty()
    // quando nao ha registro para o recursoId, nunca excecao) -- nao precisa
    // de query nativa propria.

    // Upsert direto e idempotente pela PK simples recurso_id (Boundaries
    // "Always" da spec 3-3c2b1) -- mesmo estilo do upsert de
    // SugestaoRecusadaJpaRepository#upsert: o DO UPDATE sempre aplica num
    // conflito, last-write-wins simples. Evita a corrida de findById + save
    // em duas etapas.
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.ultima_sugestao_registrada "
            + "(recurso_id, paciente_id, registrado_em) "
            + "VALUES (:recursoId, :pacienteId, :registradoEm) "
            + "ON CONFLICT (recurso_id) DO UPDATE SET "
            + "paciente_id = excluded.paciente_id, "
            + "registrado_em = excluded.registrado_em",
            nativeQuery = true)
    void upsert(@Param("recursoId") UUID recursoId,
                @Param("pacienteId") long pacienteId,
                @Param("registradoEm") Instant registradoEm);
}
