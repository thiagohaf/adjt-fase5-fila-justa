package com.filajusta.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento JPA de {@code auth.usuarios} (schema proprio do auth-service,
 * AD-9). Populada via migration Flyway com usuarios sinteticos
 * pre-cadastrados (AD-14) -- nao ha CRUD de usuario via API (fora de
 * escopo, ver Boundaries da spec 1.2).
 */
@Entity
@Table(name = "usuarios", schema = "auth")
public class UsuarioJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    protected UsuarioJpaEntity() {
        // Exigido pelo JPA.
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
