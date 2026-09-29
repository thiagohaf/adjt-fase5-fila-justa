package com.confirmasus.matching;

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
 * Cobre, no nível HTTP, {@code GET /v1/recursos/{id}/sugestao}: HAPPY_PATH
 * (200 com {@code recursoId}/{@code pacienteId} do paciente mais antigo da
 * Lista de Espera do Recurso -- ordem FIFO pura por {@code criadoEm}, AD-6),
 * RECURSO_INDISPONIVEL (200 com {@code pacienteId} null), {@code recursoId}
 * sintaticamente válido mas inexistente (404) e {@code id} não-UUID no path
 * (400). Também cobre o rastreamento AD-10 (transição de sugestão
 * atualizando {@code ultima_sugestao_registrada} e publicando {@code
 * SugestaoGerada} em {@code eventos_outbox}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Relay SQS e relay outbox desligados -- este teste so cobre GET
// /v1/recursos/{id}/sugestao, sem depender de LocalStack/SQS/SNS.
@TestPropertySource(properties = {
        "confirmasus.matching.relay.enabled=false",
        "confirmasus.matching.outbox-relay.enabled=false",
        "confirmasus.matching.liberacao-agendada-relay.enabled=false"
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

    private long seedPaciente() {
        String cpf = String.valueOf(10000000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 89999999999L));
        return jdbcTemplate.queryForObject(
                "INSERT INTO matching_alocacao.paciente (cpf) VALUES (?) RETURNING paciente_id",
                Long.class, cpf);
    }

    private void seedEntradaListaEspera(long pacienteId, UUID recursoId, Instant criadoEm) {
        jdbcTemplate.update(
                "INSERT INTO matching_alocacao.lista_espera_entrada "
                        + "(paciente_id, recurso_id, data_solicitacao, criado_em) VALUES (?, ?, ?, ?)",
                pacienteId, recursoId, Timestamp.from(criadoEm), Timestamp.from(criadoEm));
    }

    private void seedSugestaoRecusada(UUID recursoId, long pacienteId) {
        jdbcTemplate.update(
                "INSERT INTO matching_alocacao.sugestao_recusada "
                        + "(recurso_id, paciente_id, motivo, recusado_em) VALUES (?, ?, ?, ?)",
                recursoId, pacienteId, "sem leitos disponiveis na especialidade",
                Timestamp.from(Instant.now()));
    }

    private void seedAlocacaoAtiva(UUID recursoId, long pacienteId) {
        jdbcTemplate.update(
                "INSERT INTO matching_alocacao.alocacao "
                        + "(alocacao_id, recurso_id, paciente_id, status, confirmado_em) "
                        + "VALUES (?, ?, ?, 'ATIVA', ?)",
                UUID.randomUUID(), recursoId, pacienteId, Timestamp.from(Instant.now()));
    }

    // Rastreamento AD-10: helpers de leitura direta via JdbcTemplate.

    private Long ultimaSugestaoRegistradaPacienteId(UUID recursoId) {
        return jdbcTemplate.query(
                "SELECT paciente_id FROM matching_alocacao.ultima_sugestao_registrada WHERE recurso_id = ?::uuid",
                rs -> rs.next() ? rs.getLong("paciente_id") : null,
                recursoId.toString());
    }

    private Timestamp ultimaSugestaoRegistradaEm(UUID recursoId) {
        return jdbcTemplate.query(
                "SELECT registrado_em FROM matching_alocacao.ultima_sugestao_registrada WHERE recurso_id = ?::uuid",
                rs -> rs.next() ? rs.getTimestamp("registrado_em") : null,
                recursoId.toString());
    }

    private int contarEventosSugestaoGeradaPendentes(UUID recursoId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_alocacao.eventos_outbox "
                        + "WHERE event_type = 'SugestaoGerada' AND publicado_em IS NULL "
                        + "AND payload ->> 'recursoId' = ?",
                Integer.class, recursoId.toString());
    }

    private Long ultimoEventoSugestaoGeradaPacienteId(UUID recursoId) {
        return jdbcTemplate.queryForObject(
                "SELECT (payload ->> 'pacienteId')::bigint FROM matching_alocacao.eventos_outbox "
                        + "WHERE event_type = 'SugestaoGerada' AND payload ->> 'recursoId' = ? "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class, recursoId.toString());
    }

    @Test
    void recursoDisponivelComListaDeEsperaRetorna200ComOPacienteMaisAntigo() throws Exception {
        UUID recursoId = upsertRecurso(1, true);
        long pacienteMaisAntigo = seedPaciente();
        long pacienteMaisRecente = seedPaciente();
        seedEntradaListaEspera(pacienteMaisAntigo, recursoId, Instant.parse("2026-09-11T10:00:00Z"));
        seedEntradaListaEspera(pacienteMaisRecente, recursoId, Instant.parse("2026-09-11T12:00:00Z"));

        HttpResponse<String> resposta = consultarSugestao(recursoId.toString());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/json"));

        JsonNode json = objectMapper.readTree(resposta.body());
        assertThat(json.get("recursoId").asText()).isEqualTo(recursoId.toString());
        assertThat(json.get("pacienteId").asLong())
                .as("ordem FIFO pura por criadoEm -- nunca por gravidade/score (AD-6)")
                .isEqualTo(pacienteMaisAntigo);
    }

    @Test
    void pacienteRecusadoParaORecursoEPuladoNaSugestaoDoTopoDaListaDeEspera() throws Exception {
        UUID recursoId = upsertRecurso(1, true);
        long pacienteRecusado = seedPaciente();
        long pacienteElegivel = seedPaciente();
        seedEntradaListaEspera(pacienteRecusado, recursoId, Instant.parse("2026-09-11T10:00:00Z"));
        seedEntradaListaEspera(pacienteElegivel, recursoId, Instant.parse("2026-09-11T11:00:00Z"));
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

    @Test
    void sugestaoQueMudaDeAParaBAtualizaORegistroEPublica2EventosSugestaoGerada() throws Exception {
        UUID recursoId = upsertRecurso(1, true);
        long pacienteA = seedPaciente();
        long pacienteB = seedPaciente();
        seedEntradaListaEspera(pacienteA, recursoId, Instant.parse("2026-09-11T10:00:00Z"));
        seedEntradaListaEspera(pacienteB, recursoId, Instant.parse("2026-09-11T11:00:00Z"));

        HttpResponse<String> primeira = consultarSugestao(recursoId.toString());
        assertThat(primeira.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(primeira.body()).get("pacienteId").asLong()).isEqualTo(pacienteA);
        assertThat(ultimaSugestaoRegistradaPacienteId(recursoId)).isEqualTo(pacienteA);
        assertThat(contarEventosSugestaoGeradaPendentes(recursoId)).isEqualTo(1);
        assertThat(ultimoEventoSugestaoGeradaPacienteId(recursoId)).isEqualTo(pacienteA);

        // pacienteA sai da Lista de Espera "candidata" por ja ter sido
        // alocado a outro Recurso -- pacienteB (proximo por criadoEm) assume.
        seedAlocacaoAtiva(UUID.randomUUID(), pacienteA);

        HttpResponse<String> segunda = consultarSugestao(recursoId.toString());
        assertThat(segunda.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(segunda.body()).get("pacienteId").asLong()).isEqualTo(pacienteB);
        assertThat(ultimaSugestaoRegistradaPacienteId(recursoId))
                .as("upsert pela PK recurso_id -- atualiza a MESMA linha, nunca duplica")
                .isEqualTo(pacienteB);
        assertThat(contarEventosSugestaoGeradaPendentes(recursoId))
                .as("2 transicoes reais (null->A, A->B) -- 2 eventos SugestaoGerada distintos")
                .isEqualTo(2);
        assertThat(ultimoEventoSugestaoGeradaPacienteId(recursoId)).isEqualTo(pacienteB);
    }

    @Test
    void sugestaoQueRepeteOUltimoRegistroNaoGravaNovaLinhaNemPublicaNovoEvento() throws Exception {
        UUID recursoId = upsertRecurso(1, true);
        long pacienteC = seedPaciente();
        seedEntradaListaEspera(pacienteC, recursoId, Instant.parse("2026-09-11T10:00:00Z"));

        HttpResponse<String> primeira = consultarSugestao(recursoId.toString());
        assertThat(primeira.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(primeira.body()).get("pacienteId").asLong()).isEqualTo(pacienteC);
        assertThat(contarEventosSugestaoGeradaPendentes(recursoId)).isEqualTo(1);
        Timestamp registradoEmAntes = ultimaSugestaoRegistradaEm(recursoId);

        HttpResponse<String> segunda = consultarSugestao(recursoId.toString());

        assertThat(segunda.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(segunda.body()).get("pacienteId").asLong()).isEqualTo(pacienteC);
        assertThat(ultimaSugestaoRegistradaPacienteId(recursoId)).isEqualTo(pacienteC);
        assertThat(ultimaSugestaoRegistradaEm(recursoId))
                .as("compare-and-set nao altera nada quando o valor repete -- registrado_em intocado")
                .isEqualTo(registradoEmAntes);
        assertThat(contarEventosSugestaoGeradaPendentes(recursoId))
                .as("nenhuma nova linha de evento quando a sugestao repete")
                .isEqualTo(1);
    }

    @Test
    void listaDeEsperaEsgotaAposSugestaoAnteriorRegistradaNaoAlteraRegistroNemPublicaNovoEvento() throws Exception {
        UUID recursoId = upsertRecurso(1, true);
        long pacienteD = seedPaciente();
        seedEntradaListaEspera(pacienteD, recursoId, Instant.parse("2026-09-11T10:00:00Z"));

        HttpResponse<String> primeira = consultarSugestao(recursoId.toString());
        assertThat(primeira.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(primeira.body()).get("pacienteId").asLong()).isEqualTo(pacienteD);
        assertThat(ultimaSugestaoRegistradaPacienteId(recursoId)).isEqualTo(pacienteD);
        assertThat(contarEventosSugestaoGeradaPendentes(recursoId)).isEqualTo(1);
        Timestamp registradoEmAntes = ultimaSugestaoRegistradaEm(recursoId);

        // pacienteD e o UNICO paciente na Lista de Espera deste Recurso --
        // marca-lo com Alocacao ATIVA esgota a lista de candidatos por
        // inteiro.
        seedAlocacaoAtiva(UUID.randomUUID(), pacienteD);

        HttpResponse<String> segunda = consultarSugestao(recursoId.toString());

        assertThat(segunda.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(segunda.body()).get("pacienteId").isNull())
                .as("lista de espera esgotada -- pacienteId deve ser JSON null, nunca erro")
                .isTrue();
        assertThat(ultimaSugestaoRegistradaPacienteId(recursoId))
                .as("registro anterior (pacienteD) permanece intocado quando a nova sugestao e null")
                .isEqualTo(pacienteD);
        assertThat(ultimaSugestaoRegistradaEm(recursoId)).isEqualTo(registradoEmAntes);
        assertThat(contarEventosSugestaoGeradaPendentes(recursoId))
                .as("nenhum evento novo quando pacienteIdSugerido e null")
                .isEqualTo(1);
    }
}
