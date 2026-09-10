package com.filajusta.matching;

import com.filajusta.matching.application.command.AtualizarScoreReplica;
import com.filajusta.matching.application.command.ScoreReplicaRepositorio;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Ponto de entrada do matching-alocacao-service (Story 3.1b, primeiro
 * esqueleto do serviço -- Clean Architecture, schema
 * {@code matching_alocacao}). Raiz de composição: conecta a porta
 * {@link ScoreReplicaRepositorio} (application/command, framework-agnóstica
 * por AD-2) ao adapter de {@code infrastructure/persistence} e monta
 * {@link AtualizarScoreReplica} -- consumido por
 * {@code ScoreCalculadoConsumerJob} (infrastructure/relay, primeiro
 * consumidor SQS real do projeto).
 *
 * <p>{@code @EnableScheduling}: habilita o {@code @Scheduled} de
 * {@code ScoreCalculadoConsumerJob} -- sem isso o poller nunca roda, mesmo
 * com o bean registrado (mesmo padrão de
 * {@code TriagemScoreServiceApplication}, Story 3.0).
 *
 * <p>Sem endpoint REST nesta fase (Boundaries da spec 3.1b, "Never") --
 * isso é a Story 3.1c ({@code GET /v1/fila}).
 */
@SpringBootApplication
@EnableScheduling
public class MatchingAlocacaoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingAlocacaoServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AtualizarScoreReplica atualizarScoreReplica(ScoreReplicaRepositorio scoreReplicaRepositorio, Clock clock) {
        return new AtualizarScoreReplica(scoreReplicaRepositorio, clock);
    }
}
