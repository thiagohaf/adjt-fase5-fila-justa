package com.confirmasus.matching.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento JPA de paciente (para Story 5.3 Lista de Espera).
 */
@Entity
@Table(name = "paciente", schema = "matching_alocacao")
public class PacienteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    protected PacienteJpaEntity() {
        // Exigido pelo JPA
    }

    public PacienteJpaEntity(String cpf) {
        this.cpf = cpf;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getCpf() {
        return cpf;
    }
}
