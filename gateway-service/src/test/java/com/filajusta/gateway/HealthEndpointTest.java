package com.filajusta.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Cobre a AC "Health-check publico" da I/O Matrix da spec 1.1: GET
 * /actuator/health no gateway-service responde 200 sem qualquer token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

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
