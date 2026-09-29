package com.confirmasus.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de lista_espera_entrada (Story 5.3).
 */
@Entity
@Table(
    name = "lista_espera_entrada",
    schema = "matching_alocacao",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_paciente_recurso",
        columnNames = {"paciente_id", "recurso_id"}
    )
)
public class ListaEsperaEntradaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entrada_id")
    private Long entradaId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "recurso_id", nullable = false)
    private UUID recursoId;

    @Column(name = "data_solicitacao", nullable = false)
    private Instant dataSolicitacao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected ListaEsperaEntradaJpaEntity() {
        // Exigido pelo JPA
    }

    public ListaEsperaEntradaJpaEntity(Long pacienteId, UUID recursoId, Instant dataSolicitacao, Instant criadoEm) {
        this.pacienteId = pacienteId;
        this.recursoId = recursoId;
        this.dataSolicitacao = dataSolicitacao;
        this.criadoEm = criadoEm;
    }

    public Long getEntradaId() {
        return entradaId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public Instant getDataSolicitacao() {
        return dataSolicitacao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
