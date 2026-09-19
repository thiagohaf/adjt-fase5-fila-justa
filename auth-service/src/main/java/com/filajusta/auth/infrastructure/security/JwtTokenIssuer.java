package com.filajusta.auth.infrastructure.security;

import com.filajusta.auth.application.query.TokenIssuer;
import com.filajusta.auth.domain.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Implementa a porta {@link TokenIssuer} (application/query) emitindo um
 * JWT HS256 (jjwt) com claims {@code sub} (username), {@code role}
 * (puramente informativo -- nenhum RBAC nesta fase), {@code iat} e
 * {@code exp}. Segredo compartilhado com o gateway-service via variavel de
 * ambiente/Secrets Manager (AD-14) -- auth-service so emite, nunca valida.
 */
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    JwtTokenIssuer(@Value("${filajusta.jwt.secret}") String secret,
                   @Value("${filajusta.jwt.expiration-seconds}") long expirationSeconds) {
        if (expirationSeconds <= 0) {
            // exp <= iat seria um token ja expirado (ou com intervalo
            // invalido) desde a emissao -- misconfiguracao, falha rapido.
            throw new IllegalArgumentException(
                    "filajusta.jwt.expiration-seconds deve ser positivo, recebido: " + expirationSeconds);
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    @Override
    public String emitir(Usuario usuario) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("role", usuario.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
