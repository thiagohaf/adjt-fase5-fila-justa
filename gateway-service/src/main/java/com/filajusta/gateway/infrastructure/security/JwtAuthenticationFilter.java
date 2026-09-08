package com.filajusta.gateway.infrastructure.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * {@link GlobalFilter} ({@link Ordered}) -- unico ponto de validacao de JWT
 * do sistema (AD-8). Valida assinatura/expiracao (jjwt HS256, mesmo segredo
 * do {@code auth-service}, AD-14) em toda rota do {@code RouteLocator} do
 * Gateway, exceto {@code /v1/auth/login} (allowlist publica hardcoded --
 * Design Notes da spec 1.2, sem config externa para excecao tao pequena).
 * {@code /actuator/health} tambem consta na allowlist por completude de
 * intencao, mas e inerte na pratica: {@code GlobalFilter}s so rodam para
 * paths que dao match em uma rota do Gateway, e o health-check e servido
 * direto pelo autoconfig do Spring Boot Actuator (mesma porta, fora do
 * {@code RouteLocator}) -- este filtro nunca chega a ser invocado para
 * aquele path.
 *
 * <p>Em falha (token ausente, assinatura invalida ou expirado) escreve
 * {@code 401} RFC 7807 direto no {@link ServerWebExchange}
 * ({@code setStatusCode} + {@link DataBuffer} com JSON), em vez de
 * {@code @RestControllerAdvice}: o WebFlux Gateway nao passa proxy por
 * dispatch de controller como o MVC do {@code auth-service} (Design Notes).
 * Mensagem identica para os 3 casos -- nao vaza qual ocorreu (Boundaries).
 *
 * <p>O claim {@code role} nao e lido nem aplicado aqui -- RBAC por papel e
 * Non-Goal desta spec.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Set<String> ALLOWLIST_PUBLICA = Set.of(
            "/actuator/health",
            "/v1/auth/login");

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String MENSAGEM_ERRO_GENERICA =
            "Token ausente, expirado ou com assinatura invalida";

    // HS256 exige >= 256 bits (32 bytes) de segredo -- sem esta checagem, um
    // FILAJUSTA_JWT_SECRET vazio ou curto so falharia no boot com uma
    // WeakKeyException opaca do jjwt. Mesmo espirito do guard de
    // expiration-seconds em auth-service/.../JwtTokenIssuer.java.
    private static final int HS256_MIN_SECRET_BYTES = 32;

    // Folga de clock-skew entre auth-service e gateway-service (containers
    // ECS separados, sem garantia de relogio perfeitamente sincronizado) --
    // sem isso, um token ainda valido mas perto do limite de expiracao pode
    // ser rejeitado por drift entre os dois hosts.
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 30;

    // Corpo RFC 7807 fixo (sem dado dinamico/interpolado -- mensagem generica
    // identica para os 3 casos, Boundaries da spec), pre-calculado uma unica
    // vez. Escrito direto no exchange (Design Notes): o WebFlux Gateway nao
    // passa proxy por dispatch de controller, entao nao ha conversor Jackson
    // de resposta disponivel aqui como no @RestControllerAdvice do
    // auth-service -- e o unico ObjectMapper elegivel para DI (Spring Boot
    // 4.1 -- tools.jackson) nao vale a pena para um payload estatico.
    private static final byte[] CORPO_401 = ("{\"type\":\"about:blank\","
            + "\"title\":\"Nao autorizado\","
            + "\"status\":401,"
            + "\"detail\":\"" + MENSAGEM_ERRO_GENERICA + "\"}")
            .getBytes(StandardCharsets.UTF_8);

    private final SecretKey signingKey;
    private final JwtParser jwtParser;

    JwtAuthenticationFilter(@Value("${filajusta.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < HS256_MIN_SECRET_BYTES) {
            // Falha rapido com mensagem clara -- sem isso, Keys.hmacShaKeyFor
            // lanca WeakKeyException opaca (nao diz qual property configurar).
            throw new IllegalArgumentException(
                    "filajusta.jwt.secret deve ter pelo menos " + HS256_MIN_SECRET_BYTES
                            + " bytes (HS256), recebido: "
                            + (secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length));
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .clockSkewSeconds(CLOCK_SKEW_TOLERANCE_SECONDS)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = normalizado(request.getPath().value());
        if (ALLOWLIST_PUBLICA.contains(path)) {
            return chain.filter(exchange);
        }

        String authorizationHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        try {
            jwtParser.parseSignedClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            // Cobre assinatura invalida, token expirado e token malformado --
            // mesma resposta generica para os 3 (Boundaries: nao vaza qual caso ocorreu).
            return unauthorized(exchange);
        }

        return chain.filter(exchange);
    }

    // Remove uma unica barra final (nao afeta case-sensitivity -- paths HTTP
    // continuam case-sensitive) para que "/actuator/health/" bata com a
    // allowlist tanto quanto "/actuator/health".
    private static String normalizado(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    public int getOrder() {
        // Alta precedencia, mas com folga acima de HIGHEST_PRECEDENCE
        // (Integer.MIN_VALUE) -- um futuro filtro de correlationId
        // (deferred-work.md) precisa rodar ANTES deste, o que exige um
        // valor menor ainda disponivel.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.valueOf("application/problem+json"));
        // RFC 7235 -- desafio de esquema Bearer esperado em toda resposta 401.
        response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        DataBuffer buffer = response.bufferFactory().wrap(CORPO_401);
        return response.writeWith(Mono.just(buffer));
    }
}
