package com.confirmasus.matching;

import com.confirmasus.matching.application.command.GerarSugestaoRepasse;
import com.confirmasus.matching.application.command.GerarSugestaoRepasse.Resultado;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 6.1, contra Postgres real: {@code VagaLiberada} → {@code
 * SugestaoRepasse} (idempotente por {@code agendamentoId}, FIFO por {@code
 * criadoEm}), {@code GET /v1/recursos/{id}/sugestao}, confirmar/recusar
 * (escrita condicional, corrida = 1 vencedora) e devolução do Recurso ao pool.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "confirmasus.matching.relay.enabled=false",
        "confirmasus.matching.outbox-relay.enabled=false"
})
class SugestaoRepasseIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GerarSugestaoRepasse gerarSugestaoRepasse;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final AtomicLong agendamentoSeq = new AtomicLong(1000);

    private HttpResponse<String> http(String metodo, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        b.method(metodo, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID novoRecurso() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigoRecurso", "RECURSO-" + UUID.randomUUID());
        body.put("especificidadeRank", 1);
        body.put("disponivel", true);
        HttpResponse<String> r = http("POST", "/internal/recursos", objectMapper.writeValueAsString(body));
        return UUID.fromString(objectMapper.readTree(r.body()).get("recursoId").asText());
    }

    private long novoPaciente(UUID recursoId, Instant criadoEm) {
        String cpf = String.valueOf(10000000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 89999999999L));
        long id = jdbc.queryForObject(
                "INSERT INTO matching_alocacao.paciente (cpf) VALUES (?) RETURNING paciente_id", Long.class, cpf);
        jdbc.update("INSERT INTO matching_alocacao.lista_espera_entrada "
                        + "(paciente_id, recurso_id, data_solicitacao, criado_em) VALUES (?, ?, ?, ?)",
                id, recursoId, Timestamp.from(criadoEm), Timestamp.from(criadoEm));
        return id;
    }

    private JsonNode sugestaoPendente(UUID recursoId) throws Exception {
        HttpResponse<String> r = http("GET", "/v1/recursos/" + recursoId + "/sugestao", null);
        assertThat(r.statusCode()).isEqualTo(200);
        return objectMapper.readTree(r.body());
    }

    private int eventos(String tipo, UUID recursoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM matching_alocacao.eventos_outbox "
                + "WHERE event_type = ? AND payload ->> 'recursoId' = ?", Integer.class, tipo, recursoId.toString());
    }

    private boolean recursoDisponivel(UUID recursoId) {
        return jdbc.queryForObject("SELECT disponivel FROM matching_alocacao.recurso WHERE recurso_id = ?",
                Boolean.class, recursoId);
    }

    @Test
    void vagaLiberadaGeraSugestaoParaOMaisAntigoEReentregaNaoDuplica() throws Exception {
        UUID recurso = novoRecurso();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long primeiro = novoPaciente(recurso, t);
        novoPaciente(recurso, t.plusSeconds(60));
        long agendamento = agendamentoSeq.incrementAndGet();

        assertThat(gerarSugestaoRepasse.gerar(agendamento, recurso, "corr-1")).isEqualTo(Resultado.GERADA);
        assertThat(gerarSugestaoRepasse.gerar(agendamento, recurso, "corr-1")).isEqualTo(Resultado.DUPLICADA);

        JsonNode s = sugestaoPendente(recurso);
        assertThat(s.get("pacienteId").asLong()).isEqualTo(primeiro);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_alocacao.sugestao_repasse "
                + "WHERE agendamento_id = ?", Integer.class, agendamento)).isEqualTo(1);
        assertThat(eventos("SugestaoRepasseGerada", recurso)).isEqualTo(1);
    }

    @Test
    void listaVaziaNaoGeraSugestaoNemEventoEReentregaContinuaSemEfeito() throws Exception {
        UUID recurso = novoRecurso();
        long agendamento = agendamentoSeq.incrementAndGet();

        assertThat(gerarSugestaoRepasse.gerar(agendamento, recurso, null)).isEqualTo(Resultado.SEM_CANDIDATO);
        novoPaciente(recurso, Instant.now());
        assertThat(gerarSugestaoRepasse.gerar(agendamento, recurso, null)).isEqualTo(Resultado.DUPLICADA);

        assertThat(sugestaoPendente(recurso).get("sugestaoId").isNull()).isTrue();
        assertThat(eventos("SugestaoRepasseGerada", recurso)).isZero();
    }

    @Test
    void confirmarCriaAlocacaoIndisponibilizaRecursoEUmaSegundaConfirmacaoRecebe409() throws Exception {
        UUID recurso = novoRecurso();
        long paciente = novoPaciente(recurso, Instant.now());
        gerarSugestaoRepasse.gerar(agendamentoSeq.incrementAndGet(), recurso, null);
        String sugestaoId = sugestaoPendente(recurso).get("sugestaoId").asText();

        HttpResponse<String> ok = http("POST", "/v1/sugestoes-repasse/" + sugestaoId + "/confirmacao", null);
        assertThat(ok.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(ok.body()).get("pacienteId").asLong()).isEqualTo(paciente);
        assertThat(recursoDisponivel(recurso)).isFalse();
        assertThat(eventos("RepasseConfirmado", recurso)).isEqualTo(1);
        assertThat(http("POST", "/v1/sugestoes-repasse/" + sugestaoId + "/confirmacao", null).statusCode())
                .isEqualTo(409);
        assertThat(http("POST", "/v1/sugestoes-repasse/" + UUID.randomUUID() + "/confirmacao", null).statusCode())
                .isEqualTo(404);
        assertThat(sugestaoPendente(recurso).get("sugestaoId").isNull()).isTrue();
    }

    @Test
    void confirmacoesConcorrentesTemUmaUnicaVencedora() throws Exception {
        UUID recurso = novoRecurso();
        novoPaciente(recurso, Instant.now());
        gerarSugestaoRepasse.gerar(agendamentoSeq.incrementAndGet(), recurso, null);
        String sugestaoId = sugestaoPendente(recurso).get("sugestaoId").asText();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tarefas = java.util.stream.IntStream.range(0, 4)
                    .<Callable<Integer>>mapToObj(i -> () -> http(
                            "POST", "/v1/sugestoes-repasse/" + sugestaoId + "/confirmacao", null).statusCode())
                    .toList();
            List<Integer> status = new java.util.ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tarefas)) {
                status.add(f.get());
            }
            assertThat(status.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(status.stream().filter(s -> s == 409)).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(eventos("RepasseConfirmado", recurso)).isEqualTo(1);
    }

    @Test
    void recusarReatribuiAoProximoDaFilaDepoisEsgotaSemRepetirRecusado() throws Exception {
        UUID recurso = novoRecurso();
        Instant t = Instant.parse("2026-02-01T00:00:00Z");
        long a = novoPaciente(recurso, t);
        long b = novoPaciente(recurso, t.plusSeconds(1));
        gerarSugestaoRepasse.gerar(agendamentoSeq.incrementAndGet(), recurso, null);
        String sugestaoId = sugestaoPendente(recurso).get("sugestaoId").asText();
        String path = "/v1/sugestoes-repasse/" + sugestaoId + "/recusa";

        assertThat(http("POST", path, "{}").statusCode()).isEqualTo(400);

        HttpResponse<String> r1 = http("POST", path, "{\"motivo\":\"paciente desistiu\"}");
        assertThat(r1.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(r1.body()).get("proximoPacienteId").asLong()).isEqualTo(b);
        assertThat(sugestaoPendente(recurso).get("pacienteId").asLong()).isEqualTo(b);

        HttpResponse<String> r2 = http("POST", path, "{\"motivo\":\"sem transporte\"}");
        assertThat(r2.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(r2.body()).get("proximoPacienteId").isNull()).isTrue();
        assertThat(sugestaoPendente(recurso).get("sugestaoId").isNull()).isTrue();
        assertThat(http("POST", path, "{\"motivo\":\"x\"}").statusCode()).isEqualTo(409);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_alocacao.sugestao_recusada "
                + "WHERE recurso_id = ? AND paciente_id IN (?, ?)", Integer.class, recurso, a, b)).isEqualTo(2);

        assertThat(eventos("SugestaoRepasseRecusada", recurso)).isEqualTo(2);
        assertThat(eventos("SugestaoRepasseGerada", recurso)).isEqualTo(2);

        // A recusa vale so para a Vaga: numa nova Vaga do mesmo recurso, a e o primeiro da fila de novo.
        assertThat(gerarSugestaoRepasse.gerar(agendamentoSeq.incrementAndGet(), recurso, null))
                .isEqualTo(Resultado.GERADA);
        assertThat(sugestaoPendente(recurso).get("pacienteId").asLong()).isEqualTo(a);
    }

    @Test
    void novaVagaLiberadaDevolveORecursoAoPoolELiberaAAlocacaoAnterior() throws Exception {
        UUID recurso = novoRecurso();
        long p1 = novoPaciente(recurso, Instant.parse("2026-03-01T00:00:00Z"));
        novoPaciente(recurso, Instant.parse("2026-03-01T00:01:00Z"));
        gerarSugestaoRepasse.gerar(agendamentoSeq.incrementAndGet(), recurso, null);
        String sugestaoId = sugestaoPendente(recurso).get("sugestaoId").asText();
        http("POST", "/v1/sugestoes-repasse/" + sugestaoId + "/confirmacao", null);
        assertThat(recursoDisponivel(recurso)).isFalse();

        assertThat(gerarSugestaoRepasse.gerar(agendamentoSeq.incrementAndGet(), recurso, null))
                .isEqualTo(Resultado.GERADA);

        assertThat(recursoDisponivel(recurso)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM matching_alocacao.alocacao "
                + "WHERE recurso_id = ? AND paciente_id = ?", String.class, recurso, p1)).isEqualTo("LIBERADA");
        // p1 saiu da fila efetiva enquanto tinha alocacao ativa; agora o candidato e o segundo da lista.
        assertThat(sugestaoPendente(recurso).get("pacienteId").asLong()).isNotEqualTo(p1);
    }

    @Test
    void recusaSemContentTypeRecebe415ESemCorpoRecebe400Generico() throws Exception {
        String path = "/v1/sugestoes-repasse/" + UUID.randomUUID() + "/recusa";
        HttpResponse<String> semContentType = client.send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"motivo\":\"x\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(semContentType.statusCode()).isEqualTo(415);

        HttpResponse<String> semCorpo = http("POST", path, null);
        assertThat(semCorpo.statusCode()).isEqualTo(400);
        assertThat(semCorpo.body()).doesNotContain("codigoRecurso");
    }

    @Test
    void recursoInexistenteEIdInvalidoNoGet() throws Exception {
        assertThat(http("GET", "/v1/recursos/" + UUID.randomUUID() + "/sugestao", null).statusCode()).isEqualTo(404);
        assertThat(http("GET", "/v1/recursos/abc/sugestao", null).statusCode()).isEqualTo(400);
    }
}
