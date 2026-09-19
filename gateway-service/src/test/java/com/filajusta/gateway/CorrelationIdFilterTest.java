package com.filajusta.gateway;

import com.filajusta.gateway.infrastructure.web.CorrelationIdFilter;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a I/O &amp; Edge-Case Matrix completa da spec correlationId-gateway
 * para o {@code CorrelationIdFilter}: sem header (UUID novo gerado), com
 * header (mesmo valor propagado), header em branco ou invalido (tratado
 * como ausente), multiplos valores do mesmo header (usa o primeiro,
 * deterministico), propagacao para o servico downstream via stub, rota
 * publica real ({@code /v1/auth/login}) e presenca no {@code 401} do
 * {@link com.filajusta.gateway.infrastructure.security.JwtAuthenticationFilter}.
 *
 * <p>Mesmo padrao de stub HTTP local ({@link HttpServer}) +
 * {@code @DynamicPropertySource} de {@link JwtAuthenticationFilterTest},
 * mas o stub tambem ecoa o header {@code X-Correlation-Id} recebido no
 * corpo da resposta, para permitir assertar a propagacao ao downstream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationIdFilterTest {

    private static final String HEADER = CorrelationIdFilter.CORRELATION_ID_HEADER;

    private static final String JWT_TEST_SECRET =
            "correlation-id-filter-test-secret-com-32-bytes-ou-mais";
    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(JWT_TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    private static HttpServer stubProtectedService;
    private static int stubPort;

    private static HttpServer stubAuthLoginService;
    private static int stubAuthLoginPort;

    @BeforeAll
    static void startStubProtectedService() throws IOException {
        stubProtectedService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubPort = stubProtectedService.getAddress().getPort();
        stubProtectedService.createContext("/v1/dominio/protegido", exchange -> {
            String correlationIdRecebido = exchange.getRequestHeaders().getFirst(HEADER);
            String corpo = "{\"correlationIdRecebido\":"
                    + (correlationIdRecebido == null ? "null" : "\"" + correlationIdRecebido + "\"")
                    + "}";
            byte[] body = corpo.getBytes(StandardCharsets.UTF_8);
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

    // Stub para a rota publica real (/v1/auth/login, mesmo padrao de
    // AuthLoginRouteTest) -- AC1 da spec cobre "rota publica ou protegida",
    // e o CorrelationIdFilter roda para qualquer rota do RouteLocator, nao
    // so as protegidas.
    @BeforeAll
    static void startStubAuthLoginService() throws IOException {
        stubAuthLoginService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubAuthLoginPort = stubAuthLoginService.getAddress().getPort();
        stubAuthLoginService.createContext("/v1/auth/login", exchange -> {
            byte[] body = "{\"token\":\"stub-jwt-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        stubAuthLoginService.start();
    }

    @AfterAll
    static void stopStubAuthLoginService() {
        stubAuthLoginService.stop(0);
    }

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("filajusta.jwt.secret", () -> JWT_TEST_SECRET);

        // Redefine todos os campos do indice 0 (mesmo padrao de
        // JwtAuthenticationFilterTest) para um path fora da allowlist
        // publica -- simula um endpoint de dominio protegido generico.
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "dominio-protegido");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://localhost:" + stubPort);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/v1/dominio/protegido");

        // Segunda rota (indice 1), mesmo shape da rota publica real de
        // application.yml (auth-login), mas apontando para o stub local.
        registry.add("spring.cloud.gateway.server.webflux.routes[1].id", () -> "auth-login");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].uri",
                () -> "http://localhost:" + stubAuthLoginPort);
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[0]",
                () -> "Path=/v1/auth/login");
        registry.add("spring.cloud.gateway.server.webflux.routes[1].predicates[1]", () -> "Method=POST");
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

    @Test
    void semHeader_geraUuidNovoPresenteNaRespostaEEfetivamentePropagadoAoDownstream() {
        EntityExchangeResult<byte[]> resultado = client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtValido())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HEADER)
                .expectBody()
                .returnResult();

        String correlationIdNaResposta = resultado.getResponseHeaders().getFirst(HEADER);
        assertThat(correlationIdNaResposta).isNotBlank();
        assertThat(UUID.fromString(correlationIdNaResposta)).isNotNull();

        // Nao basta a chave existir no corpo (o stub sempre a emite, com
        // "null" quando nenhum header chega) -- precisa ser exatamente o
        // mesmo valor gerado e devolvido na resposta HTTP, provando que o
        // downstream de fato recebeu o header forwardado.
        String corpo = new String(resultado.getResponseBodyContent(), StandardCharsets.UTF_8);
        String correlationIdEcoadoPeloDownstream = JsonPath.read(corpo, "$.correlationIdRecebido");
        assertThat(correlationIdEcoadoPeloDownstream)
                .isNotBlank()
                .isEqualTo(correlationIdNaResposta);
    }

    @Test
    void comHeader_mesmoValorPropagadoParaRespostaEDownstream() {
        String correlationIdEnviado = "abc-123";

        client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtValido())
                .header(HEADER, correlationIdEnviado)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, correlationIdEnviado)
                .expectBody()
                .jsonPath("$.correlationIdRecebido").isEqualTo(correlationIdEnviado);
    }

    @Test
    void multiplosValoresNoHeader_usaOPrimeiroDeFormaDeterministica() {
        // Boundaries da spec: "se o cliente mandar multiplos valores, usar/
        // gerar um so" -- HttpHeaders#getFirst ja garante isso no filtro,
        // este teste apenas prova o comportamento fim-a-fim.
        String primeiroValor = "v1";
        String segundoValor = "v2";

        client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtValido())
                .header(HEADER, primeiroValor, segundoValor)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, primeiroValor)
                .expectBody()
                .jsonPath("$.correlationIdRecebido").isEqualTo(primeiroValor);
    }

    @Test
    void headerEmBranco_tratadoComoAusente_geraNovoUuid() {
        String correlationIdNaResposta = client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtValido())
                .header(HEADER, "")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HEADER)
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(HEADER);

        assertThat(correlationIdNaResposta).isNotBlank();
        assertThat(UUID.fromString(correlationIdNaResposta)).isNotNull();
    }

    @Test
    void respostaDeErro401DoFiltroJwtCarregaCorrelationId() {
        // Sem JWT valido -- JwtAuthenticationFilter responde 401 direto no
        // exchange (getOrder() maior/menos precedente que
        // CorrelationIdFilter), mas a resposta ainda deve carregar o mesmo
        // X-Correlation-Id enviado na requisicao.
        String correlationIdEnviado = "erro-401-correlation-id";

        client().get()
                .uri("/v1/dominio/protegido")
                .header(HEADER, correlationIdEnviado)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HEADER, correlationIdEnviado);
    }

    @Test
    void respostaDeErro401SemHeaderEnviadoAindaAssimCarregaUmCorrelationId() {
        String correlationIdNaResposta = client().get()
                .uri("/v1/dominio/protegido")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(HEADER)
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(HEADER);

        assertThat(correlationIdNaResposta).isNotBlank();
        assertThat(UUID.fromString(correlationIdNaResposta)).isNotNull();
    }

    @Test
    void rotaPublicaAuthLogin_tambemRecebeCorrelationId() {
        // AC1 da spec: "rota publica ou protegida". Diferente dos demais
        // testes desta classe (rota protegida "dominio-protegido"), este
        // exercita a rota publica real /v1/auth/login (allowlist do
        // JwtAuthenticationFilter -- sem Bearer token), que passa pelo
        // mesmo RouteLocator e, portanto, pelos mesmos GlobalFilters,
        // incluindo o CorrelationIdFilter. /actuator/health nao precisa de
        // teste equivalente: nunca passa por nenhum GlobalFilter (mesma
        // inercia arquitetural documentada no javadoc de
        // JwtAuthenticationFilter para aquele path).
        String correlationIdEnviado = "public-route-correlation-id";

        client().post()
                .uri("/v1/auth/login")
                .header(HEADER, correlationIdEnviado)
                .bodyValue("{\"username\":\"regulador\",\"password\":\"regulador#2026\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, correlationIdEnviado)
                .expectBody()
                .jsonPath("$.token").isEqualTo("stub-jwt-token");
    }

    @Test
    void headerComTamanhoAcimaDoLimite_tratadoComoInvalido_geraNovoUuidSemErro() {
        // Guard contra header malformado/oversized (patch de revisao
        // adversarial): valor com mais de 128 caracteres (MAX_LENGTH do
        // CorrelationIdFilter) e ASCII valido, mas tratado como invalido --
        // fallback igual ao caso ausente, sem 500.
        String valorOversized = "a".repeat(200);

        String correlationIdNaResposta = client().get()
                .uri("/v1/dominio/protegido")
                .header("Authorization", "Bearer " + jwtValido())
                .header(HEADER, valorOversized)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HEADER)
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(HEADER);

        assertThat(correlationIdNaResposta)
                .isNotBlank()
                .isNotEqualTo(valorOversized);
        assertThat(UUID.fromString(correlationIdNaResposta)).isNotNull();
    }

    @Test
    void headerComCaracteresDeControle_tratadoComoInvalido_geraNovoUuidSemErro() {
        // CR/LF ("\r\n") num header value real e rejeitado pelo proprio
        // cliente HTTP (reactor-netty) antes de sair de processo -- nao da
        // para exercitar esse caso via WebTestClient fim-a-fim. Testa o
        // CorrelationIdFilter isoladamente (unit-level), construindo o
        // ServerWebExchange via MockServerHttpRequest, que aceita o valor
        // como String Java pura, sem validacao de wire-format -- prova que
        // o proprio filtro (nao o transporte) trata o valor como invalido.
        CorrelationIdFilter filtro = new CorrelationIdFilter();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/dominio/protegido")
                        .header(HEADER, "valor\r\nmalicioso")
                        .build());

        // O filtro nao muta o `exchange` recebido -- ele constroi um novo
        // (`exchange.mutate().request(...).build()`) e o passa adiante para
        // `chain.filter(...)`; e nesse novo exchange (capturado aqui) que a
        // requisicao forwardada com o header corrigido existe.
        ServerWebExchange[] exchangeRecebidoPelaChain = new ServerWebExchange[1];
        StepVerifier.create(filtro.filter(exchange, ex -> {
                    exchangeRecebidoPelaChain[0] = ex;
                    return Mono.empty();
                }))
                .verifyComplete();

        String correlationIdGerado = exchange.getResponse().getHeaders().getFirst(HEADER);
        assertThat(correlationIdGerado).isNotBlank();
        assertThat(correlationIdGerado).doesNotContain("\r", "\n");
        assertThat(UUID.fromString(correlationIdGerado)).isNotNull();

        String correlationIdNaRequisicaoForwardada =
                exchangeRecebidoPelaChain[0].getRequest().getHeaders().getFirst(HEADER);
        assertThat(correlationIdNaRequisicaoForwardada).isEqualTo(correlationIdGerado);
    }
}
