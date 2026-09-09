package com.filajusta.triagem.infrastructure.relay;

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
 * Fabrica o {@link SnsClient} usado por {@link RelaySnsPublisherJob} (Story
 * 3.0). Em producao, {@code filajusta.triagem.relay.endpoint-override} fica
 * vazio -- o SDK resolve credenciais (variavel de ambiente/role da task) e
 * regiao pela cadeia default, nunca hardcoded (Boundaries da spec 3.0).
 *
 * <p>{@code endpoint-override} so e setado no teste de integracao com
 * LocalStack (Testcontainers) -- unico caso em que uma credencial estatica
 * de teste ("test"/"test", exigida pelo SDK para montar a requisicao, sem
 * validacao real do lado do LocalStack) e usada; nunca contra a conta AWS
 * real.
 *
 * <p>Timeout de API explicito (achado do code review): sem
 * {@code apiCallTimeout}/{@code apiCallAttemptTimeout}, uma chamada de rede
 * pendurada travaria a thread do scheduler ({@code @Scheduled}) por tempo
 * indefinido -- {@value #API_CALL_TIMEOUT_SECONDS}s cobre uma folga generosa
 * sobre a latencia normal de {@code Publish}, sem travar o poller por muito
 * tempo numa falha real de rede.
 *
 * <p>{@code @ConditionalOnProperty} (kill switch,
 * {@code filajusta.triagem.relay.enabled}, default {@code true}): usado por
 * {@code RegistrarTriagemIntegrationTest}/{@code ConsultarTriagemIntegrationTest}
 * ({@code enabled=false}) para nao criar nenhum {@link SnsClient} nem
 * agendar {@link RelaySnsPublisherJob} -- evita qualquer tentativa de
 * chamada de rede real (regiao/credenciais reais resolviveis, mas nenhum
 * endpoint-override de LocalStack) em testes que nao exercitam o relay.
 */
@Configuration
@ConditionalOnProperty(prefix = "filajusta.triagem.relay", name = "enabled", matchIfMissing = true)
class RelaySnsClientConfig {

    private static final int API_CALL_TIMEOUT_SECONDS = 10;

    @Bean
    SnsClient snsClient(@Value("${filajusta.triagem.relay.endpoint-override:}") String endpointOverride,
                         @Value("${filajusta.triagem.relay.region:}") String region) {
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
