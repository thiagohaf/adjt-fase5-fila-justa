package com.filajusta.matching;

import com.filajusta.matching.application.command.AtualizarScoreReplica;
import com.filajusta.matching.application.command.ScoreReplicaRepositorio;
import com.filajusta.matching.application.query.ConsultarFilaPriorizada;
import com.filajusta.matching.application.query.FilaRepositorio;
import com.filajusta.matching.application.query.ScoreBootstrap;
import com.filajusta.matching.domain.PrioridadeEfetiva;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>{@link PrioridadeEfetiva} (Story 3.1c) é montada aqui a partir de
 * {@code filajusta.aging.k}/{@code filajusta.aging.teto} (application.yml)
 * -- fonte única, mesmo padrão de {@code LimitesSinaisVitais} do
 * triagem-score-service. {@link ConsultarFilaPriorizada} (Story 3.1c)
 * conecta as portas {@link FilaRepositorio} e {@link ScoreBootstrap}
 * (implementadas em {@code infrastructure.persistence}/
 * {@code infrastructure.bootstrap}) -- atende {@code GET /v1/fila}
 * (infrastructure/web).
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

    @Bean
    PrioridadeEfetiva prioridadeEfetiva(@Value("${filajusta.aging.k}") double k,
                                         @Value("${filajusta.aging.teto}") double teto) {
        return new PrioridadeEfetiva(k, teto);
    }

    @Bean
    ConsultarFilaPriorizada consultarFilaPriorizada(FilaRepositorio filaRepositorio, ScoreBootstrap scoreBootstrap,
                                                      PrioridadeEfetiva prioridadeEfetiva, Clock clock) {
        return new ConsultarFilaPriorizada(filaRepositorio, scoreBootstrap, prioridadeEfetiva, clock);
    }
}
