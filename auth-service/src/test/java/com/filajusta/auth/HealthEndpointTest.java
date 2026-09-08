package com.filajusta.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o esqueleto seed do auth-service (spec 1.1, Tasks & Acceptance):
 * GET /actuator/health responde 200 na porta de management. Usa
 * {@link HttpClient} puro (JDK) em vez de TestRestTemplate para nao
 * arrastar os modulos de rest-client de teste do Spring Boot 4.1.
 *
 * <p>Desde a Story 1.2 o contexto completo exige um Postgres real (JPA +
 * Flyway, schema {@code auth}) -- {@code @ServiceConnection} substitui
 * {@code spring.datasource.*} do application.yml (que aponta para o DNS
 * interno de producao) pelo container Testcontainers.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        // filajusta.jwt.secret nao tem default (NFR-6, so env var/Secrets
        // Manager) -- o bean JwtTokenIssuer e criado eagerly no boot, mesmo
        // este teste nao chamando /v1/auth/login.
        registry.add("filajusta.jwt.secret", () -> "health-endpoint-test-secret-com-32-bytes-ou-mais");
    }

    @LocalManagementPort
    private int managementPort;

    @Test
    void healthEndpointReturns200() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + managementPort + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
