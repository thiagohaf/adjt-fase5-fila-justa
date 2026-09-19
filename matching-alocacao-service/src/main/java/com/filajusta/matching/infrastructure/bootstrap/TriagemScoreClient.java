package com.filajusta.matching.infrastructure.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Cliente HTTP síncrono para {@code GET /internal/scores} (Story 3.1a,
 * triagem-score-service) -- usado só pelo bootstrap a frio da réplica
 * (Story 3.1c, {@link ScoreBootstrapService}). {@code base-url}
 * configurável via {@code filajusta.matching.bootstrap.base-url}
 * (application.yml, mesmo padrão de host Service Connect do
 * {@code gateway-service} para {@code auth-service}, sem sufixo de
 * namespace) -- nunca hardcoded neste cliente (mesmo princípio de
 * {@code ScoreCalculadoSqsClientConfig}, Story 3.1b).
 *
 * <p>Timeout de conexão/leitura explícito ({@value #TIMEOUT_SECONDS}s) --
 * mesmo achado de code review de {@code ScoreCalculadoSqsClientConfig}: sem
 * timeout, uma chamada pendurada travaria a requisição de
 * {@code GET /v1/fila} por tempo indefinido em vez de falhar rápido e virar
 * {@code 503} (Boundaries da spec 3.1c).
 *
 * <p>Usa o {@link RestClient.Builder} autoconfigurado pelo Spring Boot
 * (bean prototype, já com os conversores JSON corretos do classpath) em vez
 * de montar um {@link RestClient} do zero -- só customiza {@code baseUrl} e
 * o {@code requestFactory} com timeout.
 */
@Component
class TriagemScoreClient {

    private static final int TIMEOUT_SECONDS = 10;

    private final RestClient restClient;

    TriagemScoreClient(RestClient.Builder restClientBuilder,
                        @Value("${filajusta.matching.bootstrap.base-url}") String baseUrl) {
        Duration timeout = Duration.ofSeconds(TIMEOUT_SECONDS);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    List<ScoreInternalDto> buscarScores() {
        ScoreInternalDto[] resposta = restClient.get()
                .uri("/internal/scores")
                .retrieve()
                .body(ScoreInternalDto[].class);
        return resposta == null ? List.of() : List.of(resposta);
    }
}
