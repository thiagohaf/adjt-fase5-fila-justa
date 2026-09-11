package com.filajusta.matching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre ponta a ponta {@code POST /internal/recursos} (Story 3.2b2) contra
 * um Postgres 18 real via Testcontainers (Flyway roda
 * {@code V3__create_recurso.sql} tal como sobe em produção) -- a I/O &amp;
 * Edge-Case Matrix inteira da spec: criação (201), atualização idempotente
 * preservando o mesmo {@code recursoId} (200), os cenários de {@code 400}
 * (especificidadeRank inválido/tipo errado, codigoRecurso ausente/vazio,
 * disponivel ausente, corpo malformado/ausente), e a normalização (trim)
 * de {@code codigoRecurso} que preserva a idempotência do upsert (achados
 * no code review multi-agente da Story 3.2b2).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Relay SQS (Story 3.1b) desligado -- este teste so cobre POST
// /internal/recursos, sem depender de LocalStack/SQS.
@TestPropertySource(properties = "filajusta.matching.relay.enabled=false")
class UpsertRecursoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpResponse<String> upsertRecurso(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/recursos"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> upsertRecurso(String codigoRecurso, Integer especificidadeRank,
                                                Boolean disponivel) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigoRecurso", codigoRecurso);
        body.put("especificidadeRank", especificidadeRank);
        body.put("disponivel", disponivel);
        return upsertRecurso(objectMapper.writeValueAsString(body));
    }

    @Test
    void codigoRecursoIneditoRetorna201ComRecursoIdGerado() throws Exception {
        String codigoRecurso = novoCodigoRecurso();

        HttpResponse<String> resposta = upsertRecurso(codigoRecurso, 2, true);

        assertThat(resposta.statusCode()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("codigoRecurso").asText()).isEqualTo(codigoRecurso);
        assertThat(json.get("especificidadeRank").asInt()).isEqualTo(2);
        assertThat(json.get("disponivel").asBoolean()).isTrue();
        assertThat(json.get("recursoId").asText()).isNotBlank();
        // Prova que UUID.fromString nao lanca -- e um UUID v4 valido.
        assertThat(UUID.fromString(json.get("recursoId").asText())).isNotNull();

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.recurso WHERE codigo_recurso = ?",
                Integer.class, codigoRecurso);
        assertThat(total).isEqualTo(1);
    }

    @Test
    void codigoRecursoJaCadastradoComValoresDiferentesRetorna200AtualizadoComOMesmoRecursoId() throws Exception {
        String codigoRecurso = novoCodigoRecurso();
        HttpResponse<String> primeira = upsertRecurso(codigoRecurso, 1, true);
        assertThat(primeira.statusCode()).isEqualTo(201);
        String recursoIdOriginal = objectMapper.readTree(primeira.body()).get("recursoId").asText();

        HttpResponse<String> segunda = upsertRecurso(codigoRecurso, 4, false);

        assertThat(segunda.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(segunda.body());
        assertThat(json.get("recursoId").asText()).isEqualTo(recursoIdOriginal);
        assertThat(json.get("especificidadeRank").asInt()).isEqualTo(4);
        assertThat(json.get("disponivel").asBoolean()).isFalse();

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.recurso WHERE codigo_recurso = ?",
                Integer.class, codigoRecurso);
        assertThat(total).as("upsert nunca duplica a linha").isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void especificidadeRankZeroOuNegativoRetorna400(int especificidadeRankInvalido) throws Exception {
        HttpResponse<String> resposta = upsertRecurso(novoCodigoRecurso(), especificidadeRankInvalido, true);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void especificidadeRankAusenteRetorna400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigoRecurso", novoCodigoRecurso());
        body.put("disponivel", true);

        HttpResponse<String> resposta = upsertRecurso(objectMapper.writeValueAsString(body));

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void codigoRecursoVazioOuEmBrancoRetorna400(String codigoRecursoInvalido) throws Exception {
        HttpResponse<String> resposta = upsertRecurso(codigoRecursoInvalido, 1, true);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void corpoJsonMalformadoRetorna400() throws Exception {
        HttpResponse<String> resposta = upsertRecurso("{ isso nao e json valido");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void corpoAusenteRetorna400() throws Exception {
        HttpResponse<String> resposta = upsertRecurso("");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void especificidadeRankComTipoErradoRetorna400() throws Exception {
        String codigoRecurso = novoCodigoRecurso();
        String body = """
                {"codigoRecurso": "%s", "especificidadeRank": "abc", "disponivel": true}
                """.formatted(codigoRecurso);

        HttpResponse<String> resposta = upsertRecurso(body);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void codigoRecursoComEspacosAoRedorResolveParaOMesmoRecursoQueSemEspacos() throws Exception {
        String codigoRecursoBase = novoCodigoRecurso();
        String codigoRecursoComEspacos = "  " + codigoRecursoBase + "  ";

        HttpResponse<String> primeira = upsertRecurso(codigoRecursoComEspacos, 1, true);
        assertThat(primeira.statusCode()).isEqualTo(201);
        JsonNode jsonPrimeira = objectMapper.readTree(primeira.body());
        assertThat(jsonPrimeira.get("codigoRecurso").asText()).isEqualTo(codigoRecursoBase);
        String recursoIdOriginal = jsonPrimeira.get("recursoId").asText();

        HttpResponse<String> segunda = upsertRecurso(codigoRecursoBase, 3, false);

        assertThat(segunda.statusCode())
                .as("mesmo codigoRecurso apos trim -- deve atualizar, nao criar")
                .isEqualTo(200);
        JsonNode jsonSegunda = objectMapper.readTree(segunda.body());
        assertThat(jsonSegunda.get("recursoId").asText()).isEqualTo(recursoIdOriginal);
        assertThat(jsonSegunda.get("codigoRecurso").asText()).isEqualTo(codigoRecursoBase);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.recurso WHERE codigo_recurso = ?",
                Integer.class, codigoRecursoBase);
        assertThat(total).as("trim preserva idempotencia do upsert -- nao duplica a linha").isEqualTo(1);
    }

    @Test
    void codigoRecursoAusenteRetorna400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("especificidadeRank", 1);
        body.put("disponivel", true);

        HttpResponse<String> resposta = upsertRecurso(objectMapper.writeValueAsString(body));

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void disponivelAusenteRetorna400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigoRecurso", novoCodigoRecurso());
        body.put("especificidadeRank", 1);

        HttpResponse<String> resposta = upsertRecurso(objectMapper.writeValueAsString(body));

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    // codigoRecurso proprio por teste (UNIQUE no banco) -- evita colisao
    // entre metodos de teste que rodam na mesma instancia do
    // Postgres/Spring context.
    private static String novoCodigoRecurso() {
        return "RECURSO-" + UUID.randomUUID();
    }
}
