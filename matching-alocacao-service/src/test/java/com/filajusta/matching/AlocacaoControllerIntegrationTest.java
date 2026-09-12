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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre, no nível HTTP, a I/O &amp; Edge-Case Matrix da spec 3-3b1 que
 * {@code ConfirmarAlocacaoTest} (unitário, com mocks) não prova por si só --
 * o FORMATO da resposta (201 com corpo JSON, RFC 7807 nos erros) de
 * {@code POST /v1/recursos/{id}/alocacoes}: confirmação feliz, os 2
 * cenários de {@code 409}, Recurso inexistente ({@code 404}), corpo
 * inválido e {@code correlationId} acima do limite ({@code 400}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class AlocacaoControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID upsertRecursoDisponivel() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigoRecurso", "RECURSO-" + UUID.randomUUID());
        body.put("especificidadeRank", 1);
        body.put("disponivel", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/recursos"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> resposta = client.send(request, HttpResponse.BodyHandlers.ofString());
        return UUID.fromString(objectMapper.readTree(resposta.body()).get("recursoId").asText());
    }

    private HttpResponse<String> confirmar(String recursoId, String corpoJson, String correlationId)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/recursos/" + recursoId + "/alocacoes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpoJson));
        if (correlationId != null) {
            builder.header("X-Correlation-Id", correlationId);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String corpoComPaciente(long pacienteId) {
        return "{\"pacienteId\":" + pacienteId + "}";
    }

    // pacienteId proprio por teste (indice unico parcial no banco) -- evita
    // colisao entre metodos de teste que rodam na mesma instancia do
    // Postgres/Spring context (mesmo raciocinio do codigoRecurso proprio por
    // teste em RecursoSugestaoControllerIntegrationTest).
    private static long novoPacienteId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    @Test
    void confirmacaoFelizRetorna201EMarcaRecursoIndisponivel() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();
        long pacienteId = novoPacienteId();

        HttpResponse<String> resposta = confirmar(recursoId.toString(), corpoComPaciente(pacienteId), "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("recursoId").asText()).isEqualTo(recursoId.toString());
        assertThat(json.get("pacienteId").asLong()).isEqualTo(pacienteId);
        assertThat(json.get("status").asText()).isEqualTo("ATIVA");

        Boolean disponivel = jdbcTemplate.queryForObject(
                "SELECT disponivel FROM matching_alocacao.recurso WHERE recurso_id = ?::uuid",
                Boolean.class, recursoId.toString());
        assertThat(disponivel).isFalse();

        // AC3 da spec 3-3b1: a linha do outbox precisa existir para o
        // RelaySnsPublisherJob (Story 3-3a, mecanismo generico ja provado em
        // RelaySnsPublisherJobIntegrationTest) publicar -- aqui so provamos
        // que ConfirmarAlocacao grava a linha certa na mesma transacao.
        // Filtra pelo recursoId no payload (nao por correlation_id, que
        // outros metodos de teste desta classe tambem usam "corr-1").
        Integer linhasOutboxPendentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.eventos_outbox "
                        + "WHERE event_type = 'AlocacaoConfirmada' AND publicado_em IS NULL "
                        + "AND payload ->> 'recursoId' = ?",
                Integer.class, recursoId.toString());
        assertThat(linhasOutboxPendentes).isEqualTo(1);
    }

    @Test
    void segundaConfirmacaoParaOMesmoRecursoRetorna409() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();
        confirmar(recursoId.toString(), corpoComPaciente(novoPacienteId()), "corr-1");

        HttpResponse<String> resposta = confirmar(recursoId.toString(), corpoComPaciente(novoPacienteId()), "corr-2");

        assertThat(resposta.statusCode()).isEqualTo(409);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void confirmacaoParaPacienteJaAlocadoEmOutroRecursoRetorna409() throws Exception {
        UUID primeiroRecurso = upsertRecursoDisponivel();
        UUID segundoRecurso = upsertRecursoDisponivel();
        long pacienteId = novoPacienteId();
        confirmar(primeiroRecurso.toString(), corpoComPaciente(pacienteId), "corr-1");

        HttpResponse<String> resposta = confirmar(segundoRecurso.toString(), corpoComPaciente(pacienteId), "corr-2");

        assertThat(resposta.statusCode()).isEqualTo(409);
    }

    @Test
    void recursoInexistenteRetorna404RFC7807NomeandoOId() throws Exception {
        UUID recursoIdInexistente = UUID.randomUUID();

        HttpResponse<String> resposta = confirmar(recursoIdInexistente.toString(), corpoComPaciente(1L), "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(404);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("detail").asText()).contains(recursoIdInexistente.toString());
    }

    @Test
    void corpoSemPacienteIdRetorna400ComDetailNomeandoOCampo() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();

        HttpResponse<String> resposta = confirmar(recursoId.toString(), "{}", "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
        JsonNode json = objectMapper.readTree(resposta.body());
        // Achado do code review multi-agente (verification-gap): o "detail"
        // nao pode vazar texto tecnico interno (nome do record vinculado
        // etc.) -- so o nome do campo invalido, nunca a mensagem bruta do
        // Spring (MethodArgumentNotValidException#getMessage()).
        assertThat(json.get("detail").asText())
                .contains("pacienteId")
                .doesNotContain("confirmarAlocacaoRequest", "Validation failed for argument");
    }

    @Test
    void idNaoUuidNoPathRetorna400() throws Exception {
        HttpResponse<String> resposta = confirmar("nao-e-um-uuid", corpoComPaciente(1L), "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void correlationIdAcimaDoLimiteRetorna400() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();
        String correlationIdMuitoLongo = "x".repeat(129);

        HttpResponse<String> resposta = confirmar(recursoId.toString(), corpoComPaciente(1L), correlationIdMuitoLongo);

        assertThat(resposta.statusCode()).isEqualTo(400);
    }
}
