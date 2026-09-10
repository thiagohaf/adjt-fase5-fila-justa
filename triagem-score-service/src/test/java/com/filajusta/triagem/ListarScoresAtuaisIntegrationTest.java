package com.filajusta.triagem;

import com.filajusta.triagem.application.command.EventoOutboxRepositorio;
import com.filajusta.triagem.domain.EventoOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre ponta a ponta os 3 cenarios da I/O & Edge-Case Matrix da spec 3.1a,
 * contra um Postgres 18 real via Testcontainers: {@code GET
 * /internal/scores} com o banco vazio, com N Triagens ja registradas via
 * {@code POST /v1/triagens}, e com uma linha do outbox ja marcada como
 * publicada pelo relay.
 *
 * <p>Relay (Story 3.0) desligado -- este endpoint le {@code eventos_outbox}
 * independente de {@code publicado_em} (um Score atual continua atual antes
 * do relay publicar no SNS), entao desligar o relay so evita depender de um
 * SNS real neste teste, sem afetar o que e coberto.
 *
 * <p>{@code @BeforeEach} trunca as tabelas do schema {@code triagem_score}
 * antes de cada teste (achado do code review): o container Postgres e o
 * contexto Spring sao compartilhados entre os metodos {@code @Test} desta
 * classe (cache de contexto do Spring Boot Test), entao sem isso o cenario
 * "banco vazio" so passaria se a ordem de execucao do JUnit calhasse de
 * rodar primeiro -- os outros dois testes tambem quebrariam contando linhas
 * deixadas por execucoes anteriores.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "filajusta.triagem.relay.enabled=false")
class ListarScoresAtuaisIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    private int port;

    // Usado so pelo teste de "Score ja publicado" abaixo, para marcar
    // publicado_em direto (mais simples que subir LocalStack so para isso --
    // padrao ja aceito no servico, ver EventoOutboxJpaRepositoryConcurrencyTest
    // autowireando repositorios direto num teste de integracao).
    @Autowired
    private EventoOutboxRepositorio eventoOutboxRepositorio;

    @Autowired
    private DataSource dataSource;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void limparBanco() throws Exception {
        try (Connection conexao = dataSource.getConnection();
             Statement statement = conexao.createStatement()) {
            statement.execute("TRUNCATE TABLE triagem_score.eventos_outbox, triagem_score.triagens, "
                    + "triagem_score.pacientes RESTART IDENTITY CASCADE");
        }
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

    private HttpResponse<String> listarScoresAtuais() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/scores"))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void bancoVazioRetorna200ComListaVazia() throws Exception {
        HttpResponse<String> response = listarScoresAtuais();

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.isArray()).isTrue();
        assertThat(json).isEmpty();
    }

    @Test
    void triagensRegistradasRetornam200ComListaCompletaDosScoresAtuais() throws Exception {
        HttpResponse<String> registro1 = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "MODERADA", List.of("tosse"));
        assertThat(registro1.statusCode()).isEqualTo(201);
        JsonNode registrado1 = objectMapper.readTree(registro1.body());

        // Mesmo CPF do primeiro registro -- Boundaries da spec 3.1a nao
        // pede deduplicacao por pacienteId ("Retorna todos os Scores
        // atuais", sem paginacao/filtros): 2 Triagens do MESMO paciente
        // devem gerar 2 entradas na lista, uma por evento ScoreCalculado.
        HttpResponse<String> registro2 = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "GRAVE", List.of("falta de ar"));
        assertThat(registro2.statusCode()).isEqualTo(201);
        JsonNode registrado2 = objectMapper.readTree(registro2.body());

        HttpResponse<String> response = listarScoresAtuais();

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.isArray()).isTrue();
        assertThat(json).hasSize(2);

        List<JsonNode> itens = StreamSupport.stream(json.spliterator(), false).toList();

        // MODERADA x GRAVE tem pesos distintos (GravidadePercebida), entao os
        // 2 registros produzem Scores (fatores) distintos -- casamento por
        // igualdade profunda do proprio no "score", nao por um id (a
        // resposta nunca expoe triagemId).
        JsonNode item1 = encontrarPorScore(itens, registrado1.get("score"));
        assertThat(item1.get("pacienteId").asLong()).isEqualTo(registrado1.get("pacienteId").asLong());
        assertThat(item1.get("occurredAt").asText()).isEqualTo(registrado1.get("criadoEm").asText());
        assertThat(item1.get("eventId").asText()).isNotBlank();

        JsonNode item2 = encontrarPorScore(itens, registrado2.get("score"));
        assertThat(item2.get("pacienteId").asLong()).isEqualTo(registrado2.get("pacienteId").asLong());
        assertThat(item2.get("occurredAt").asText()).isEqualTo(registrado2.get("criadoEm").asText());
        assertThat(item2.get("eventId").asText()).isNotBlank();

        // eventId distinto por evento -- nunca reaproveitado entre Triagens.
        assertThat(item1.get("eventId").asText()).isNotEqualTo(item2.get("eventId").asText());
    }

    @Test
    void scoreJaPublicadoPeloRelayContinuaAparecendoNaListagem() throws Exception {
        HttpResponse<String> registro = registrarTriagem(
                "529.982.247-25", sinaisVitaisValidos(), "MODERADA", List.of("tosse"));
        assertThat(registro.statusCode()).isEqualTo(201);
        JsonNode registrado = objectMapper.readTree(registro.body());

        // Simula o relay ja tendo publicado esta linha no SNS (marca
        // publicado_em direto, sem precisar de LocalStack): a I/O Matrix da
        // spec 3.1a nao filtra por publicado_em -- um Score continua "atual"
        // independente de ja ter sido relayado ou nao (achado do code
        // review: nenhum teste cobria isso, um filtro publicadoEm IS NULL
        // adicionado por engano na query nao quebraria nenhum teste).
        List<EventoOutbox> pendentes = eventoOutboxRepositorio.buscarNaoPublicados(10);
        assertThat(pendentes).hasSize(1);
        boolean marcado = eventoOutboxRepositorio.marcarComoPublicado(pendentes.get(0).getId());
        assertThat(marcado).isTrue();

        HttpResponse<String> response = listarScoresAtuais();

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json).hasSize(1);
        assertThat(json.get(0).get("pacienteId").asLong()).isEqualTo(registrado.get("pacienteId").asLong());
        assertThat(json.get(0).get("score")).isEqualTo(registrado.get("score"));
        assertThat(json.get(0).get("eventId").asText()).isNotBlank();
    }

    private static JsonNode encontrarPorScore(List<JsonNode> itens, JsonNode scoreEsperado) {
        return itens.stream()
                .filter(item -> item.get("score").equals(scoreEsperado))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nenhum item com score=" + scoreEsperado));
    }
}
