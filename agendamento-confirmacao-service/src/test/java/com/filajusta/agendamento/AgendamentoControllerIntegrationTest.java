package com.filajusta.agendamento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
}
