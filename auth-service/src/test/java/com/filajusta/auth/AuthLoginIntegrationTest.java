package com.filajusta.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a I/O & Edge-Case Matrix da spec 1.2: login valido -> {@code 200} +
 * JWT (claims {@code sub}/{@code role}/{@code iat}/{@code exp}); senha
 * errada e usuario inexistente -> {@code 401} identico. Roda contra um
 * Postgres 18 real via Testcontainers -- exercita a migration Flyway com os
 * 3 usuarios sinteticos (regulador/triagem/auditor) tal como sobe em
 * producao.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthLoginIntegrationTest {

    private static final String JWT_TEST_SECRET = "auth-service-integration-test-secret-min-32-bytes!!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("filajusta.jwt.secret", () -> JWT_TEST_SECRET);
        registry.add("filajusta.jwt.expiration-seconds", () -> "3600");
    }

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpResponse<String> login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(username, password));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Os 3 usuarios sinteticos da migration (V1__create_auth_schema_and_users.sql)
    // -- sem cobrir os 3, um typo no hash BCrypt de triagem/auditor passaria
    // despercebido (so regulador era exercitado antes).
    @ParameterizedTest(name = "{0} loga com 200 + JWT com role={2}")
    @CsvSource({
            "regulador, regulador#2026, REGULADOR",
            "triagem,   triagem#2026,   TRIAGEM",
            "auditor,   auditor#2026,   AUDITOR"
    })
    void loginValidoRetorna200ComJwtAssinado(String username, String password, String expectedRole) throws Exception {
        HttpResponse<String> response = login(username, password);

        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(response.body());
        String token = json.get("token").asText();
        assertThat(token.split("\\.")).hasSize(3);

        SecretKey key = Keys.hmacShaKeyFor(JWT_TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.get("role", String.class)).isEqualTo(expectedRole);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void camposAusentesRetorna400RfC7807() throws Exception {
        // LoginRequest.@NotBlank -- username/password ausentes sao
        // requisicao invalida (400), nao credencial invalida (401).
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void senhaErradaRetorna401RfC7807() throws Exception {
        HttpResponse<String> response = login("regulador", "senha-incorreta");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void usuarioInexistenteRetorna401IdenticoASenhaErrada() throws Exception {
        HttpResponse<String> senhaErrada = login("regulador", "senha-incorreta");
        HttpResponse<String> usuarioInexistente = login("nao-existe", "qualquer-coisa");

        assertThat(usuarioInexistente.statusCode()).isEqualTo(senhaErrada.statusCode());
        assertThat(usuarioInexistente.statusCode()).isEqualTo(401);

        // Corpo identico (mesmo "detail"/"title") -- nao vaza qual dos dois
        // casos ocorreu (Boundaries da spec 1.2).
        JsonNode corpoSenhaErrada = objectMapper.readTree(senhaErrada.body());
        JsonNode corpoUsuarioInexistente = objectMapper.readTree(usuarioInexistente.body());
        assertThat(corpoUsuarioInexistente.get("detail")).isEqualTo(corpoSenhaErrada.get("detail"));
        assertThat(corpoUsuarioInexistente.get("title")).isEqualTo(corpoSenhaErrada.get("title"));
    }

    private record LoginPayload(String username, String password) {
    }
}
