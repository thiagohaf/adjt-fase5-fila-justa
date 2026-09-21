package com.confirmasus.agendamento.infrastructure.relay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Fabrica o {@link SnsClient} usado por {@link RelaySnsPublisherJob} (spec
 * 1.2) -- copia adaptada de {@code matching-alocacao-service/.../
 * infrastructure/relay/RelaySnsClientConfig.java}. Em producao,
 * {@code confirmasus.agendamento.outbox-relay.endpoint-override} fica vazio --
 * o SDK resolve credenciais (variavel de ambiente/role da task) e regiao
 * pela cadeia default, nunca hardcoded.
 *
 * <p>{@code endpoint-override} so e setado no teste de integracao com
 * LocalStack (Testcontainers) -- unico caso em que uma credencial estatica
 * de teste ("test"/"test", exigida pelo SDK para montar a requisicao, sem
 * validacao real do lado do LocalStack) e usada; nunca contra a conta AWS
 * real.
 *
 * <p>Timeout de API explicito (mesmo achado do code review do molde): sem
 * {@code apiCallTimeout}/{@code apiCallAttemptTimeout}, uma chamada de rede
 * pendurada travaria a thread do scheduler ({@code @Scheduled}) por tempo
 * indefinido -- {@value #API_CALL_TIMEOUT_SECONDS}s cobre uma folga generosa
 * sobre a latencia normal de {@code Publish}.
 *
 * <p>{@code @ConditionalOnProperty} (kill switch,
 * {@code confirmasus.agendamento.outbox-relay.enabled}, default {@code true}
 * em {@code application.yml}): os testes {@code @SpringBootTest} que nao
 * exercitam este relay desligam {@code outbox-relay.enabled=false}
 * explicitamente; so o teste de integracao do relay liga explicitamente com
 * um topico LocalStack.
 */
@Configuration
@ConditionalOnProperty(prefix = "confirmasus.agendamento.outbox-relay", name = "enabled", matchIfMissing = false)
class RelaySnsClientConfig {

    private static final int API_CALL_TIMEOUT_SECONDS = 10;

    @Bean
    SnsClient snsClient(@Value("${confirmasus.agendamento.outbox-relay.endpoint-override:}") String endpointOverride,
                         @Value("${confirmasus.agendamento.outbox-relay.region:}") String region) {
        SnsClientBuilder builder = SnsClient.builder()
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
