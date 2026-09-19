package com.filajusta.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code matching_alocacao.ultima_sugestao_registrada}
 * (Story 3-3c2b1). Existe só para satisfazer o parâmetro genérico de {@link
 * UltimaSugestaoRegistradaJpaRepository} ({@code
 * JpaRepository<UltimaSugestaoRegistradaJpaEntity, UUID>}) e a leitura via
 * {@code findById} herdado -- a escrita real passa pelo upsert nativo
 * ({@link UltimaSugestaoRegistradaJpaRepository#upsert}), não por {@code
 * save()} desta entidade (mesmo padrão de {@code SugestaoRecusadaJpaEntity}).
 *
 * <p>PK simples {@code recurso_id} (sem {@code @EmbeddedId} -- ao contrário
 * de {@code SugestaoRecusadaJpaEntity}, que tem PK composta {@code
 * (recurso_id, paciente_id)}): só existe UMA última sugestão registrada por
 * Recurso.
 */
@Entity
@Table(name = "ultima_sugestao_registrada", schema = "matching_alocacao")
public class UltimaSugestaoRegistradaJpaEntity {

    @Id
    @Column(name = "recurso_id")
    private UUID recursoId;

    @Column(name = "paciente_id", nullable = false)
    private long pacienteId;

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    protected UltimaSugestaoRegistradaJpaEntity() {
        // Exigido pelo JPA.
    }

    public long getPacienteId() {
        return pacienteId;
    }

    public Instant getRegistradoEm() {
        return registradoEm;
    }
}
