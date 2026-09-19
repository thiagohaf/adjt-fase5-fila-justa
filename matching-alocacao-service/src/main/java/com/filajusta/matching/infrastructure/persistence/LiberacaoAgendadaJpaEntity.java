package com.filajusta.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code matching_alocacao.liberacao_agendada}
 * (V8__create_liberacao_agendada.sql, Story 3-4a1), molde de
 * {@code EventoOutboxJpaEntity}. Diferente daquela, {@code alocacaoId} é a
 * própria PK (sem {@code @GeneratedValue}) -- gerada pela aplicação
 * ({@code ConfirmarAlocacao}) antes da persistência, mesmo padrão de
 * {@code AlocacaoJpaEntity}.
 */
@Entity
@Table(name = "liberacao_agendada", schema = "matching_alocacao")
public class LiberacaoAgendadaJpaEntity {

    @Id
    @Column(name = "alocacao_id")
    private UUID alocacaoId;

    @Column(name = "recurso_id", nullable = false)
    private UUID recursoId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "delay_segundos", nullable = false)
    private int delaySegundos;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "enviado_em")
    private Instant enviadoEm;

    protected LiberacaoAgendadaJpaEntity() {
        // Exigido pelo JPA.
    }

    public LiberacaoAgendadaJpaEntity(UUID alocacaoId, UUID recursoId, String correlationId,
                                       int delaySegundos, Instant criadoEm, Instant enviadoEm) {
        this.alocacaoId = alocacaoId;
        this.recursoId = recursoId;
        this.correlationId = correlationId;
        this.delaySegundos = delaySegundos;
        this.criadoEm = criadoEm;
        this.enviadoEm = enviadoEm;
    }

    public UUID getAlocacaoId() {
        return alocacaoId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getDelaySegundos() {
        return delaySegundos;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }
}
