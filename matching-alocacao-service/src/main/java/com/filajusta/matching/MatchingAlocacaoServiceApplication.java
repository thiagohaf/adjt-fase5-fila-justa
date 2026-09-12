package com.filajusta.matching;

import com.filajusta.matching.application.command.AlocacaoRepositorio;
import com.filajusta.matching.application.command.AtualizarScoreReplica;
import com.filajusta.matching.application.command.ConfirmarAlocacao;
import com.filajusta.matching.application.command.EventoOutboxRepositorio;
import com.filajusta.matching.application.command.RecursoRepositorio;
import com.filajusta.matching.application.command.ScoreReplicaRepositorio;
import com.filajusta.matching.application.command.UpsertRecurso;
import com.filajusta.matching.application.query.AlocacaoConsultaRepositorio;
import com.filajusta.matching.application.query.ConsultarFilaPriorizada;
import com.filajusta.matching.application.query.ConsultarSugestaoRecurso;
import com.filajusta.matching.application.query.FilaRepositorio;
import com.filajusta.matching.application.query.RecursoConsultaRepositorio;
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
 * (infrastructure/web); desde a Story 3-3b2b também conecta
 * {@link AlocacaoConsultaRepositorio} (porto da Story 3-3b2a, implementado
 * em {@code infrastructure.persistence}) para excluir da fila todo
 * {@code pacienteId} com {@code Alocacao} ATIVA. {@link UpsertRecurso}
 * (Story 3.2b2) conecta a porta
 * {@link RecursoRepositorio} -- atende {@code POST /internal/recursos}
 * (infrastructure/web). {@link ConsultarSugestaoRecurso} (Story 3.2b3)
 * conecta as portas {@link RecursoConsultaRepositorio} e
 * {@link ConsultarFilaPriorizada} (reutilizada sem duplicar o cálculo de
 * Prioridade Efetiva) -- atende {@code GET /v1/recursos/{id}/sugestao}
 * (infrastructure/web).
 *
 * <p>Story 3-3a: infraestrutura outbox própria deste serviço (AD-3) --
 * {@code EventoOutboxRepositorioAdapter} (infrastructure/persistence) e
 * {@code RelaySnsPublisherJob}/{@code RelaySnsClientConfig}
 * (infrastructure/relay) são {@code @Component}/{@code @Configuration}
 * registrados via component scan, mesmo padrão de todos os demais adapters
 * deste serviço (ex.: {@link RecursoRepositorio} → {@code
 * RecursoRepositorioAdapter}) -- sem {@code @Bean} explícito aqui, porque
 * nenhum caso de uso real desta story consome {@code EventoOutboxRepositorio}
 * como dependência de construtor (nenhum produtor existe ainda, ver
 * Boundaries da spec 3-3a; isso fica para as Stories 3.3b/3.3c).
 * {@code @EnableScheduling} (já presente desde a Story 3.1b, para
 * {@code ScoreCalculadoConsumerJob}) também habilita o {@code @Scheduled} de
 * {@code RelaySnsPublisherJob} -- nenhuma anotação nova necessária aqui.
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
                                                      PrioridadeEfetiva prioridadeEfetiva, Clock clock,
                                                      AlocacaoConsultaRepositorio alocacaoConsultaRepositorio) {
        return new ConsultarFilaPriorizada(
                filaRepositorio, scoreBootstrap, prioridadeEfetiva, clock, alocacaoConsultaRepositorio);
    }

    @Bean
    UpsertRecurso upsertRecurso(RecursoRepositorio recursoRepositorio) {
        return new UpsertRecurso(recursoRepositorio);
    }

    @Bean
    ConsultarSugestaoRecurso consultarSugestaoRecurso(RecursoConsultaRepositorio recursoConsultaRepositorio,
                                                        ConsultarFilaPriorizada consultarFilaPriorizada) {
        return new ConsultarSugestaoRecurso(recursoConsultaRepositorio, consultarFilaPriorizada);
    }

    @Bean
    ConfirmarAlocacao confirmarAlocacao(AlocacaoRepositorio alocacaoRepositorio,
                                         RecursoRepositorio recursoRepositorio,
                                         RecursoConsultaRepositorio recursoConsultaRepositorio,
                                         EventoOutboxRepositorio eventoOutboxRepositorio,
                                         Clock clock) {
        return new ConfirmarAlocacao(
                alocacaoRepositorio, recursoRepositorio, recursoConsultaRepositorio, eventoOutboxRepositorio, clock);
    }
}
