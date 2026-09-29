package com.confirmasus.matching.domain;

import java.util.Objects;

/**
 * Paciente identificado por CPF. Resolvido/criado de forma idempotente
 * por ResolverOuCriarPaciente (application/command).
 */
public final class Paciente {

    private final Long id;
    private final Cpf cpf;

    public Paciente(Long id, Cpf cpf) {
        this.id = id;
        this.cpf = Objects.requireNonNull(cpf, "cpf");
    }

    public Long getId() {
        return id;
    }

    public Cpf getCpf() {
        return cpf;
    }
}
