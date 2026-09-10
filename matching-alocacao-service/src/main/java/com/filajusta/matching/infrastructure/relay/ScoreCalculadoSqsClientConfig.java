package com.filajusta.matching.infrastructure.relay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Fabrica o {@link SqsClient} usado por {@link ScoreCalculadoConsumerJob}
 * (Story 3.1b, primeiro consumidor SQS real do projeto). Em produção,
 * {@code filajusta.matching.relay.endpoint-override} fica vazio -- o SDK
 * resolve credenciais (variável de ambiente/role da task) pela cadeia
 * default, nunca hardcoded (mesmo Boundary da spec 3.0,
 * {@code RelaySnsClientConfig}). A REGIÃO, diferente das credenciais, NÃO
 * cai na cadeia default do SDK neste serviço (achado do code review, javadoc
 * corrigido): {@code application.yml} sempre popula
 * {@code filajusta.matching.relay.region} (via
 * {@code ${AWS_REGION:us-east-1}}), então esta fábrica sempre chama
 * {@code .region(...)} explicitamente -- {@code us-east-1} é o fallback
 * silencioso sempre que a variável de ambiente {@code AWS_REGION} não está
 * setada no runtime real, nunca a cadeia de resolução de região do SDK.
 *
 * <p>{@code endpoint-override} só é setado no teste de integração com
 * LocalStack (Testcontainers) -- único caso em que uma credencial estática
 * de teste ("test"/"test", exigida pelo SDK para montar a requisição, sem
 * validação real do lado do LocalStack) é usada; nunca contra a conta AWS
 * real.
 *
 * <p>Timeout de API explícito (mesmo achado do code review da Story 3.0):
 * sem {@code apiCallTimeout}/{@code apiCallAttemptTimeout}, uma chamada de
 * rede pendurada travaria a thread do scheduler ({@code @Scheduled}) por
 * tempo indefinido. {@value #API_CALL_TIMEOUT_SECONDS}s (não os 10s
 * herdados do {@code RelaySnsClientConfig} da Story 3.0, que só faz
 * {@code Publish} e nunca long-poll) -- este cliente também faz
 * {@code receiveMessage} com {@code wait-time-seconds} configurável até o
 * teto de 20s do próprio SQS (ver {@link ScoreCalculadoConsumerJob}); um
 * timeout de 10s cortaria a chamada antes do SQS responder sempre que
 * alguém configurar long-polling de verdade (&gt;~9s), gerando falhas
 * "Falha ao receber mensagens" a cada ciclo mesmo sem nenhum problema real
 * -- {@value #API_CALL_TIMEOUT_SECONDS}s folga confortavelmente acima do
 * máximo de 20s.
 *
 * <p>{@code @ConditionalOnProperty} (kill switch,
 * {@code filajusta.matching.relay.enabled}, default {@code true}): usado
 * por testes que não exercitam o consumidor para não criar nenhum
 * {@link SqsClient} nem agendar {@link ScoreCalculadoConsumerJob}.
 */
@Configuration
@ConditionalOnProperty(prefix = "filajusta.matching.relay", name = "enabled", matchIfMissing = true)
class ScoreCalculadoSqsClientConfig {

    // SQS aceita WaitTimeSeconds ate 20s (ver ScoreCalculadoConsumerJob) --
    // 30s folga confortavelmente acima disso, cobrindo tanto o long-poll
    // quanto a latencia normal de rede da chamada em si.
    private static final int API_CALL_TIMEOUT_SECONDS = 30;

    @Bean
    SqsClient sqsClient(@Value("${filajusta.matching.relay.endpoint-override:}") String endpointOverride,
                         @Value("${filajusta.matching.relay.region:}") String region) {
        SqsClientBuilder builder = SqsClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(API_CALL_TIMEOUT_SECONDS))
                        .apiCallAttemptTimeout(Duration.ofSeconds(API_CALL_TIMEOUT_SECONDS))
                        .build());

        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        }
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region));
        }

        return builder.build();
    }
}
