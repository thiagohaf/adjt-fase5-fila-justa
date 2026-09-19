package com.filajusta.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code matching_alocacao.alocacao} (Story 3-3b1). Existe
 * só para satisfazer o parâmetro genérico de {@link AlocacaoJpaRepository}
 * ({@code JpaRepository<AlocacaoJpaEntity, UUID>}) -- a escrita real passa
 * pelo INSERT nativo ({@link AlocacaoJpaRepository#inserir}), não por
 * {@code save()} desta entidade (mesmo padrão de {@code RecursoJpaEntity}).
 */
@Entity
@Table(name = "alocacao", schema = "matching_alocacao")
public class AlocacaoJpaEntity {

    @Id
    @Column(name = "alocacao_id")
    private UUID alocacaoId;

    @Column(name = "recurso_id", nullable = false)
    private UUID recursoId;

    @Column(name = "paciente_id", nullable = false)
    private long pacienteId;

    @Column(nullable = false)
    private String status;

    @Column(name = "confirmado_em", nullable = false)
    private Instant confirmadoEm;

    protected AlocacaoJpaEntity() {
        // Exigido pelo JPA.
    }
}
