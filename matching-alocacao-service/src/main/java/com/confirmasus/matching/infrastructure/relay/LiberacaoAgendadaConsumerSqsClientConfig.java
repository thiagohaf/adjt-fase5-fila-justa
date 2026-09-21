package com.confirmasus.matching.infrastructure.relay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * Configuração alternativa do {@link SqsClient} para o consumidor
 * {@link LiberacaoAgendadaSqsConsumerJob} (Story 3-4b2): criada somente
 * quando o consumidor está habilitado MAS o relay de publicação
 * ({@link LiberacaoAgendadaRelayJob}, Story 3-4a2) não está. Ambos precisam
 * do mesmo bean {@code liberacaoAgendadaSqsClient}, então:
 *
 * <p>Caso 1: Relay habilitado (Story 3-4a2) → {@link LiberacaoAgendadaSqsClientConfig}
 * cria {@code liberacaoAgendadaSqsClient}; Consumer reutiliza via @Qualifier
 *
 * <p>Caso 2: Consumer habilitado (Story 3-4b2) mas relay desabilitado →
 * Esta config ({@link LiberacaoAgendadaConsumerSqsClientConfig}) cria
 * {@code liberacaoAgendadaSqsClient} se não existir (@ConditionalOnMissingBean)
 *
 * <p>Padrão idêntico ao de {@link LiberacaoAgendadaSqsClientConfig} (mesmo
 * timeout, creds via endpoint-override em testes).
 */
@Configuration
@ConditionalOnProperty(prefix = "confirmasus.matching.liberacao-agendada-consumer", name = "enabled")
class LiberacaoAgendadaConsumerSqsClientConfig {

    private static final int API_CALL_TIMEOUT_SECONDS = 15;

    @Bean
    @ConditionalOnMissingBean(name = "liberacaoAgendadaSqsClient")
    SqsClient liberacaoAgendadaSqsClient(
            @Value("${confirmasus.matching.liberacao-agendada-consumer.endpoint-override:}") String endpointOverride,
            @Value("${confirmasus.matching.liberacao-agendada-consumer.region:}") String region) {
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
