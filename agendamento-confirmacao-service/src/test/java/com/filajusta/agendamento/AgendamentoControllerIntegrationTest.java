package com.filajusta.agendamento;

import com.filajusta.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre ponta a ponta o contrato HTTP de {@code POST /v1/agendamentos}
 * (I/O &amp; Edge-Case Matrix da spec 1.1), contra um Postgres 18 real via
 * Testcontainers (Flyway roda a migration
 * V1__create_agendamento_confirmacao_schema.sql tal como sobe em producao):
 *
 * <ol>
 *   <li>Registro feliz, paciente novo -&gt; 201, Agendamento AGUARDANDO_JANELA, Paciente criado implicitamente;</li>
 *   <li>Registro feliz, paciente ja existente -&gt; 201, mesmo pacienteId reutilizado;</li>
 *   <li>CPF invalido -&gt; 422 RFC 7807, nada persistido;</li>
 *   <li>recursoId malformado/ausente -&gt; 422 RFC 7807, nada persistido;</li>
 *   <li>dataHoraAgendamento no passado/ausente -&gt; 422 RFC 7807, nada persistido.</li>
 * </ol>
 *
 * <p>Cobre tambem, ponta a ponta, o contrato HTTP de {@code POST
 * /v1/agendamentos/{id}/confirmacao} (I/O &amp; Edge-Case Matrix da spec
 * 1.3): confirmacao valida (200), confirmacao duplicada (200, sem novo
 * evento), janela ainda nao aberta (409), vaga ja liberada (409) e
 * agendamentoId inexistente (404).
 *
 * <p>Cobre tambem, ponta a ponta, o contrato HTTP de {@code POST
 * /v1/agendamentos/{id}/recusa} (I/O &amp; Edge-Case Matrix da spec 1.4):
 * recusa valida (200), recusa duplicada (200, sem novo evento),
 * janela ainda nao aberta (409), confirmado (409), liberado por outro motivo
 * (409) e agendamentoId inexistente (404).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Este teste nao exercita o relay outbox (Story 1.2) -- desliga o kill
