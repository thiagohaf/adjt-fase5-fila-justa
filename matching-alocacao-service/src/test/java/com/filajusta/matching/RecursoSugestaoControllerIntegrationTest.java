package com.filajusta.matching;

import org.junit.jupiter.api.Test;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre, no nivel HTTP, as linhas da I/O &amp; Edge-Case Matrix da spec
 * 3.2b3 que {@code ConsultarSugestaoRecursoTest} (unitario, com mocks) nao
 * prova por si so -- o FORMATO da resposta (200 com corpo JSON, RFC 7807 nos
 * erros) de {@code GET /v1/recursos/{id}/sugestao}, nao so o comportamento
 * do caso de uso: HAPPY_PATH (200 com {@code recursoId}/{@code pacienteId}
 * reais), RECURSO_INDISPONIVEL (200 com {@code pacienteId} null), {@code
 * recursoId} sintaticamente valido mas inexistente (404) e {@code id}
 * nao-UUID no path (400).
 *
 * <p>RECURSO_INEXISTENTE e ID_MALFORMADO nao tocam a fila global ({@code
 * ConsultarSugestaoRecurso} lanca a excecao ou o Spring rejeita o path
 * variable antes de chamar {@code ConsultarFilaPriorizada.consultar()},
 * confirmado em {@code ConsultarSugestaoRecursoTest#recursoInexistenteLancaRecursoNaoEncontradoENuncaConsultaAFilaGlobal}),
 * e HAPPY_PATH popula a fila global inserindo direto em {@code
 * score_replica} via {@link JdbcTemplate} -- entao nenhum cenario aqui
 * precisa estubar o bootstrap sincrono (WireMock), diferente de {@code
 * FilaBootstrapIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Relay SQS (Story 3.1b) e relay outbox (Story 3-3a) desligados -- este
// teste so cobre GET /v1/recursos/{id}/sugestao, sem depender de
// LocalStack/SQS/SNS.
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class RecursoSugestaoControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpResponse<String> consultarSugestao(String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/recursos/" + id + "/sugestao"))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private UUID upsertRecurso(int especificidadeRank, boolean disponivel) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigoRecurso", "RECURSO-" + UUID.randomUUID());
        body.put("especificidadeRank", especificidadeRank);
        body.put("disponivel", disponivel);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/recursos"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> resposta = client.send(request, HttpResponse.BodyHandlers.ofString());
        return UUID.fromString(objectMapper.readTree(resposta.body()).get("recursoId").asText());
    }

    private void seedScoreReplica(long pacienteId, int score) {
        jdbcTemplate.update(
                "INSERT INTO matching_alocacao.score_replica "
                        + "(paciente_id, score, occurred_at, event_id, updated_at) VALUES (?, ?, ?, ?, ?)",
                pacienteId, score, Timestamp.from(Instant.parse("2026-09-11T12:00:00Z")),
                UUID.randomUUID(), Timestamp.from(Instant.now()));
    }

    private void seedSugestaoRecusada(UUID recursoId, long pacienteId) {
        jdbcTemplate.update(
                "INSERT INTO matching_alocacao.sugestao_recusada "
                        + "(recurso_id, paciente_id, motivo, recusado_em) VALUES (?, ?, ?, ?)",
                recursoId, pacienteId, "sem leitos disponiveis na especialidade",
                Timestamp.from(Instant.now()));
    }

    @Test
    void recursoDisponivelComRankMaisGenericoRetorna200ComPacienteIdDoTopoDaFila() throws Exception {
        // HAPPY_PATH no nivel HTTP: Recurso rank=1 (o mais generico
        // possivel) -- N=0 sempre, sugestao = topo da fila global real.
        long pacienteId = 918273L;
        seedScoreReplica(pacienteId, 80);
        UUID recursoId = upsertRecurso(1, true);

        HttpResponse<String> resposta = consultarSugestao(recursoId.toString());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/json"));

        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("recursoId").asText()).isEqualTo(recursoId.toString());
        assertThat(json.get("pacienteId").asLong()).isEqualTo(pacienteId);
    }

    @Test
    void pacienteRecusadoParaORecursoEPuladoNaSugestaoDoTopoDaFila() throws Exception {
        // Story 3-3c2a: topo da fila global (pacienteId=502931L) ja foi
        // recusado para este Recurso -- sugestao deve pular para o proximo
        // paciente elegivel da fila (pacienteId=502942L), sem erro.
        // Scores bem acima dos usados nos demais testes desta classe (80) --
        // a tabela score_replica nao e limpa entre metodos (container
        // Testcontainers estatico), entao o topo da fila global precisa ser
        // garantido mesmo com linhas de outros testes ja persistidas.
        long pacienteRecusado = 502931L;
        long pacienteElegivel = 502942L;
        seedScoreReplica(pacienteRecusado, 100);
        seedScoreReplica(pacienteElegivel, 99);
        UUID recursoId = upsertRecurso(1, true);
        seedSugestaoRecusada(recursoId, pacienteRecusado);

        HttpResponse<String> resposta = consultarSugestao(recursoId.toString());

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("pacienteId").asLong()).isEqualTo(pacienteElegivel);
    }

    @Test
    void recursoIndisponivelRetorna200ComPacienteIdNulo() throws Exception {
        UUID recursoId = upsertRecurso(2, false);

        HttpResponse<String> resposta = consultarSugestao(recursoId.toString());

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("recursoId").asText()).isEqualTo(recursoId.toString());
        assertThat(json.get("pacienteId").isNull())
                .as("pacienteId deve serializar como JSON null, nunca ser omitido -- corpo: %s", resposta.body())
                .isTrue();
    }

    @Test
    void recursoIdInexistenteRetorna404RFC7807NomeandoOId() throws Exception {
        UUID recursoIdInexistente = UUID.randomUUID();

        HttpResponse<String> resposta = consultarSugestao(recursoIdInexistente.toString());

        assertThat(resposta.statusCode()).isEqualTo(404);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("detail").asText()).contains(recursoIdInexistente.toString());
    }

    @Test
    void idNaoUuidNoPathRetorna400RFC7807() throws Exception {
        HttpResponse<String> resposta = consultarSugestao("nao-e-um-uuid");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }
}
