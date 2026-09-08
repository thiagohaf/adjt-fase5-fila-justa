package com.filajusta.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Cobre a unica rota publica de dominio da spec 1.2 (application.yml:
 * {@code auth-login}, {@code Path=/v1/auth/login} + {@code Method=POST} ->
 * {@code http://auth-service:8081}, sem filtro). Sem este
 * teste, um typo no path/metodo/porta/esquema da URI 404aria ou desviaria a
 * requisicao em producao com a suite inteira ainda verde -- e o unico
 * caminho externamente alcancavel ate o login.
 *
 * <p>Substitui a URI da rota (todos os campos do indice 0, via
 * {@code @DynamicPropertySource}) por um {@link HttpServer} stub local, para
 * nao depender de um auth-service real de verdade neste teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthLoginRouteTest {

    private static HttpServer stubAuthService;
    private static int stubPort;

    @BeforeAll
    static void startStubAuthService() throws IOException {
        stubAuthService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubPort = stubAuthService.getAddress().getPort();
        stubAuthService.createContext("/v1/auth/login", exchange -> {
            byte[] body = "{\"token\":\"stub-jwt-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        stubAuthService.start();
    }

    @AfterAll
    static void stopStubAuthService() {
        stubAuthService.stop(0);
    }

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        // Redefine todos os campos do indice 0 (nao so "uri") para nao
        // depender de como o Binder mescla uma fonte de propriedade dinamica
        // com a lista ja carregada do application.yml.
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "auth-login");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://localhost:" + stubPort);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/v1/auth/login");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[1]", () -> "Method=POST");
    }

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void postAuthLoginEhRoteadoParaOAuthService() {
        client().post()
                .uri("/v1/auth/login")
                .bodyValue("{\"username\":\"regulador\",\"password\":\"regulador#2026\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("stub-jwt-token");
    }

    @Test
    void getAuthLoginNaoEhRoteado() {
        // O predicado Method=POST restringe a rota -- GET no mesmo path nao
        // deve cair no auth-service (nem em nenhuma outra rota: routes so
        // tem esta, alem do health-check publico em outra porta).
        client().get()
                .uri("/v1/auth/login")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void pathNaoCorrespondenteNaoEhRoteado() {
        client().post()
                .uri("/v1/algum/outro/caminho")
                .exchange()
                .expectStatus().isNotFound();
    }
}