// switch explicitamente (mesmo padrao de matching-alocacao-service) para
// nao exigir um topic-arn/SnsClient real so para subir o contexto.
@TestPropertySource(properties = "filajusta.agendamento.outbox-relay.enabled=false")
class AgendamentoControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpResponse<String> registrarAgendamento(String cpf, String recursoId, Instant dataHoraAgendamento)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cpf", cpf);
        body.put("recursoId", recursoId);
        body.put("dataHoraAgendamento", dataHoraAgendamento == null ? null : dataHoraAgendamento.toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/agendamentos"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Instant dataFutura() {
        return Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    }

    private static String novoRecursoId() {
        return java.util.UUID.randomUUID().toString();
    }

    private HttpResponse<String> confirmarPresenca(long agendamentoId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/agendamentos/" + agendamentoId + "/confirmacao"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> recusarPresenca(long agendamentoId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/agendamentos/" + agendamentoId + "/recusa"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Insere Paciente + Agendamento diretamente via JDBC no {@code status}
     * pedido -- {@code POST /v1/agendamentos} so alcanca {@code
     * AGUARDANDO_JANELA} (Story 1.1), entao os demais estados precisam ser
     * montados direto no banco para exercitar {@code POST
     * /v1/agendamentos/{id}/confirmacao} (spec 1.3).
     */
    private long criarAgendamentoComStatus(StatusAgendamento status) {
        String cpf = String.format("%011d", Math.abs(System.nanoTime()) % 100_000_000_000L);
        Long pacienteId = jdbcTemplate.queryForObject(
                "INSERT INTO agendamento_confirmacao.pacientes (cpf) VALUES (?) RETURNING id",
                Long.class, cpf);

        Instant agora = Instant.now();
        // java.time.Instant puro nao tem tipo SQL inferivel pelo driver
        // Postgres via setObject -- java.sql.Timestamp.from(...) (mesmo
        // resultado de TIMESTAMPTZ) evita o BadSqlGrammarException.
        return jdbcTemplate.queryForObject(
                "INSERT INTO agendamento_confirmacao.agendamentos "
                        + "(paciente_id, recurso_id, data_hora_agendamento, status, criado_em, janela_abre_em) "
                        + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                pacienteId, java.util.UUID.randomUUID(),
                java.sql.Timestamp.from(agora.plus(1, ChronoUnit.DAYS)), status.name(),
                java.sql.Timestamp.from(agora.minus(1, ChronoUnit.HOURS)),
                java.sql.Timestamp.from(agora.minus(10, ChronoUnit.MINUTES)));
    }

    @Test
    void cpfNovoRetorna201ComAgendamentoAguardandoJanelaECriaPacienteImplicitamente() throws Exception {
        HttpResponse<String> response = registrarAgendamento("529.982.247-25", novoRecursoId(), dataFutura());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/json"));

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("agendamentoId").asLong()).isPositive();
        assertThat(json.get("pacienteId").asLong()).isPositive();
        assertThat(json.get("status").asText()).isEqualTo("AGUARDANDO_JANELA");
        // CPF em texto claro nunca sai do servico (Boundaries da spec 1.1).
        assertThat(json.has("cpf")).isFalse();
    }

    @Test
    void cpfJaUsadoEmDoisAgendamentosDistintosReutilizaOMesmoPacienteId() throws Exception {
        String cpf = "111.444.777-35";

        HttpResponse<String> primeiro = registrarAgendamento(cpf, novoRecursoId(), dataFutura());
        HttpResponse<String> segundo = registrarAgendamento(cpf, novoRecursoId(), dataFutura());

        assertThat(primeiro.statusCode()).isEqualTo(201);
        assertThat(segundo.statusCode()).isEqualTo(201);

        long pacienteId1 = objectMapper.readTree(primeiro.body()).get("pacienteId").asLong();
        long pacienteId2 = objectMapper.readTree(segundo.body()).get("pacienteId").asLong();
        assertThat(pacienteId2).isEqualTo(pacienteId1);

        long agendamentoId1 = objectMapper.readTree(primeiro.body()).get("agendamentoId").asLong();
        long agendamentoId2 = objectMapper.readTree(segundo.body()).get("agendamentoId").asLong();
        assertThat(agendamentoId2).isNotEqualTo(agendamentoId1);

        Integer totalPacientes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.pacientes WHERE cpf = ?",
                Integer.class, "11144477735");
        assertThat(totalPacientes).isEqualTo(1);
    }

    @Test
    void cpfInvalidoRetorna422RfC7807SemPersistirNada() throws Exception {
        Integer pacientesAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.pacientes", Integer.class);
        Integer agendamentosAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos", Integer.class);

        HttpResponse<String> response = registrarAgendamento("111.111.111-11", novoRecursoId(), dataFutura());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("cpf");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.pacientes", Integer.class)).isEqualTo(pacientesAntes);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos", Integer.class))
                .isEqualTo(agendamentosAntes);
    }

    @Test
    void recursoIdMalformadoRetorna422RfC7807SemPersistirNada() throws Exception {
        Integer agendamentosAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos", Integer.class);

        HttpResponse<String> response = registrarAgendamento("529.982.247-25", "nao-e-um-uuid", dataFutura());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("recursoId");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos", Integer.class))
                .isEqualTo(agendamentosAntes);
    }

    @Test
    void recursoIdAusenteRetorna422RfC7807() throws Exception {
        HttpResponse<String> response = registrarAgendamento("529.982.247-25", null, dataFutura());

        assertThat(response.statusCode()).isEqualTo(422);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("recursoId");
    }

    @Test
    void dataHoraAgendamentoNoPassadoRetorna422RfC7807SemPersistirNada() throws Exception {
        Integer agendamentosAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos", Integer.class);
        Instant passado = Instant.now().minus(1, ChronoUnit.DAYS);

        HttpResponse<String> response = registrarAgendamento("529.982.247-25", novoRecursoId(), passado);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("dataHoraAgendamento");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos", Integer.class))
                .isEqualTo(agendamentosAntes);
    }

    @Test
    void dataHoraAgendamentoAusenteRetorna422RfC7807() throws Exception {
        HttpResponse<String> response = registrarAgendamento("529.982.247-25", novoRecursoId(), null);

        assertThat(response.statusCode()).isEqualTo(422);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("campo").asText()).isEqualTo("dataHoraAgendamento");
    }

    @Test
    void corpoAusenteRetorna422RfC7807() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/agendamentos"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void confirmacaoValidaRetorna200ETransicionaParaConfirmadoGravandoEventoNoOutbox() throws Exception {
        long agendamentoId = criarAgendamentoComStatus(StatusAgendamento.AGUARDANDO_CONFIRMACAO);

        HttpResponse<String> response = confirmarPresenca(agendamentoId);

        assertThat(response.statusCode()).isEqualTo(200);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, agendamentoId);
        assertThat(status).isEqualTo("CONFIRMADO");

        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE event_type = 'ConfirmacaoRegistrada' AND payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(agendamentoId));
        assertThat(eventosGravados).isEqualTo(1);
    }

    @Test
    void confirmacaoDuplicadaRetorna200SemGravarNovoEvento() throws Exception {
        long agendamentoId = criarAgendamentoComStatus(StatusAgendamento.AGUARDANDO_CONFIRMACAO);

        HttpResponse<String> primeira = confirmarPresenca(agendamentoId);
        HttpResponse<String> segunda = confirmarPresenca(agendamentoId);

        assertThat(primeira.statusCode()).isEqualTo(200);
        assertThat(segunda.statusCode()).isEqualTo(200);

        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE event_type = 'ConfirmacaoRegistrada' AND payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(agendamentoId));
        assertThat(eventosGravados).isEqualTo(1);
    }

    @Test
    void janelaAindaNaoAbertaRetorna409RfC7807() throws Exception {
        long agendamentoId = criarAgendamentoComStatus(StatusAgendamento.AGUARDANDO_JANELA);

        HttpResponse<String> response = confirmarPresenca(agendamentoId);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, agendamentoId);
        assertThat(status).isEqualTo("AGUARDANDO_JANELA");
    }

    @Test
    void vagaJaLiberadaRetorna409RfC7807() throws Exception {
        long agendamentoId = criarAgendamentoComStatus(StatusAgendamento.LIBERADO);

        HttpResponse<String> response = confirmarPresenca(agendamentoId);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void agendamentoIdInexistenteRetorna404() throws Exception {
        HttpResponse<String> response = confirmarPresenca(Long.MAX_VALUE);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void identificadorNaoNumericoRetorna400RfC7807() throws Exception {
        // Achado do code review adversarial: id nao numerico no path faz o
        // Spring lancar MethodArgumentTypeMismatchException antes do
        // controller -- sem handler dedicado caia no fallback generico e
        // respondia 500 para uma entrada invalida.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/agendamentos/abc/confirmacao"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void recusaValidaRetorna200ETransicionaParaLiberadoComMotivoRecusaGravandoDoisEventosNoOutbox()
            throws Exception {
        long agendamentoId = criarAgendamentoComStatus(StatusAgendamento.AGUARDANDO_CONFIRMACAO);

        HttpResponse<String> response = recusarPresenca(agendamentoId);

        assertThat(response.statusCode()).isEqualTo(200);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, agendamentoId);
        assertThat(status).isEqualTo("LIBERADO");
        String motivo = jdbcTemplate.queryForObject(
                "SELECT motivo_liberacao FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, agendamentoId);
        assertThat(motivo).isEqualTo("RECUSA");

        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(agendamentoId));
        assertThat(eventosGravados).isEqualTo(2);

        Integer recusaRegistrada = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE event_type = 'RecusaRegistrada' AND payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(agendamentoId));
        assertThat(recusaRegistrada).isEqualTo(1);

        Integer vagaLiberada = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE event_type = 'VagaLiberada' AND payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(agendamentoId));
        assertThat(vagaLiberada).isEqualTo(1);
    }

    @Test
    void recusaDuplicadaRetorna200SemGravarNovoEvento() throws Exception {
        long agendamentoId = criarAgendamentoComStatus(StatusAgendamento.AGUARDANDO_CONFIRMACAO);

        HttpResponse<String> primeira = recusarPresenca(agendamentoId);
        HttpResponse<String> segunda = recusarPresenca(agendamentoId);

        assertThat(primeira.statusCode()).isEqualTo(200);
        assertThat(segunda.statusCode()).isEqualTo(200);

        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(agendamentoId));
        assertThat(eventosGravados).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAgendamento.class, names = {"AGUARDANDO_JANELA", "CONFIRMADO", "LIBERADO"})
    void recusaEmEstadosInvalidosRetorna409RfC7807(StatusAgendamento statusInvalido) throws Exception {
        long agendamentoId = criarAgendamentoComStatus(statusInvalido);

        HttpResponse<String> response = recusarPresenca(agendamentoId);

        assertThat(response.statusCode())
                .as("recusa em status " + statusInvalido + " deve retornar 409")
                .isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        String statusAtual = jdbcTemplate.queryForObject(
                "SELECT status FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, agendamentoId);
        assertThat(statusAtual)
                .as("status nao deve ter sido alterado")
                .isEqualTo(statusInvalido.name());
    }

    @Test
    void recusaEmAgendamentoIdInexistenteRetorna404() throws Exception {
        HttpResponse<String> response = recusarPresenca(Long.MAX_VALUE);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }

    @Test
    void recusaComIdentificadorNaoNumericoRetorna400RfC7807() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/agendamentos/abc/recusa"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));
    }
}
