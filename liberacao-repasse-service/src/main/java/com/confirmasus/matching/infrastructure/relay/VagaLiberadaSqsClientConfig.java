package com.confirmasus.matching.infrastructure.relay;

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
 * {@link SqsClient} de {@link VagaLiberadaSqsConsumerJob} (Story 6.1), mesmo
 * padrão de {@link RelaySnsClientConfig}. Só existe com o kill switch
 * {@code confirmasus.matching.vaga-liberada-consumer.enabled=true}.
 * {@code endpoint-override} (LocalStack) usa credencial estática de teste;
 * em produção o SDK resolve credenciais/região pela cadeia default.
 */
@Configuration
@ConditionalOnProperty(prefix = "confirmasus.matching.vaga-liberada-consumer", name = "enabled")
class VagaLiberadaSqsClientConfig {

    // Acima do long polling maximo (20s) para nao abortar um ReceiveMessage legitimo.
    private static final int API_CALL_TIMEOUT_SECONDS = 30;

    @Bean
    SqsClient sqsClient(
            @Value("${confirmasus.matching.vaga-liberada-consumer.endpoint-override:}") String endpointOverride,
            @Value("${confirmasus.matching.vaga-liberada-consumer.region:}") String region) {
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
