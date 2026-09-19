package com.filajusta.auth.domain;

import java.util.Objects;

/**
 * Usuario sintetico pre-cadastrado do auth-service (AD-14). Agregado puro,
 * sem dependencia de framework (AD-2) -- persistencia (JPA) e a
 * representacao usada para emitir o JWT vivem em infrastructure/.
 */
public final class Usuario {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String role;

    public Usuario(Long id, String username, String passwordHash, String role) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }
}
