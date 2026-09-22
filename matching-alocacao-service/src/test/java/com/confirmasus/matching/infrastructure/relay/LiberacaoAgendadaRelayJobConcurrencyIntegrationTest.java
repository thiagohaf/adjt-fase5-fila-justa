package com.confirmasus.matching.infrastructure.relay;

import com.confirmasus.matching.application.command.LiberacaoAgendadaRepositorio;
import com.confirmasus.matching.domain.LiberacaoAgendada;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers e uma fila SQS
 * standard real via Testcontainers-LocalStack, a segunda Acceptance Criteria
 * da spec 3-4a2 (cenario CONCORRENCIA_2_INSTANCIAS da I/O &amp; Edge-Case
 * Matrix): "Given 2 execucoes concorrentes do job sobre a mesma linha
 * pendente, when ambas rodam, then exatamente uma publica e marca
 * enviado_em". Molde de
 * {@code UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest
 * #registrosConcorrentesParaOMesmoValorNovoApenasUmDelesRetornaTrue}
 * (ExecutorService/CountDownLatch, N chamadas concorrentes contra um Postgres
 * real).
 *
 * <p>N threads chamando {@code job.publicarPendentes()} concorrentemente no
 * MESMO bean Spring simula "N instancias do job disparando ao mesmo tempo":
 * cada chamada, por ser {@code @Transactional} (proxy Spring), abre sua
 * PROPRIA transacao/conexao -- exatamente a mesma condicao de corrida entre
 * processos distintos que o {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * (Boundaries da spec 3-4a2) precisa resolver. {@code batch-size=1}: cada
 * chamada so pode enxergar 1 linha pendente por vez, isolando a corrida na
 * UNICA linha pendente do teste.
 *
 * <p>Diferente de {@link LiberacaoAgendadaRelayJobIntegrationTest} (LocalStack
 * real tambem, mas 1 unica execucao), aqui a fila real prova a AC de forma
 * DIRETA e completa: exatamente 1 mensagem chega na fila (nunca 8, nem 0) E
 * {@code enviado_em} fica gravado -- SKIP LOCKED garante que so a transacao
 * vencedora le+envia+marca a linha; as demais 7 threads sempre veem lista
 * vazia (SKIP LOCKED as pula, quer a vencedora ja tenha comitado, quer ainda
 * esteja com o lock aberto).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiberacaoAgendadaRelayJobConcurrencyIntegrationTest {

    private static final String FILA = "liberacao-agendada-concurrency-test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    // 4.12.0 (nao a numeracao "2026.MM"): a partir da nova numeracao por
    // calendario a imagem community exige LOCALSTACK_AUTH_TOKEN mesmo so
    // para SQS -- mesmo achado documentado em RelaySnsPublisherJobIntegrationTest.
    @Container
    static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:4.12.0")
            .withServices("sqs");

    private static String queueUrl;

    @DynamicPropertySource
    static void relayProperties(DynamicPropertyRegistry registry) {
        try (SqsClient sqs = sqsClient()) {
            queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                            .queueName(FILA)
                            .build())
                    .queueUrl();
        }

        registry.add("confirmasus.matching.liberacao-agendada-relay.enabled", () -> "true");
        registry.add("confirmasus.matching.liberacao-agendada-relay.endpoint-override",
                () -> localstack.getEndpoint().toString());
        registry.add("confirmasus.matching.liberacao-agendada-relay.region", localstack::getRegion);
        registry.add("confirmasus.matching.liberacao-agendada-relay.queue-url", () -> queueUrl);
        // batch-size=1: cada chamada so pode enxergar 1 linha pendente por
        // vez -- isola a corrida de leitura na UNICA linha do teste (ver
        // javadoc da classe).
        registry.add("confirmasus.matching.liberacao-agendada-relay.batch-size", () -> "1");
        registry.add("confirmasus.matching.relay.enabled", () -> "false");
        registry.add("confirmasus.matching.outbox-relay.enabled", () -> "false");
    }

    private static SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
    }

    @Autowired
    private LiberacaoAgendadaRelayJob job;

    @Autowired
    private LiberacaoAgendadaRepositorio liberacaoAgendadaRepositorio;

    @Test
    void execucoesConcorrentesSobreAMesmaLinhaPendenteApenasUmaAPublicaEMarca() throws Exception {
        UUID alocacaoId = UUID.randomUUID();
        // delaySegundos baixo (1s): so precisa ser positivo (validacao do
        // dominio) e deixar o teste rapido -- o valor em si nao e o que esta
        // sendo provado aqui (ja coberto por
        // LiberacaoAgendadaRelayJobIntegrationTest).
        liberacaoAgendadaRepositorio.salvar(new LiberacaoAgendada(
                alocacaoId, UUID.randomUUID(), "corr-" + alocacaoId, 1, Instant.now(), null));

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Void>> resultados = new ArrayList<>();
            for (int i = 0; i < totalThreads; i++) {
                resultados.add(executor.submit(() -> {
                    partida.await();
                    job.publicarPendentes();
                    return null;
                }));
            }

            partida.countDown();
            List<Exception> falhas = new ArrayList<>();
            for (Future<Void> resultado : resultados) {
                try {
                    resultado.get(15, TimeUnit.SECONDS);
                } catch (Exception e) {
                    falhas.add(e);
                }
            }
            assertThat(falhas)
                    .as("nenhuma execucao concorrente do job pode propagar excecao "
                            + "(mesma garantia de nunca derrubar a app)")
                    .isEmpty();
        } finally {
            executor.shutdown();
        }

        List<Message> mensagens = drenarFila();
        assertThat(mensagens)
                .as("exatamente 1 das %d execucoes concorrentes pode publicar a MESMA liberacao pendente -- "
                        + "SKIP LOCKED fecha a corrida de leitura entre instancias do job", totalThreads)
                .hasSize(1);
        assertThat(mensagens.get(0).body()).contains(alocacaoId.toString());

        Optional<LiberacaoAgendada> liberacao = liberacaoAgendadaRepositorio.buscarPendentes(100).stream()
                .filter(candidata -> candidata.getAlocacaoId().equals(alocacaoId))
                .findFirst();
        assertThat(liberacao)
                .as("a linha publicada com sucesso deve sair de buscarPendentes -- enviado_em foi gravado")
                .isEmpty();
    }

    private List<Message> drenarFila() throws InterruptedException {
        List<Message> recebidas = new ArrayList<>();
        long limite = System.currentTimeMillis() + 15_000;
        try (SqsClient sqs = sqsClient()) {
            // Espera a 1a mensagem ficar visivel (delaySegundos=1 no teste).
            while (recebidas.isEmpty() && System.currentTimeMillis() < limite) {
                recebidas.addAll(sqs.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(10)
                                .waitTimeSeconds(1)
                                .build())
                        .messages());
            }
            // Uma rodada extra curta confirma que nenhuma segunda mensagem
            // (de outra thread que tambem tivesse conseguido publicar)
            // chega logo em seguida.
            recebidas.addAll(sqs.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(2)
                            .build())
                    .messages());
        }
        return recebidas;
    }
}
