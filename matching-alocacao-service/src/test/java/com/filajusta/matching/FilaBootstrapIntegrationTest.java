package com.filajusta.matching;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova o boot a frio de {@code GET /v1/fila} (Story 3.1c) contra um
 * Postgres 18 real (Testcontainers) e um {@code GET /internal/scores}
 * estubado via WireMock -- primeiro precedente de WireMock no projeto
 * (Design Notes da spec 3.1c, papel análogo ao LocalStack para AWS na
 * Story 3.0): réplica vazia dispara o bootstrap síncrono antes de
 * responder (populando a réplica via o upsert idempotente da 3.1b), e
 * falha do bootstrap (triagem-score-service indisponível/erro) nunca serve
 * uma fila incompleta silenciosamente -- vira {@code 503} RFC 7807, réplica
 * permanece vazia (I/O &amp; Edge-Case Matrix da spec 3.1c). Também cobre o
 * Patch 1 do code review: falha no meio de um lote de 2+ linhas reverte o
 * bootstrap inteiro (transação única em {@code ScoreBootstrapService}),
 * nunca deixa a réplica parcialmente populada.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Relay SQS (Story 3.1b) e relay outbox (Story 3-3a) desligados -- este
// teste so cobre o bootstrap sincrono via HTTP (Story 3.1c), sem depender
// de LocalStack/SQS/SNS.
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class FilaBootstrapIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMockServer.start();
    }

    @AfterAll
    static void pararWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void bootstrapProperties(DynamicPropertyRegistry registry) {
        registry.add("filajusta.matching.bootstrap.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void limparReplicaEStubs() {
        jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        wireMockServer.resetAll();
    }

    private HttpResponse<String> consultarFila() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/fila"))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void replicaVaziaDisparaBootstrapSincronoEPopulaAFilaAntesDeResponder() throws Exception {
        long pacienteId = 918273L;
        String corpoInternalScores = """
                [
                  {
                    "pacienteId": %d,
                    "score": {"valor": 77, "algoritmoVersao": "v1", "fatores": []},
                    "occurredAt": "2026-09-08T12:00:00Z",
                    "eventId": "11111111-1111-1111-1111-111111111111"
                  }
                ]
                """.formatted(pacienteId);

        wireMockServer.stubFor(get(urlEqualTo("/internal/scores"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(corpoInternalScores)));

        HttpResponse<String> resposta = consultarFila();

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode fila = objectMapper.readTree(resposta.body());
        assertThat(fila.isArray()).isTrue();

        JsonNode item = itemDoPaciente(fila, pacienteId);
        assertThat(item).as("pacienteId=%d presente na fila retornada", pacienteId).isNotNull();
        assertThat(item.get("score").asInt()).isEqualTo(77);
        assertThat(item.get("prioridadeEfetiva").asDouble()).isGreaterThanOrEqualTo(77.0);

        // Bootstrap upsertou de fato via o mecanismo idempotente da 3.1b
        // (nao so um retorno "de mentirinha" do controller).
        var linhas = jdbcTemplate.queryForList(
                "SELECT score, numero_sequencial_triagem FROM matching_alocacao.score_replica "
                        + "WHERE paciente_id = ?", pacienteId);
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("score")).isEqualTo(77);
        // I/O Matrix da spec 3.2b1: resposta sem numeroSequencialTriagem
        // (compatibilidade defensiva) grava null, bootstrap nao falha.
        assertThat(linhas.get(0).get("numero_sequencial_triagem")).isNull();

        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/internal/scores")));

        // Segunda consulta com a replica ja populada NAO dispara um novo
        // bootstrap (Boundaries da spec 3.1c: so dispara com a replica
        // vazia).
        HttpResponse<String> segundaResposta = consultarFila();
        assertThat(segundaResposta.statusCode()).isEqualTo(200);
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/internal/scores")));
    }

    @Test
    void respostaComNumeroSequencialTriagemPersisteOValorNaReplica() throws Exception {
        // AC da spec 3.2b1: resposta de bootstrap com numeroSequencialTriagem
        // -- ScoreBootstrapService popula a replica a frio com o valor
        // recebido, igual.
        long pacienteId = 5544332L;
        String corpoInternalScores = """
                [
                  {
                    "pacienteId": %d,
                    "score": {"valor": 65, "algoritmoVersao": "v1", "fatores": []},
                    "occurredAt": "2026-09-08T12:00:00Z",
                    "eventId": "33333333-3333-3333-3333-333333333333",
                    "numeroSequencialTriagem": 456
                  }
                ]
                """.formatted(pacienteId);

        wireMockServer.stubFor(get(urlEqualTo("/internal/scores"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(corpoInternalScores)));

        HttpResponse<String> resposta = consultarFila();

        assertThat(resposta.statusCode()).isEqualTo(200);
        var linhas = jdbcTemplate.queryForList(
                "SELECT numero_sequencial_triagem FROM matching_alocacao.score_replica WHERE paciente_id = ?",
                pacienteId);
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("numero_sequencial_triagem")).isEqualTo(456L);
    }

    @Test
    void falhaNoBootstrapRetorna503EReplicaPermaneceVazia() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/internal/scores"))
                .willReturn(aResponse().withStatus(500)));

        HttpResponse<String> resposta = consultarFila();

        assertThat(resposta.statusCode()).isEqualTo(503);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        Integer totalLinhas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.score_replica", Integer.class);
        assertThat(totalLinhas).isZero();
    }

    @Test
    void falhaNoMeioDoLoteReverteOBootstrapInteiroEReplicaContinuaVazia() throws Exception {
        // Achado do code review (Patch 1): sem transacao em volta do loop
        // inteiro de ScoreBootstrapService#bootstrapar, a 1a linha (valida)
        // ficava commitada mesmo com a 2a linha (score fora de 0..100,
        // rejeitada por ScoreReplica) falhando -- a replica deixava de
        // estar vazia e todo GET /v1/fila seguinte pulava o bootstrap para
        // sempre, servindo uma fila permanentemente incompleta. Com o fix,
        // a falha na 2a linha reverte a 1a tambem -- count() == 0 depois.
        long pacienteIdValido = 111222L;
        long pacienteIdInvalido = 333444L;
        String corpoInternalScores = """
                [
                  {
                    "pacienteId": %d,
                    "score": {"valor": 80, "algoritmoVersao": "v1", "fatores": []},
                    "occurredAt": "2026-09-08T12:00:00Z",
                    "eventId": "11111111-1111-1111-1111-111111111111"
                  },
                  {
                    "pacienteId": %d,
                    "score": {"valor": 150, "algoritmoVersao": "v1", "fatores": []},
                    "occurredAt": "2026-09-08T12:00:00Z",
                    "eventId": "22222222-2222-2222-2222-222222222222"
                  }
                ]
                """.formatted(pacienteIdValido, pacienteIdInvalido);

        wireMockServer.stubFor(get(urlEqualTo("/internal/scores"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(corpoInternalScores)));

        HttpResponse<String> resposta = consultarFila();

        // Bug de mapeamento/validacao (score invalido), nao "servico
        // externo fora do ar" -- Patch 3: propaga como 500 generico, nunca
        // 503 (que e reservado para falha da chamada HTTP em si).
        assertThat(resposta.statusCode()).isEqualTo(500);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        Integer totalLinhas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.score_replica", Integer.class);
        assertThat(totalLinhas)
                .as("replica deve continuar vazia -- a linha valida (pacienteId=%d) NAO pode ter ficado "
                        + "commitada isoladamente quando a linha seguinte (pacienteId=%d) falhou",
                        pacienteIdValido, pacienteIdInvalido)
                .isZero();
    }

    private static JsonNode itemDoPaciente(JsonNode fila, long pacienteId) {
        for (JsonNode item : fila) {
            if (item.get("pacienteId").asLong() == pacienteId) {
                return item;
            }
        }
        return null;
    }
}
