package com.filajusta.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Mapeamento JPA de {@code matching_alocacao.recurso} (Story 3.2b2). Só
 * usado para leitura ({@link RecursoJpaRepository#findByCodigoRecurso}) --
 * a escrita de produção passa pelo upsert nativo ({@code
 * RecursoJpaRepository#upsert}), não por {@code save()} desta entidade
 * (mesmo padrão de {@code ScoreReplicaJpaEntity}).
 */
@Entity
@Table(name = "recurso", schema = "matching_alocacao")
public class RecursoJpaEntity {

    @Id
    @Column(name = "recurso_id")
    private UUID recursoId;

    @Column(name = "codigo_recurso", nullable = false, unique = true)
    private String codigoRecurso;

    @Column(name = "especificidade_rank", nullable = false)
    private int especificidadeRank;

    @Column(nullable = false)
    private boolean disponivel;

    protected RecursoJpaEntity() {
        // Exigido pelo JPA.
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public String getCodigoRecurso() {
        return codigoRecurso;
    }

    public int getEspecificidadeRank() {
        return especificidadeRank;
    }

    public boolean isDisponivel() {
        return disponivel;
    }
}
