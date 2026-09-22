package com.confirmasus.auditoria.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

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
     * @return cliente SQS thread-safe
     */
    @Bean
    @ConditionalOnProperty(prefix = "confirmasus.auditoria.relay", name = "enabled", matchIfMissing = true)
    SqsClient sqsClient() {
        return SqsClient.builder().build();
    }
}
