package com.filajusta.triagem.infrastructure.relay;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link RelaySnsClientConfig} sem subir contexto Spring (chama o
 * metodo do {@code @Bean} direto). Achado do code review: so o branch com
 * LocalStack ({@code endpoint-override} setado) era exercitado por algum
 * teste ate aqui -- o branch de producao (sem override, cadeia default de
 * credenciais/regiao) nunca era provado, mascarado pelo LINE coverage do
 * JaCoCo (as linhas depois do {@code if} sempre executam, independente do
 * branch tomado). Regiao sempre passada explicitamente nos dois testes
 * (nunca deixada em branco) para o teste nao depender do ambiente onde roda
 * (perfil AWS local, variavel de ambiente etc.) -- isso e uma preocupacao
 * ortogonal ao que este teste prova.
 */
class RelaySnsClientConfigTest {

    private final RelaySnsClientConfig config = new RelaySnsClientConfig();

    @Test
    void semEndpointOverrideUsaACadeiaDefaultDeCredenciaisNuncaACredencialEstaticaDeTeste() {
        try (SnsClient client = config.snsClient("", "us-east-1")) {
            var configuracao = client.serviceClientConfiguration();

            assertThat(configuracao.endpointOverride()).isEmpty();
            assertThat(configuracao.region()).isEqualTo(Region.of("us-east-1"));
            // Nunca a credencial estatica "test"/"test" usada so para
            // LocalStack (Boundaries da spec 3.0: credenciais AWS so via
            // variavel de ambiente/role da task, nunca hardcoded).
            assertThat(configuracao.credentialsProvider()).isNotInstanceOf(StaticCredentialsProvider.class);
        }
    }

    @Test
    void comEndpointOverrideUsaEndpointECredencialEstaticaDeTesteDoLocalStack() {
        try (SnsClient client = config.snsClient("http://localhost:4566", "us-east-1")) {
            var configuracao = client.serviceClientConfiguration();

            assertThat(configuracao.endpointOverride()).contains(URI.create("http://localhost:4566"));
            assertThat(configuracao.credentialsProvider()).isInstanceOf(StaticCredentialsProvider.class);
        }
    }

    @Test
    void apiCallTimeoutEApiCallAttemptTimeoutSaoConfiguradosParaEvitarThreadDoSchedulerPresaIndefinidamente() {
        // Achado do code review: SnsClient sem timeout deixa uma chamada de
        // rede pendurada travar a thread do @Scheduled para sempre.
        try (SnsClient client = config.snsClient("", "us-east-1")) {
            var overrideConfig = client.serviceClientConfiguration().overrideConfiguration();

            assertThat(overrideConfig.apiCallTimeout()).contains(Duration.ofSeconds(10));
            assertThat(overrideConfig.apiCallAttemptTimeout()).contains(Duration.ofSeconds(10));
        }
    }
}
