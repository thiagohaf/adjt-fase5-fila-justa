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

    private HttpResponse<String> recusar(String recursoId, String corpoJson, String correlationId)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/recursos/" + recursoId + "/alocacoes/recusa"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpoJson));
        if (correlationId != null) {
            builder.header("X-Correlation-Id", correlationId);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String corpoDeRecusa(long pacienteId, String motivo) {
        return "{\"pacienteId\":" + pacienteId + ",\"motivo\":\"" + motivo + "\"}";
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

        // Story 3-4a1, achado do code review multi-agente: prova, contra o
        // binding REAL de application.yml (nao os valores arbitrarios de
        // ConfirmarAlocacaoTest/LiberacaoDuracaoPropertiesTest), que
        // ConfirmarAlocacao grava LiberacaoAgendada na mesma transacao --
        // upsertRecursoDisponivel() sempre usa especificidadeRank=1, cuja
        // duracao configurada e filajusta.liberacao.duracao.rank-1=PT2M
        // (120s). Os 4 @Value posicionais de
        // MatchingAlocacaoServiceApplication#liberacaoDuracaoProperties nao
        // tem vinculo em tempo de compilacao com a chave de propriedade --
        // uma inversao de ordem so seria detectada aqui, contra o YAML real.
        UUID alocacaoId = UUID.fromString(json.get("alocacaoId").asText());
        Map<String, Object> liberacaoAgendada = jdbcTemplate.queryForMap(
                "SELECT recurso_id, delay_segundos, enviado_em FROM matching_alocacao.liberacao_agendada "
                        + "WHERE alocacao_id = ?::uuid",
                alocacaoId.toString());
        assertThat(liberacaoAgendada.get("recurso_id")).isEqualTo(recursoId);
        assertThat(liberacaoAgendada.get("delay_segundos")).isEqualTo(120);
        assertThat(liberacaoAgendada.get("enviado_em")).isNull();
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

    // Story 3-3c1 (RecusarSugestao): cenarios de
    // POST /v1/recursos/{id}/alocacoes/recusa a nivel HTTP contra Postgres
    // real -- feliz (grava o par em sugestao_recusada e o evento no outbox)
    // e duplicata (upsert idempotente, sem duplicar linha nem falhar). Os
    // cenarios de recurso inexistente e correlationId invalido ja tem
    // cobertura equivalente, unitaria, em RecusarSugestaoTest -- aqui so se
    // prova o FORMATO da resposta/persistencia deste novo endpoint. Motivo
    // em branco e pacienteId invalido sao Bean Validation no
    // RecusarSugestaoRequest (nivel HTTP) -- RecusarSugestaoTest opera sobre
    // o comando ja com um "long pacienteId" primitivo validado, entao nao
    // exercita essa anotacao; a cobertura real desses 2 cenarios vive so
    // aqui.

    @Test
    void recusaFelizRetorna201EGravaParEEventoOutbox() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();
        long pacienteId = novoPacienteId();

        HttpResponse<String> resposta = recusar(
                recursoId.toString(), corpoDeRecusa(pacienteId, "Paciente recusou o leito"), "corr-recusa-1");

        assertThat(resposta.statusCode()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("recursoId").asText()).isEqualTo(recursoId.toString());
        assertThat(json.get("pacienteId").asLong()).isEqualTo(pacienteId);
        assertThat(json.get("motivo").asText()).isEqualTo("Paciente recusou o leito");

        String motivoPersistido = jdbcTemplate.queryForObject(
                "SELECT motivo FROM matching_alocacao.sugestao_recusada "
                        + "WHERE recurso_id = ?::uuid AND paciente_id = ?",
                String.class, recursoId.toString(), pacienteId);
        assertThat(motivoPersistido).isEqualTo("Paciente recusou o leito");

        Integer linhasOutboxPendentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.eventos_outbox "
                        + "WHERE event_type = 'SugestaoRecusada' AND publicado_em IS NULL "
                        + "AND payload ->> 'recursoId' = ? AND correlation_id = 'corr-recusa-1'",
                Integer.class, recursoId.toString());
        assertThat(linhasOutboxPendentes).isEqualTo(1);
    }

    @Test
    void recusaDuplicadaParaOMesmoParRetorna201DeNovoESemDuplicarLinha() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();
        long pacienteId = novoPacienteId();
        recusar(recursoId.toString(), corpoDeRecusa(pacienteId, "motivo original"), "corr-recusa-1");

        HttpResponse<String> resposta = recusar(
                recursoId.toString(), corpoDeRecusa(pacienteId, "motivo atualizado"), "corr-recusa-2");

        assertThat(resposta.statusCode()).isEqualTo(201);

        Integer totalLinhas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.sugestao_recusada "
                        + "WHERE recurso_id = ?::uuid AND paciente_id = ?",
                Integer.class, recursoId.toString(), pacienteId);
        assertThat(totalLinhas).isEqualTo(1);

        String motivoPersistido = jdbcTemplate.queryForObject(
                "SELECT motivo FROM matching_alocacao.sugestao_recusada "
                        + "WHERE recurso_id = ?::uuid AND paciente_id = ?",
                String.class, recursoId.toString(), pacienteId);
        assertThat(motivoPersistido).isEqualTo("motivo atualizado");
    }

    @Test
    void recusaComRecursoInexistenteRetorna404RFC7807NomeandoOId() throws Exception {
        UUID recursoIdInexistente = UUID.randomUUID();

        HttpResponse<String> resposta = recusar(
                recursoIdInexistente.toString(), corpoDeRecusa(novoPacienteId(), "motivo"), "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(404);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("detail").asText()).contains(recursoIdInexistente.toString());
    }

    @Test
    void recusaComMotivoEmBrancoRetorna400() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();

        HttpResponse<String> resposta = recusar(
                recursoId.toString(), corpoDeRecusa(novoPacienteId(), ""), "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("detail").asText()).contains("motivo");
    }

    @Test
    void recusaComPacienteIdAusenteRetorna400ComDetailNomeandoOCampo() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();

        HttpResponse<String> resposta = recusar(
                recursoId.toString(), "{\"motivo\":\"Paciente recusou o leito\"}", "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("detail").asText())
                .contains("pacienteId")
                .doesNotContain("recusarSugestaoRequest", "Validation failed for argument");
    }

    @Test
    void recusaComPacienteIdNaoPositivoRetorna400ComDetailNomeandoOCampo() throws Exception {
        UUID recursoId = upsertRecursoDisponivel();

        HttpResponse<String> resposta = recusar(
                recursoId.toString(), corpoDeRecusa(0L, "Paciente recusou o leito"), "corr-1");

        assertThat(resposta.statusCode()).isEqualTo(400);
        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("detail").asText()).contains("pacienteId");
    }
}
