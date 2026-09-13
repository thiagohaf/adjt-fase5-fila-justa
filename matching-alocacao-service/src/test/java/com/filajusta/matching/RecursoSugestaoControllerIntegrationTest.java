package com.filajusta.matching;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
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
 *
 * <p>Desde a Story 3-3c2b2, também cobre o rastreamento AD-10 a nível
 * HTTP/Postgres real: transição de sugestão {@code A→B} atualizando {@code
 * ultima_sugestao_registrada} e publicando 2 linhas {@code SugestaoGerada}
 * em {@code eventos_outbox}, sugestão repetida sem nova escrita/evento, fila
 * esgotando depois de um registro anterior (registro permanece intocado,
 * nenhum evento novo) e bootstrap a frio (réplica vazia) através deste
 * mesmo endpoint -- prova que {@code @Transactional} não-{@code readOnly}
 * de {@code ConsultarSugestaoRecurso} aceita a escrita de bootstrap de
 * {@code ScoreReplicaRepositorioAdapter#upsertSeMaisRecente} quando ela
 * participa da mesma transação (achado do verification-gap do review
 * multi-agente: esse caminho nunca era exercitado por nenhum teste antes).
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

    // WireMock so e usado pelo cenario de bootstrap a frio (score_replica
    // vazia) -- mesmo papel/padrao de FilaBootstrapIntegrationTest, unico
    // outro precedente no projeto. As demais classes de teste desta classe
    // nunca disparam o bootstrap porque sempre semeiam score_replica direto
    // via JdbcTemplate antes de chamar o endpoint (replica nunca vazia).
    private static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMockServer.start();
    }

    @AfterAll
    static void pararWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void bootstrapProperties(DynamicPropertyRegistry registry) {
        registry.add("filajusta.matching.bootstrap.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetarWireMock() {
        wireMockServer.resetAll();
    }

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

    // Story 3-3c2b2 (rastreamento AD-10): helpers de leitura direta via
    // JdbcTemplate, molde de AlocacaoControllerIntegrationTest:229-255.

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

    private String ultimoEventoSugestaoGeradaSugeridoEm(UUID recursoId) {
        return jdbcTemplate.queryForObject(
                "SELECT payload ->> 'sugeridoEm' FROM matching_alocacao.eventos_outbox "
                        + "WHERE event_type = 'SugestaoGerada' AND payload ->> 'recursoId' = ? "
                        + "ORDER BY id DESC LIMIT 1",
                String.class, recursoId.toString());
    }

    private void seedAlocacaoAtiva(UUID recursoId, long pacienteId) {
        jdbcTemplate.update(
                "INSERT INTO matching_alocacao.alocacao "
                        + "(alocacao_id, recurso_id, paciente_id, status, confirmado_em) "
                        + "VALUES (?, ?, ?, 'ATIVA', ?)",
                UUID.randomUUID(), recursoId, pacienteId, Timestamp.from(Instant.now()));
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

    // Story 3-3c2b2 (rastreamento AD-10): cenarios de GET
    // /v1/recursos/{id}/sugestao a nivel HTTP/Postgres real cobrindo
    // ultima_sugestao_registrada + eventos_outbox (event_type=
    // 'SugestaoGerada'). Ao contrario dos demais testes desta classe, os 3
    // cenarios abaixo TRUNCAM score_replica antes (isolamento total da fila
    // global, necessario para controlar quem e o topo com precisao) E
    // depois (para nao deixar nenhum paciente residual atrapalhando o
    // truque de "score bem acima" dos testes HAPPY_PATH/pulo-de-recusados
    // pre-existentes, que dependem da tabela acumulada mas nunca truncada).

    @Test
    void sugestaoQueMudaDeAParaBAtualizaORegistroEPublica2EventosSugestaoGerada() throws Exception {
        // Transicao real A->B (nao so o primeiro registro): 2a chamada com
        // um paciente de score maior assume o topo da fila global.
        jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        try {
            long pacienteA = 700001L;
            long pacienteB = 700002L;
            seedScoreReplica(pacienteA, 40);
            UUID recursoId = upsertRecurso(1, true);

            HttpResponse<String> primeira = consultarSugestao(recursoId.toString());
            assertThat(primeira.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(primeira.body()).get("pacienteId").asLong()).isEqualTo(pacienteA);
            assertThat(ultimaSugestaoRegistradaPacienteId(recursoId)).isEqualTo(pacienteA);
            assertThat(contarEventosSugestaoGeradaPendentes(recursoId)).isEqualTo(1);
            assertThat(ultimoEventoSugestaoGeradaPacienteId(recursoId)).isEqualTo(pacienteA);

            seedScoreReplica(pacienteB, 90);

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

            // Achado de revisao de codigo: os helpers acima so conferiam
            // pacienteId do payload, nunca sugeridoEm -- confirma aqui que o
            // campo foi de fato gravado (nao valida o instante exato, so que
            // e uma string ISO-8601 parseavel, nunca nulo/vazio).
            String sugeridoEm = ultimoEventoSugestaoGeradaSugeridoEm(recursoId);
            assertThat(sugeridoEm).isNotBlank();
            assertThat(Instant.parse(sugeridoEm)).isNotNull();
        } finally {
            jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        }
    }

    @Test
    void sugestaoQueRepeteOUltimoRegistroNaoGravaNovaLinhaNemPublicaNovoEvento() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        try {
            long pacienteC = 700003L;
            seedScoreReplica(pacienteC, 50);
            UUID recursoId = upsertRecurso(1, true);

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
        } finally {
            jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        }
    }

    @Test
    void filaEsgotaAposSugestaoAnteriorRegistradaNaoAlteraRegistroNemPublicaNovoEvento() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        try {
            long pacienteD = 700004L;
            seedScoreReplica(pacienteD, 60);
            UUID recursoId = upsertRecurso(1, true);

            HttpResponse<String> primeira = consultarSugestao(recursoId.toString());
            assertThat(primeira.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(primeira.body()).get("pacienteId").asLong()).isEqualTo(pacienteD);
            assertThat(ultimaSugestaoRegistradaPacienteId(recursoId)).isEqualTo(pacienteD);
            assertThat(contarEventosSugestaoGeradaPendentes(recursoId)).isEqualTo(1);
            Timestamp registradoEmAntes = ultimaSugestaoRegistradaEm(recursoId);

            // Esgota a fila global de verdade (nao RECURSO_INDISPONIVEL):
            // score_replica foi truncada acima, entao pacienteD e o UNICO
            // paciente na fila -- marca-lo com Alocacao ATIVA basta para
            // esvaziar a fila inteira (ConsultarFilaPriorizada, reutilizado
            // por ConsultarSugestaoRecurso, o exclui).
            seedAlocacaoAtiva(UUID.randomUUID(), pacienteD);

            HttpResponse<String> segunda = consultarSugestao(recursoId.toString());

            assertThat(segunda.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(segunda.body()).get("pacienteId").isNull())
                    .as("fila global esgotada -- pacienteId deve ser JSON null, nunca erro")
                    .isTrue();
            assertThat(ultimaSugestaoRegistradaPacienteId(recursoId))
                    .as("registro anterior (pacienteD) permanece intocado quando a nova sugestao e null")
                    .isEqualTo(pacienteD);
            assertThat(ultimaSugestaoRegistradaEm(recursoId)).isEqualTo(registradoEmAntes);
            assertThat(contarEventosSugestaoGeradaPendentes(recursoId))
                    .as("nenhum evento novo quando pacienteIdSugerido e null")
                    .isEqualTo(1);
        } finally {
            jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        }
    }

    @Test
    void bootstrapAFrioViaEsteEndpointRegistraSugestaoEPublicaEventoNaMesmaTransacaoDaEscritaDeBootstrap()
            throws Exception {
        // Achado do verification-gap (code review multi-agente da spec
        // 3-3c2b2): o caminho "replica vazia -> bootstrap sincrono dentro
        // da MESMA transacao nao-readOnly de ConsultarSugestaoRecurso"
        // nunca era exercitado por nenhum teste. Se @Transactional fosse
        // readOnly=true, o Postgres rejeitaria a escrita de
        // ScoreReplicaRepositorioAdapter#upsertSeMaisRecente (mesmo risco
        // documentado em FilaRepositorioAdapter) e este teste falharia --
        // aqui a prova e o 200 com o registro/evento AD-10 tambem gravados,
        // nao so a resposta HTTP.
        jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        try {
            long pacienteBootstrap = 800001L;
            String corpoInternalScores = """
                    [
                      {
                        "pacienteId": %d,
                        "score": {"valor": 88, "algoritmoVersao": "v1", "fatores": []},
                        "occurredAt": "2026-09-12T12:00:00Z",
                        "eventId": "66666666-6666-6666-6666-666666666666"
                      }
                    ]
                    """.formatted(pacienteBootstrap);
            wireMockServer.stubFor(get(urlEqualTo("/internal/scores"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(corpoInternalScores)));

            UUID recursoId = upsertRecurso(1, true);

            HttpResponse<String> resposta = consultarSugestao(recursoId.toString());

            assertThat(resposta.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(resposta.body()).get("pacienteId").asLong())
                    .isEqualTo(pacienteBootstrap);
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/internal/scores")));

            Integer linhasReplica = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM matching_alocacao.score_replica WHERE paciente_id = ?",
                    Integer.class, pacienteBootstrap);
            assertThat(linhasReplica)
                    .as("bootstrap upsertou de fato a replica (nao so um retorno de mentirinha)")
                    .isEqualTo(1);

            assertThat(ultimaSugestaoRegistradaPacienteId(recursoId)).isEqualTo(pacienteBootstrap);
            assertThat(contarEventosSugestaoGeradaPendentes(recursoId)).isEqualTo(1);
            assertThat(ultimoEventoSugestaoGeradaPacienteId(recursoId)).isEqualTo(pacienteBootstrap);
        } finally {
            // Limpeza: nao deixar o paciente do bootstrap (score 88) residual
            // na replica -- os testes HAPPY_PATH/pulo-de-recusados desta
            // classe dependem de scores especificos (80/100/99) para
            // determinar o topo da fila acumulada, sem truncar entre si.
            jdbcTemplate.execute("TRUNCATE TABLE matching_alocacao.score_replica");
        }
    }
}
