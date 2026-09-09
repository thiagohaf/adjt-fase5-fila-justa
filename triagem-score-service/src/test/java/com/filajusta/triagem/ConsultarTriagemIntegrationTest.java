package com.filajusta.triagem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 * Cobre ponta a ponta os 3 cenarios da I/O & Edge-Case Matrix da spec 2.2,
 * contra um Postgres 18 real via Testcontainers: registra uma Triagem via
 * {@code POST /v1/triagens} e consulta via {@code GET /v1/triagens/{id}}.
 *
 * <ol>
 *   <li>Triagem existente -&gt; 200, Score identico (valor, versao, fatores)
 *   ao retornado no registro, mais sinais vitais/gravidade/sintomas/criadoEm;</li>
 *   <li>id inexistente -&gt; 404 RFC 7807 nomeando o id;</li>
 *   <li>id nao numerico -&gt; 400 RFC 7807, sem escapar como 500 generico.</li>
 * </ol>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Relay (Story 3.0) desligado aqui -- este teste so cobre a spec 2.2
// (consulta), nao a publicacao real; sem isso o RelaySnsPublisherJob
// tentaria falar com SNS de verdade (sem LocalStack neste teste).
@TestPropertySource(properties = "filajusta.triagem.relay.enabled=false")
class ConsultarTriagemIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private HttpResponse<String> consultarTriagem(String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/triagens/" + id))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void triagemExistenteRetorna200ComScoreIdenticoAoRegistro() throws Exception {
        HttpResponse<String> registro = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "MODERADA", List.of("tosse", "febre"));
        assertThat(registro.statusCode()).isEqualTo(201);
        JsonNode registrado = objectMapper.readTree(registro.body());
        long triagemId = registrado.get("triagemId").asLong();

        HttpResponse<String> consulta = consultarTriagem(String.valueOf(triagemId));

        assertThat(consulta.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(consulta.body());

        assertThat(json.get("triagemId").asLong()).isEqualTo(triagemId);
        assertThat(json.get("pacienteId").asLong()).isEqualTo(registrado.get("pacienteId").asLong());
        assertThat(json.get("gravidadePercebida").asText()).isEqualTo("MODERADA");
        assertThat(json.get("sintomas")).extracting(JsonNode::asText).containsExactly("tosse", "febre");
        assertThat(json.get("criadoEm").asText()).isEqualTo(registrado.get("criadoEm").asText());

        // Score (valor, versao, fatores) identico ao retornado no registro --
        // nunca recalculado (Boundaries da spec 2.2).
        assertThat(json.get("score")).isEqualTo(registrado.get("score"));

        JsonNode sinaisVitais = json.get("sinaisVitais");
        assertThat(sinaisVitais.get("frequenciaCardiaca").asDouble()).isEqualTo(85.0);
        assertThat(sinaisVitais.get("pressaoArterialSistolica").asDouble()).isEqualTo(120.0);
        assertThat(sinaisVitais.get("pressaoArterialDiastolica").asDouble()).isEqualTo(80.0);
        assertThat(sinaisVitais.get("saturacaoOxigenio").asDouble()).isEqualTo(97.0);
        assertThat(sinaisVitais.get("frequenciaRespiratoria").asDouble()).isEqualTo(18.0);
        assertThat(sinaisVitais.get("temperatura").asDouble()).isEqualTo(36.7);

        // CPF em texto claro nunca sai do servico (mesma fronteira da 2.1).
        assertThat(json.has("cpf")).isFalse();
    }

    @Test
    void idInexistenteRetorna404RFC7807NomeandoOId() throws Exception {
        HttpResponse<String> response = consultarTriagem("999999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("detail").asText()).contains("999999");
    }

    @Test
    void idNaoNumericoRetorna400RFC7807() throws Exception {
        HttpResponse<String> response = consultarTriagem("abc");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }
}
