package com.filajusta.matching.infrastructure.relay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um contexto Spring real (Testcontainers-Postgres, sem
 * LocalStack), que {@code filajusta.matching.relay.enabled=false} realmente
 * impede a criação do {@link SqsClient} ({@link ScoreCalculadoSqsClientConfig})
 * e de {@link ScoreCalculadoConsumerJob} -- achado do code review: esse
 * "kill switch" já era usado como efeito colateral por outros testes (ex.:
 * {@code ScoreReplicaRepositorioAdapterIntegrationTest}, só para não
 * precisar de LocalStack), mas nada testava diretamente o
 * {@code @ConditionalOnProperty} em si; uma regressão removendo essa
 * anotação de uma das duas classes não seria pega por nenhum teste
 * existente.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "filajusta.matching.relay.enabled=false")
class ScoreCalculadoConsumerJobConditionalOnPropertyDisabledTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void comORelayDesabilitadoNenhumSqsClientNemConsumidorEhCriadoNoContexto() {
        assertThat(applicationContext.getBeanNamesForType(SqsClient.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(ScoreCalculadoConsumerJob.class)).isEmpty();
    }
}
