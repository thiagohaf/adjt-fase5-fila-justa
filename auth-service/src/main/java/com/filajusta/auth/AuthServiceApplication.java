package com.filajusta.auth;

import com.filajusta.auth.application.query.AutenticarUsuario;
import com.filajusta.auth.application.query.TokenIssuer;
import com.filajusta.auth.application.query.UsuarioRepositorio;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Ponto de entrada do auth-service.
 *
 * <p>Story 1.1: esqueleto minimo, so {@code GET /actuator/health} (AD-8/AD-12).
 * Story 1.2 acrescenta {@code POST /v1/auth/login} (AD-14): esta classe e a
 * raiz de composicao que conecta as portas de {@code application.query}
 * (framework-agnosticas por design) aos adapters de {@code infrastructure}
 * -- {@link AutenticarUsuario} nao carrega nenhuma anotacao Spring.
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AutenticarUsuario autenticarUsuario(UsuarioRepositorio usuarioRepositorio,
                                         PasswordEncoder passwordEncoder,
                                         TokenIssuer tokenIssuer) {
        return new AutenticarUsuario(usuarioRepositorio, passwordEncoder, tokenIssuer);
    }
}
