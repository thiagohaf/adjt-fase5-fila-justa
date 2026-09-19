package com.filajusta.matching.infrastructure.relay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Achado do code review da spec 3-4a2: nenhum teste subia o contexto Spring
 * com os DOIS relays de SQS ({@code filajusta.matching.relay} -- Story 3.1b
 * -- e {@code filajusta.matching.liberacao-agendada-relay} -- Story 3-4a2)
 * habilitados AO MESMO TEMPO -- que e exatamente a configuracao DEFAULT real
 * de {@code application.yml} ({@code relay.enabled=true} via
 * {@code matchIfMissing} + {@code liberacao-agendada-relay.enabled=true}
 * explicito). Sem este teste, remover o {@code @Primary} acrescentado em
 * {@link ScoreCalculadoSqsClientConfig#sqsClient} (ou qualquer regressao
 * equivalente) quebraria a subida REAL do servico
 * ({@code NoUniqueBeanDefinitionException}, dois beans {@link SqsClient} no
 * mesmo contexto) sem nenhum teste falhar -- todos os demais testes deste
 * modulo desligam explicitamente um dos dois relays.
 *
 * <p>{@code endpoint-override=http://localhost:1} (porta fechada) para os
 * dois relays -- mesmo raciocinio de
 * {@code LiberacaoAgendadaRelayJobConcurrencyIntegrationTest}: nao precisa de
 * LocalStack real, so precisa que os DOIS beans {@link SqsClient} SUBAM
 * (nenhuma chamada de rede e esperada nem verificada aqui); qualquer falha
 * transitoria de {@code sendMessage}/{@code receiveMessage} em background e
 * capturada internamente pelos jobs (nunca propaga, nunca derruba o
 * contexto).
 */
@Testcontainers
@SpringBootTest
class SqsClientPrimaryBeanIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void relayProperties(DynamicPropertyRegistry registry) {
        // filajusta.matching.relay.enabled NAO e setado aqui de proposito --
        // fica no default real de application.yml (true, matchIfMissing),
        // igual producao. queue-url/endpoint-override precisam de um valor
        // (fail-fast do construtor de ScoreCalculadoConsumerJob) -- porta
        // fechada, nenhuma chamada de rede real esperada.
        registry.add("filajusta.matching.relay.endpoint-override", () -> "http://localhost:1");
        registry.add("filajusta.matching.relay.queue-url", () -> "http://localhost:1/000000000000/fake-score");

        // O relay desta story (3-4a2) HABILITADO junto com o de cima --
        // exatamente a configuracao default real de producao (Boundaries da
        // spec 3-4a2, "Ask First": nenhuma decisao adicional).
        registry.add("filajusta.matching.liberacao-agendada-relay.enabled", () -> "true");
        registry.add("filajusta.matching.liberacao-agendada-relay.endpoint-override", () -> "http://localhost:1");
        registry.add("filajusta.matching.liberacao-agendada-relay.queue-url",
                () -> "http://localhost:4566/000000000000/fake");

        // Relay outbox (Story 3-3a) desligado -- fora do escopo deste teste
        // (topic-arn vazio faria RelaySnsPublisherJob falhar ao subir).
        registry.add("filajusta.matching.outbox-relay.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ScoreCalculadoConsumerJob scoreCalculadoConsumerJob;

    @Test
    void comOsDoisRelaysHabilitadosOContextoSobeEOConsumidorDeScoreUsaOSqsClientPrimario() {
        // Os dois beans SqsClient coexistem -- prova que a subida do
        // contexto (linha acima, @Autowired) nao lancou
        // NoUniqueBeanDefinitionException so por sorte/ordem de resolucao;
        // confirma explicitamente que sao DOIS beans distintos.
        SqsClient sqsClientPrimario = applicationContext.getBean("sqsClient", SqsClient.class);
        SqsClient liberacaoAgendadaSqsClient =
                applicationContext.getBean("liberacaoAgendadaSqsClient", SqsClient.class);
        assertThat(sqsClientPrimario).isNotSameAs(liberacaoAgendadaSqsClient);

        // @Primary resolve a ambiguidade: a injecao SEM qualifier de
        // ScoreCalculadoConsumerJob (Story 3.1b, preexistente) precisa
        // continuar resolvendo para o bean "sqsClient" -- nunca para
        // "liberacaoAgendadaSqsClient" -- mesmo com os dois no contexto.
        Object sqsClientInjetado = ReflectionTestUtils.getField(scoreCalculadoConsumerJob, "sqsClient");
        assertThat(sqsClientInjetado)
                .as("ScoreCalculadoConsumerJob deve injetar o SqsClient @Primary (bean \"sqsClient\"), "
                        + "nunca o bean \"liberacaoAgendadaSqsClient\" -- sem @Primary isto nem "
                        + "compilaria/subiria (NoUniqueBeanDefinitionException)")
                .isSameAs(sqsClientPrimario);
    }
}
