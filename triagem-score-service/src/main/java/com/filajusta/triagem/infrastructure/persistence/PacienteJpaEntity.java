package com.filajusta.triagem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento JPA de {@code triagem_score.pacientes} (schema proprio, AD-9).
 * {@code cpf} e unico -- rede de seguranca da idempotencia de
 * {@code ResolverOuCriarPaciente} (application/command).
 */
@Entity
@Table(name = "pacientes", schema = "triagem_score")
public class PacienteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    protected PacienteJpaEntity() {
        // Exigido pelo JPA.
    }

    public PacienteJpaEntity(String cpf) {
        this.cpf = cpf;
    }

    public Long getId() {
        return id;
    }

    public String getCpf() {
        return cpf;
    }
}
