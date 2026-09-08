package com.filajusta.auth.infrastructure.web;

import com.filajusta.auth.application.query.AutenticarUsuario;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unico endpoint publico do auth-service (AD-14): {@code POST /v1/auth/login}.
 * Credenciais corretas -> {@code 200} + JWT; invalidas -> {@code 401} RFC 7807
 * (ver {@link AuthExceptionHandler}, que trata a excecao unica lancada por
 * {@link AutenticarUsuario}).
 */
@RestController
public class AuthController {

    private final AutenticarUsuario autenticarUsuario;

    public AuthController(AutenticarUsuario autenticarUsuario) {
        this.autenticarUsuario = autenticarUsuario;
    }

    @PostMapping("/v1/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String token = autenticarUsuario.autenticar(request.username(), request.password());
        return new LoginResponse(token);
    }
}
