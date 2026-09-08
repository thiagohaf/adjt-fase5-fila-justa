package com.filajusta.gateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a I/O &amp; Edge-Case Matrix completa da spec 1.2 para o
 * {@code JwtAuthenticationFilter}: JWT valido encaminha normalmente; sem
 * token, token expirado ou assinatura invalida -&gt; {@code 401} RFC 7807
 * com mensagem identica (Boundaries -- nao vaza qual caso ocorreu). As
 * rotas publicas ({@code /actuator/health}, {@code /v1/auth/login}) sao
 * cobertas em separado por {@link HealthEndpointTest} e
 * {@link AuthLoginRouteTest}.
 *
 * <p>Ainda nao existe endpoint de dominio real (Epic 2+) -- redefine a
 * rota de indice 0 para um {@link HttpServer} stub local representando um
 * endpoint protegido generico, mesmo padrao de {@link AuthLoginRouteTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtAuthenticationFilterTest {

    private static final String JWT_TEST_SECRET =
            "jwt-authentication-filter-test-secret-com-32-bytes-ou-mais";
    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(JWT_TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    private static HttpServer stubProtectedService;
    private static int stubPort;

    @BeforeAll
    static void startStubProtectedService() throws IOException {
        stubProtectedService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubPort = stubProtectedService.getAddress().getPort();
        stubProtectedService.createContext("/v1/dominio/protegido", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        stubProtectedService.start();
    }

    @AfterAll
    static void stopStubProtectedService() {
        stubProtectedService.stop(0);
    }

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("filajusta.jwt.secret", () -> JWT_TEST_SECRET);

        // Redefine todos os campos do indice 0 (mesmo padrao de
        // AuthLoginRouteTest) para um path fora da allowlist publica --
        // simula um endpoint de dominio protegido generico.
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "dominio-protegido");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://localhost:" + stubPort);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/v1/dominio/protegido");
    }

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String jwtValido() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("regulador")
                .claim("role", "REGULADOR")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(SIGNING_KEY, Jwts.SIG.HS256)
                .compact();
    }

    private String jwtExpirado() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("regulador")
                .claim("role", "REGULADOR")
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(3600)))
                .signWith(SIGNING_KEY, Jwts.SIG.HS256)
                .compact();
    }

    private String jwtAssinaturaInvalida() {
        SecretKey outraChave = Keys.hmacShaKeyFor(
                "um-segredo-completamente-diferente-com-32-bytes-min".getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("regulador")
                .claim("role", "REGULADOR")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(outraChave, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void jwtValidoEhEncaminhadoNormalmente() {
        client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtValido())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ok").isEqualTo(true);
    }

    @Test
    void semTokenRetorna401RfC7807() {
        client().get()
                .uri("/v1/dominio/protegido")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/problem+json");
    }

    @Test
    void tokenExpiradoRetorna401RfC7807() {
        client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtExpirado())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/problem+json");
    }

    @Test
    void assinaturaInvalidaRetorna401RfC7807() {
        client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtAssinaturaInvalida())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/problem+json");
    }

    @Test
    void tokenMalformadoRetorna401RfC7807() {
        // Nao e um JWT de verdade (string arbitraria, sem os 3 segmentos
        // Base64url separados por ".") -- exercita o ramo IllegalArgumentException
        // do catch em JwtAuthenticationFilter.filter, distinto de assinatura
        // invalida/expirado (JwtException), mas com a mesma resposta generica.
        client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer token-garbage-nao-e-um-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/problem+json");
    }

    @Test
    void headerAuthorizationSemBearerRetorna401RfC7807() {
        client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", jwtValido())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/problem+json");
    }

    @Test
    void mensagemDeErroEhIdenticaParaOsTresCasos() {
        // Boundaries da spec 1.2: mensagem generica identica para
        // ausente/expirado/assinatura invalida -- nao vaza qual caso ocorreu.
        String semToken = corpoDaResposta401(null);
        String expirado = corpoDaResposta401("Bearer " + jwtExpirado());
        String assinaturaInvalida = corpoDaResposta401("Bearer " + jwtAssinaturaInvalida());

        assertThat(semToken).isEqualTo(expirado);
        assertThat(expirado).isEqualTo(assinaturaInvalida);
    }

    private String corpoDaResposta401(String authorizationHeader) {
        WebTestClient.RequestHeadersSpec<?> request = client().get().uri("/v1/dominio/protegido");
        if (authorizationHeader != null) {
            request = request.header("Authorization", authorizationHeader);
        }
        return request.exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
