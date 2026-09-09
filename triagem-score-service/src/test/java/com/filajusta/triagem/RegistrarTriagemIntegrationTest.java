package com.filajusta.triagem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre ponta a ponta os 6 cenarios da I/O & Edge-Case Matrix da spec 2.1,
 * contra um Postgres 18 real via Testcontainers (Flyway roda a migration
 * V1__create_triagem_schema.sql tal como sobe em producao):
 *
 * <ol>
 *   <li>Happy path, CPF novo -&gt; 201, Score com fatores, Paciente criado implicitamente;</li>
 *   <li>CPF ja usado -&gt; 201, mesmo pacienteId reutilizado;</li>
 *   <li>Sinal vital ausente/fora de faixa -&gt; 400 RFC 7807 nomeando o campo;</li>
 *   <li>CPF invalido -&gt; 400 RFC 7807;</li>
 *   <li>Mesmos inputs, 2 Triagens distintas -&gt; Score identico, versao registrada;</li>
 *   <li>Evento ScoreCalculado gravado em eventos_outbox na mesma transacao do 201.</li>
 * </ol>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Relay (Story 3.0) desligado aqui -- este teste so cobre a spec 2.1
// (gravacao no outbox), nao a publicacao real; sem isso o RelaySnsPublisherJob
// tentaria falar com SNS de verdade (sem LocalStack neste teste).
@TestPropertySource(properties = "filajusta.triagem.relay.enabled=false")
class RegistrarTriagemIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpResponse<String> registrarTriagem(String cpf,
                                                    Map<String, Object> sinaisVitais,
                                                    String gravidadePercebida,
                                                    List<String> sintomas) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpf", cpf);
        body.put("sinaisVitais", sinaisVitais);
        body.put("gravidadePercebida", gravidadePercebida);
        body.put("sintomas", sintomas);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> sinaisVitaisValidos() {
        Map<String, Object> sinaisVitais = new LinkedHashMap<>();
        sinaisVitais.put("frequenciaCardiaca", 85.0);
        sinaisVitais.put("pressaoArterialSistolica", 120.0);
        sinaisVitais.put("pressaoArterialDiastolica", 80.0);
        sinaisVitais.put("saturacaoOxigenio", 97.0);
        sinaisVitais.put("frequenciaRespiratoria", 18.0);
        sinaisVitais.put("temperatura", 36.7);
        return sinaisVitais;
    }

    @Test
    void cpfNovoRetorna201ComScoreEFatoresECriaPacienteImplicitamente() throws Exception {
        HttpResponse<String> response = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "MODERADA", List.of("tosse", "febre"));

        assertThat(response.statusCode()).isEqualTo(201);

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("pacienteId").asLong()).isPositive();
        assertThat(json.get("triagemId").asLong()).isPositive();
        assertThat(json.get("score").get("algoritmoVersao").asText()).isEqualTo("v1");
        assertThat(json.get("score").get("valor").asInt()).isBetween(0, 100);
        assertThat(json.get("score").get("fatores")).hasSize(7);
        assertThat(json.get("sintomas")).extracting(JsonNode::asText).containsExactly("tosse", "febre");
        // CPF em texto claro nunca sai do servico (Boundaries da spec 2.1).
        assertThat(json.has("cpf")).isFalse();
    }

    @Test
    void cpfJaUsadoReutilizaOMesmoPacienteId() throws Exception {
        String cpf = "111.444.777-35";

        HttpResponse<String> primeira = registrarTriagem(cpf, sinaisVitaisValidos(), "LEVE", List.of());
        HttpResponse<String> segunda = registrarTriagem(cpf, sinaisVitaisValidos(), "GRAVE", List.of("dor"));

        assertThat(primeira.statusCode()).isEqualTo(201);
        assertThat(segunda.statusCode()).isEqualTo(201);

        long pacienteId1 = objectMapper.readTree(primeira.body()).get("pacienteId").asLong();
        long pacienteId2 = objectMapper.readTree(segunda.body()).get("pacienteId").asLong();
        assertThat(pacienteId2).isEqualTo(pacienteId1);

        long triagemId1 = objectMapper.readTree(primeira.body()).get("triagemId").asLong();
        long triagemId2 = objectMapper.readTree(segunda.body()).get("triagemId").asLong();
        assertThat(triagemId2).isNotEqualTo(triagemId1);

        Integer totalPacientes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM triagem_score.pacientes WHERE cpf = ?", Integer.class, "11144477735");
        assertThat(totalPacientes).isEqualTo(1);
    }

    @Test
    void frequenciaCardiacaForaDaFaixaRetorna400RfC7807NomeandoOCampo() throws Exception {
        Map<String, Object> sinaisVitais = sinaisVitaisValidos();
        sinaisVitais.put("frequenciaCardiaca", 999.0);

        HttpResponse<String> response = registrarTriagem("529.982.247-25", sinaisVitais, "LEVE", List.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("frequenciaCardiaca");
    }

    @Test
    void pasMenorOuIgualAPadRetorna400NomeandoOCampo() throws Exception {
        Map<String, Object> sinaisVitais = sinaisVitaisValidos();
        sinaisVitais.put("pressaoArterialSistolica", 80.0);
        sinaisVitais.put("pressaoArterialDiastolica", 90.0);

        HttpResponse<String> response = registrarTriagem("529.982.247-25", sinaisVitais, "LEVE", List.of());

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("pressaoArterialSistolica");
    }

    @Test
    void cpfInvalidoRetorna400RfC7807() throws Exception {
        HttpResponse<String> response = registrarTriagem(
                "111.111.111-11", sinaisVitaisValidos(), "LEVE", List.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("cpf");
    }

    @Test
    void mesmosInputsEmDuasTriagensDistintasProduzemOMesmoScore() throws Exception {
        Map<String, Object> sinaisVitais = sinaisVitaisValidos();

        HttpResponse<String> triagem1 = registrarTriagem("529.982.247-25", sinaisVitais, "GRAVE", List.of());
        HttpResponse<String> triagem2 = registrarTriagem("111.444.777-35", sinaisVitais, "GRAVE", List.of());

        assertThat(triagem1.statusCode()).isEqualTo(201);
        assertThat(triagem2.statusCode()).isEqualTo(201);

        JsonNode score1 = objectMapper.readTree(triagem1.body()).get("score");
        JsonNode score2 = objectMapper.readTree(triagem2.body()).get("score");

        assertThat(score1.get("valor").asInt()).isEqualTo(score2.get("valor").asInt());
        assertThat(score1.get("algoritmoVersao").asText()).isEqualTo(score2.get("algoritmoVersao").asText());
        assertThat(score1.get("fatores")).isEqualTo(score2.get("fatores"));
    }

    @Test
    void eventoScoreCalculadoEGravadoEmEventosOutboxSemBloquearAResposta() throws Exception {
        Integer totalAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM triagem_score.eventos_outbox", Integer.class);

        HttpResponse<String> response = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "CRITICA", List.of());

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode responseJson = objectMapper.readTree(response.body());

        Integer totalDepois = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM triagem_score.eventos_outbox", Integer.class);
        assertThat(totalDepois).isEqualTo(totalAntes + 1);

        Map<String, Object> ultimoEvento = jdbcTemplate.queryForMap(
                "SELECT event_type, payload FROM triagem_score.eventos_outbox ORDER BY id DESC LIMIT 1");
        assertThat(ultimoEvento.get("event_type")).isEqualTo("ScoreCalculado");

        // Payload deserializado e comparado campo a campo (nao so "contem a
        // substring") -- pegaria pacienteId/triagemId/algoritmoVersao/fatores
        // ausentes ou incorretos, o que um assert de substring nao pegaria.
        JsonNode payload = objectMapper.readTree(ultimoEvento.get("payload").toString());
        assertThat(payload.get("pacienteId").asLong()).isEqualTo(responseJson.get("pacienteId").asLong());
        assertThat(payload.get("triagemId").asLong()).isEqualTo(responseJson.get("triagemId").asLong());
        assertThat(payload.get("scoreValor").asInt()).isEqualTo(responseJson.get("score").get("valor").asInt());
        assertThat(payload.get("algoritmoVersao").asText()).isEqualTo("v1");
        assertThat(payload.get("fatores")).hasSize(7);
        assertThat(payload.get("fatores")).isEqualTo(responseJson.get("score").get("fatores"));
    }

    @Test
    void correlationIdAusenteGeraUmValorNaoNuloGravadoNoOutbox() throws Exception {
        HttpResponse<String> response = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "LEVE", List.of());

        assertThat(response.statusCode()).isEqualTo(201);

        String correlationId = jdbcTemplate.queryForObject(
                "SELECT correlation_id FROM triagem_score.eventos_outbox ORDER BY id DESC LIMIT 1", String.class);
        assertThat(correlationId).isNotBlank();
    }

    @Test
    void correlationIdDoHeaderXCorrelationIdEPropagadoParaOOutbox() throws Exception {
        String correlationIdEnviado = "corr-" + java.util.UUID.randomUUID();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpf", "529.982.247-25");
        body.put("sinaisVitais", sinaisVitaisValidos());
        body.put("gravidadePercebida", "LEVE");
        body.put("sintomas", List.of());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", correlationIdEnviado)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);

        String correlationIdGravado = jdbcTemplate.queryForObject(
                "SELECT correlation_id FROM triagem_score.eventos_outbox ORDER BY id DESC LIMIT 1", String.class);
        assertThat(correlationIdGravado).isEqualTo(correlationIdEnviado);
    }

    @Test
    void correlationIdMaiorQueOLimitePersistivelRetorna400EmVezDe500() throws Exception {
        // Achado do code review: eventos_outbox.correlation_id e
        // VARCHAR(128) (migration V2) -- sem validacao, um header maior
        // quebrava no INSERT como 500 nao controlado, em vez de 400
        // nomeando o campo, mesma convencao do resto do servico.
        String correlationIdMuitoLongo = "c".repeat(129);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpf", "529.982.247-25");
        body.put("sinaisVitais", sinaisVitaisValidos());
        body.put("gravidadePercebida", "LEVE");
        body.put("sintomas", List.of());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", correlationIdMuitoLongo)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("correlationId");
    }

    @Test
    void versionDoEnvelopeGravadaNoOutboxComeca1() throws Exception {
        HttpResponse<String> response = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "LEVE", List.of());

        assertThat(response.statusCode()).isEqualTo(201);

        Integer version = jdbcTemplate.queryForObject(
                "SELECT version FROM triagem_score.eventos_outbox ORDER BY id DESC LIMIT 1", Integer.class);
        assertThat(version).isEqualTo(1);
    }

    @Test
    void linhaDoOutboxNascePendenteDePublicacao() throws Exception {
        HttpResponse<String> response = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "LEVE", List.of());

        assertThat(response.statusCode()).isEqualTo(201);

        Integer pendentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM triagem_score.eventos_outbox WHERE publicado_em IS NULL", Integer.class);
        assertThat(pendentes).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "INVALIDO"})
    void gravidadePercebidaAusenteOuInvalidaRetorna400NomeandoOCampo(String gravidadeInvalida) throws Exception {
        HttpResponse<String> response = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), gravidadeInvalida, List.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("gravidadePercebida");
    }

    @Test
    void sinaisVitaisAusenteRetorna400NomeandoOPrimeiroSinalVital() throws Exception {
        // "sinaisVitais" nem existe no corpo (nao so null) -- exercita o
        // guard nulo de TriagemController, que repassa campos nulos ao
        // dominio para reportar o primeiro sinal vital ausente.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpf", "529.982.247-25");
        body.put("gravidadePercebida", "LEVE");
        body.put("sintomas", List.of());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("frequenciaCardiaca");
    }

    @Test
    void corpoAusenteRetorna400RfC7807() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void corpoJsonMalformadoRetorna400RfC7807() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{ isso nao e json valido"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }
}
