package com.filajusta.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Cobre a AC "Health-check publico" da I/O Matrix da spec 1.1/1.2: GET
 * /actuator/health no gateway-service responde 200 sem qualquer token
 * (allowlist publica do {@code JwtAuthenticationFilter}, Story 1.2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        // filajusta.jwt.secret nao tem default (NFR-6, so env var/Secrets
        // Manager) -- o bean JwtAuthenticationFilter e criado eagerly no
        // boot, mesmo este teste so exercitando a rota publica.
        registry.add("filajusta.jwt.secret", () -> "health-endpoint-test-secret-com-32-bytes-ou-mais");
    }

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointIsPublicAndReturns200() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
