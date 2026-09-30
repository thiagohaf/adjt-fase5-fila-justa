package com.confirmasus.auditoria.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

/**
 * Configuração do cliente SQS para auditoria-service.
 *
 * <p>Cria um {@link SqsClient} único (singleton) que é injetado no consumer job.
 *
 * <p>{@code @ConditionalOnProperty} -- apenas cria o bean se
 * {@code confirmasus.auditoria.relay.enabled = true} (default).
 * Permite desabilitar o consumidor em ambientes de teste ou desenvolvimento.
 */
@Configuration
class DecisaoSqsClientConfig {

    /**
     * Cria um cliente SQS default (credenciais via AWS SDK default providers:
     * env vars, sistema de perfis ~/.aws, credentials provider chain).
     *
     * <p>{@code endpoint-override} (LocalStack) aponta o cliente para o
     * endpoint local; vazio (default) usa o endpoint regional da AWS.
     *
     * @return cliente SQS thread-safe
     */
    @Bean
    @ConditionalOnProperty(prefix = "confirmasus.auditoria.relay", name = "enabled", matchIfMissing = true)
    public SqsClient sqsClient(
            @Value("${confirmasus.auditoria.relay.endpoint-override:}") String endpointOverride) {
        SqsClientBuilder builder = SqsClient.builder();
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
