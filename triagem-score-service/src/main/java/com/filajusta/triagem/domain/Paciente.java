package com.filajusta.triagem.domain;

import java.util.Objects;

/**
 * Paciente identificado por CPF (FR-2). Resolvido/criado de forma idempotente
 * por {@code ResolverOuCriarPaciente} (application/command): {@code id} e
 * {@code null} antes da primeira persistencia.
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
