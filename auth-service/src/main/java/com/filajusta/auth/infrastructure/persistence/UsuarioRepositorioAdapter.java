package com.filajusta.auth.infrastructure.persistence;

import com.filajusta.auth.application.query.UsuarioRepositorio;
import com.filajusta.auth.domain.Usuario;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter que implementa a porta {@link UsuarioRepositorio} (application/query)
 * usando o {@link UsuarioJpaRepository} (Spring Data, schema {@code auth}).
 */
@Component
class UsuarioRepositorioAdapter implements UsuarioRepositorio {

    private final UsuarioJpaRepository jpaRepository;

    UsuarioRepositorioAdapter(UsuarioJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        return jpaRepository.findByUsername(username)
                .map(entity -> new Usuario(
                        entity.getId(),
                        entity.getUsername(),
                        entity.getPasswordHash(),
                        entity.getRole()));
    }
}
