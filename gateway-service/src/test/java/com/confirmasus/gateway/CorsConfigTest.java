package com.confirmasus.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Preflight CORS do Swagger UI: origem permitida recebe os cabecalhos; outra origem, nao. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("confirmasus.jwt.secret", () -> "cors-config-test-secret-com-32-bytes-ou-mais");
    }

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void preflightDeOrigemPermitidaRecebeCabecalhosCors() {
        client().options().uri("/v1/agendamentos")
                .header("Origin", "http://localhost:8088")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:8088")
                .expectHeader().exists("Access-Control-Allow-Methods");
    }

    @Test
    void origemDoFrontendLocalTambemEPermitida() {
        client().options().uri("/v1/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }

    @Test
    void preflightDeOrigemNaoPermitidaNaoRecebeCabecalhosCors() {
        client().options().uri("/v1/agendamentos")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }
}
