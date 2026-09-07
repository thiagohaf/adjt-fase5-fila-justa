package com.filajusta.auth.application.query;

import com.filajusta.auth.domain.Usuario;

import java.util.Optional;

/**
 * Porta de saida para busca de {@link Usuario} por username. Implementada em
 * {@code infrastructure/persistence} (JPA, schema {@code auth}, AD-9).
 */
public interface UsuarioRepositorio {

    Optional<Usuario> buscarPorUsername(String username);
}
