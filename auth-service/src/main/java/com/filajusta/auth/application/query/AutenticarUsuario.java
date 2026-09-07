package com.filajusta.auth.application.query;

import com.filajusta.auth.domain.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Caso de uso de login (AD-14): verifica credenciais e emite um JWT --
 * nao muta nenhum estado de dominio, por isso vive em {@code application/query}.
 *
 * <p>Usuario inexistente e senha incorreta lancam a mesma
 * {@link CredencialInvalidaException}, garantindo que
 * {@code infrastructure/web} responda {@code 401} identico para os dois
 * casos (I/O & Edge-Case Matrix da spec 1.2).
 */
public class AutenticarUsuario {

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public AutenticarUsuario(UsuarioRepositorio usuarioRepositorio,
                              PasswordEncoder passwordEncoder,
                              TokenIssuer tokenIssuer) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    public String autenticar(String username, String senha) {
        Usuario usuario = usuarioRepositorio.buscarPorUsername(username)
                .orElseThrow(CredencialInvalidaException::new);

        if (!passwordEncoder.matches(senha, usuario.getPasswordHash())) {
            throw new CredencialInvalidaException();
        }

        return tokenIssuer.emitir(usuario);
    }
}
