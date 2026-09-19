package com.filajusta.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code matching_alocacao.sugestao_recusada} (Story
 * 3-3c1). Existe só para satisfazer o parâmetro genérico de
 * {@link SugestaoRecusadaJpaRepository} ({@code
 * JpaRepository<SugestaoRecusadaJpaEntity, SugestaoRecusadaJpaEntity.Id>})
 * -- a escrita real passa pelo upsert nativo ({@link
 * SugestaoRecusadaJpaRepository#upsert}), não por {@code save()} desta
 * entidade (mesmo padrão de {@code AlocacaoJpaEntity}/{@code
 * RecursoJpaEntity}).
 *
 * <p>PK composta {@code (recurso_id, paciente_id)} -- sem coluna sintética
 * (ao contrário de {@code AlocacaoJpaEntity}, cuja PK é {@code
 * alocacao_id}), então precisa de {@link Id} embutida via {@link
 * EmbeddedId}.
 */
@Entity
@Table(name = "sugestao_recusada", schema = "matching_alocacao")
public class SugestaoRecusadaJpaEntity {

    @EmbeddedId
    private Id id;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "recusado_em", nullable = false)
    private Instant recusadoEm;

    protected SugestaoRecusadaJpaEntity() {
        // Exigido pelo JPA.
    }

    public static final class Id implements Serializable {

        @Column(name = "recurso_id")
        private UUID recursoId;

        @Column(name = "paciente_id")
        private long pacienteId;

        protected Id() {
            // Exigido pelo JPA.
        }

        @Override
        public boolean equals(Object outro) {
            if (this == outro) {
                return true;
            }
            if (!(outro instanceof Id id)) {
                return false;
            }
            return pacienteId == id.pacienteId && Objects.equals(recursoId, id.recursoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recursoId, pacienteId);
        }
    }
}
