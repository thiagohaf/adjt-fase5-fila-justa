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
 * Fabrica o {@link SqsClient} usado por {@link LiberacaoAgendadaRelayJob}
 * (Story 3-4a2) -- copia estrutural de {@link ScoreCalculadoSqsClientConfig}
 * (Story 3.1b), com namespace de configuracao PROPRIO e isolado
 * ({@code filajusta.matching.liberacao-agendada-relay.*}, nunca
 * {@code filajusta.matching.relay.*} nem {@code outbox-relay.*} -- Boundaries
 * da spec 3-4a2): reaproveitar qualquer um dos dois namespaces existentes
 * acoplaria o kill switch/endpoint-override deste relay ao do consumidor SQS
 * de ScoreCalculado ou ao do publisher SNS de outbox, impedindo ligar/desligar
 * cada um independentemente em teste (e em producao).
 *
 * <p>Em producao, {@code endpoint-override} fica vazio -- o SDK resolve
 * credenciais pela cadeia default (variavel de ambiente/role da task), nunca
 * hardcoded (mesmo Boundary da spec 3.0). A REGIAO nao cai na cadeia default
 * do SDK neste servico: {@code application.yml} sempre popula
 * {@code filajusta.matching.liberacao-agendada-relay.region} (via
 * {@code ${AWS_REGION:us-east-1}}) -- por isso, na pratica (producao e
 * testes), {@code region} nunca chega vazio aqui e esta fabrica sempre acaba
 * chamando {@code .region(...)} explicitamente. O metodo em si so chama
 * {@code builder.region(...)} quando {@code region} nao e vazio (mesmo
 * guard condicional de {@code endpointOverride} logo abaixo) -- o
 * comportamento condicional existe para o caso defensivo de alguem apagar a
 * populacao default do {@code application.yml}; a garantia real de "regiao
 * sempre setada" vem do YAML, nao deste metodo.
 *
 * <p>{@code endpoint-override} so e setado nos testes de integracao com
 * LocalStack (Testcontainers) -- unico caso em que a credencial estatica de
 * teste ("test"/"test") e usada; nunca contra a conta AWS real.
 *
 * <p>Timeout de API explicito (mesmo raciocinio de
 * {@link ScoreCalculadoSqsClientConfig}): sem
 * {@code apiCallTimeout}/{@code apiCallAttemptTimeout}, uma chamada de rede
 * pendurada travaria a thread do scheduler ({@code @Scheduled}) por tempo
 * indefinido. Este cliente so faz {@code sendMessage} (nunca long-poll) --
 * {@value #API_CALL_TIMEOUT_SECONDS}s e generoso o bastante sem o motivo de
 * long-polling que levou {@code ScoreCalculadoSqsClientConfig} a 30s.
 *
 * <p>{@code @ConditionalOnProperty}
 * ({@code filajusta.matching.liberacao-agendada-relay.enabled}, SEM
 * {@code matchIfMissing} -- default {@code false}, mesmo padrao de
 * {@code RelaySnsClientConfig}/{@code outbox-relay}, nao o {@code true} de
 * {@link ScoreCalculadoSqsClientConfig}): mesma condicao de
 * {@link LiberacaoAgendadaRelayJob}, para que os dois beans sejam
 * criados/omitidos juntos. Bean nomeado explicitamente
 * ({@code liberacaoAgendadaSqsClient}, nao o default {@code sqsClient} de
 * {@link ScoreCalculadoSqsClientConfig}) porque os dois beans {@link SqsClient}
 * coexistem no mesmo contexto Spring quando ambos os relays estao habilitados
 * -- ver {@code @Qualifier} correspondente no construtor de
 * {@link LiberacaoAgendadaRelayJob} e {@code @Primary} acrescentado a
 * {@link ScoreCalculadoSqsClientConfig#sqsClient} para manter a injecao
 * (sem qualifier) de {@code ScoreCalculadoConsumerJob} resolvendo para o bean
 * certo sem ambiguidade.
 */
@Configuration
@ConditionalOnProperty(prefix = "filajusta.matching.liberacao-agendada-relay", name = "enabled")
class LiberacaoAgendadaSqsClientConfig {

    // Sem long-poll (so sendMessage) -- folga confortavel acima da latencia
    // normal de rede sem o motivo dos 30s de ScoreCalculadoSqsClientConfig
    // (que precisa cobrir WaitTimeSeconds ate 20s).
    private static final int API_CALL_TIMEOUT_SECONDS = 15;

    @Bean
    SqsClient liberacaoAgendadaSqsClient(
            @Value("${filajusta.matching.liberacao-agendada-relay.endpoint-override:}") String endpointOverride,
            @Value("${filajusta.matching.liberacao-agendada-relay.region:}") String region) {
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
