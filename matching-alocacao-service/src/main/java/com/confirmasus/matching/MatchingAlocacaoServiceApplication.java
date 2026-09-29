package com.confirmasus.matching;

import com.confirmasus.matching.application.command.AlocacaoRepositorio;
import com.confirmasus.matching.application.command.ConfirmarAlocacao;
import com.confirmasus.matching.application.command.CriarEntradaListaEspera;
import com.confirmasus.matching.application.command.EventoOutboxRepositorio;
import com.confirmasus.matching.application.command.LiberacaoAgendadaRepositorio;
import com.confirmasus.matching.application.command.LiberarRecurso;
import com.confirmasus.matching.application.command.ListaEsperaEntradaRepositorio;
import com.confirmasus.matching.application.command.PacienteRepositorio;
import com.confirmasus.matching.application.command.RecursoRepositorio;
import com.confirmasus.matching.application.command.RecusarSugestao;
import com.confirmasus.matching.application.command.ResolverOuCriarPaciente;
import com.confirmasus.matching.application.command.SugestaoRecusadaRepositorio;
import com.confirmasus.matching.application.command.UltimaSugestaoRegistradaRepositorio;
import com.confirmasus.matching.application.command.UpsertRecurso;
import com.confirmasus.matching.application.query.AlocacaoConsultaRepositorio;
import com.confirmasus.matching.application.query.ConsultarSugestaoRecurso;
import com.confirmasus.matching.application.query.ListaEsperaEntradaConsultaRepositorio;
import com.confirmasus.matching.application.query.RecursoConsultaRepositorio;
import com.confirmasus.matching.application.query.SugestaoRecusadaConsultaRepositorio;
import com.confirmasus.matching.infrastructure.config.LiberacaoDuracaoProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

/**
 * Ponto de entrada do matching-alocacao-service -- Clean Architecture,
 * schema {@code matching_alocacao}. Raiz de composição.
 *
 * <p>{@link UpsertRecurso} conecta a porta {@link RecursoRepositorio} --
 * atende {@code POST /internal/recursos}/{@code POST /v1/recursos}
 * (infrastructure/web). {@link ConsultarSugestaoRecurso} conecta as portas
 * {@link RecursoConsultaRepositorio}, {@link ListaEsperaEntradaConsultaRepositorio}
 * (Sugestão de Repasse FIFO por {@code criadoEm}, AD-6), {@link
 * AlocacaoConsultaRepositorio} (exclui paciente já alocado), {@link
 * SugestaoRecusadaConsultaRepositorio} (pula paciente já recusado para o
 * Recurso), {@link UltimaSugestaoRegistradaRepositorio} e {@link
 * EventoOutboxRepositorio} (rastreamento: {@code SugestaoGerada} só quando
 * a sugestão muda) -- atende {@code GET /v1/recursos/{id}/sugestao}
 * (infrastructure/web).
 *
 * <p>Infraestrutura outbox própria deste serviço (AD-3) --
 * {@code EventoOutboxRepositorioAdapter} (infrastructure/persistence) e
 * {@code RelaySnsPublisherJob}/{@code RelaySnsClientConfig}
 * (infrastructure/relay) são {@code @Component}/{@code @Configuration}
 * registrados via component scan, mesmo padrão de todos os demais adapters
 * deste serviço (ex.: {@link RecursoRepositorio} → {@code
 * RecursoRepositorioAdapter}) -- sem {@code @Bean} explícito aqui, porque
 * nenhum caso de uso real desta story consome {@code EventoOutboxRepositorio}
 * como dependência de construtor (nenhum produtor existe ainda, ver
 * nenhum caso de uso real consome {@code EventoOutboxRepositorio} como
 * dependência de construtor. {@code @EnableScheduling} habilita o
 * {@code @Scheduled} de {@code RelaySnsPublisherJob}.
 *
 * <p>{@link RecusarSugestao} conecta a porta {@link SugestaoRecusadaRepositorio}
 * (implementada em {@code infrastructure.persistence}) -- atende {@code
 * POST /v1/recursos/{id}/alocacoes/recusa} (infrastructure/web), mesmo
 * molde de {@link ConfirmarAlocacao}.
 *
 * <p>{@link LiberacaoDuracaoProperties} é montada aqui a partir de {@code
 * confirmasus.liberacao.duracao.rank-{1..4}} (application.yml, fail-fast:
 * qualquer rank ausente do YAML já quebra a resolução do placeholder no
 * boot). {@link ConfirmarAlocacao} conecta também {@link LiberacaoAgendadaRepositorio}
 * e este bean para agendar a liberação do Recurso na mesma transação da
 * confirmação.
 *
 * <p>{@code LiberacaoAgendadaRelayJob}/{@code LiberacaoAgendadaSqsClientConfig}
 * (infrastructure/relay) publicam a mensagem de delay de {@code
 * liberacao_agendada} numa fila SQS standard própria -- {@code @Component}/
 * {@code @Configuration} registrados via component scan, sem {@code @Bean}
 * explícito aqui.
 *
 * <p>{@link LiberarRecurso} precisa de {@code @Bean} explícito aqui
 * (diferente do padrão "sem @Bean" de outros comandos desta classe) porque
 * seu método {@code liberar} é {@code @Transactional} -- essa anotação só
 * produz um proxy AOP real em cima de um bean gerenciado pelo container;
 * instanciado via {@code new} (como nos testes) ela fica inerte e as 3
 * escritas (Alocacao, Recurso, outbox) rodariam cada uma na sua própria
 * transação, quebrando a atomicidade exigida (mesmo motivo pelo qual
 * {@link ConfirmarAlocacao}, acima, também é {@code @Bean} explícito).
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
    UpsertRecurso upsertRecurso(RecursoRepositorio recursoRepositorio) {
        return new UpsertRecurso(recursoRepositorio);
    }

    @Bean
    ConsultarSugestaoRecurso consultarSugestaoRecurso(RecursoConsultaRepositorio recursoConsultaRepositorio,
                                                        ListaEsperaEntradaConsultaRepositorio listaEsperaEntradaConsultaRepositorio,
                                                        AlocacaoConsultaRepositorio alocacaoConsultaRepositorio,
                                                        SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio,
                                                        UltimaSugestaoRegistradaRepositorio ultimaSugestaoRegistradaRepositorio,
                                                        EventoOutboxRepositorio eventoOutboxRepositorio,
                                                        Clock clock) {
        return new ConsultarSugestaoRecurso(
                recursoConsultaRepositorio, listaEsperaEntradaConsultaRepositorio, alocacaoConsultaRepositorio,
                sugestaoRecusadaConsultaRepositorio, ultimaSugestaoRegistradaRepositorio, eventoOutboxRepositorio,
                clock);
    }

    @Bean
    LiberacaoDuracaoProperties liberacaoDuracaoProperties(
            @Value("${confirmasus.liberacao.duracao.rank-1}") Duration rank1,
            @Value("${confirmasus.liberacao.duracao.rank-2}") Duration rank2,
            @Value("${confirmasus.liberacao.duracao.rank-3}") Duration rank3,
            @Value("${confirmasus.liberacao.duracao.rank-4}") Duration rank4) {
        return new LiberacaoDuracaoProperties(rank1, rank2, rank3, rank4);
    }

    @Bean
    ConfirmarAlocacao confirmarAlocacao(AlocacaoRepositorio alocacaoRepositorio,
                                         RecursoRepositorio recursoRepositorio,
                                         RecursoConsultaRepositorio recursoConsultaRepositorio,
                                         EventoOutboxRepositorio eventoOutboxRepositorio,
                                         LiberacaoAgendadaRepositorio liberacaoAgendadaRepositorio,
                                         LiberacaoDuracaoProperties liberacaoDuracaoProperties,
                                         Clock clock) {
        return new ConfirmarAlocacao(
                alocacaoRepositorio, recursoRepositorio, recursoConsultaRepositorio, eventoOutboxRepositorio,
                liberacaoAgendadaRepositorio, liberacaoDuracaoProperties, clock);
    }

    @Bean
    LiberarRecurso liberarRecurso(AlocacaoRepositorio alocacaoRepositorio,
                                    RecursoRepositorio recursoRepositorio,
                                    EventoOutboxRepositorio eventoOutboxRepositorio,
                                    Clock clock) {
        return new LiberarRecurso(alocacaoRepositorio, recursoRepositorio, eventoOutboxRepositorio, clock);
    }

    @Bean
    RecusarSugestao recusarSugestao(SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio,
                                      EventoOutboxRepositorio eventoOutboxRepositorio,
                                      RecursoConsultaRepositorio recursoConsultaRepositorio,
                                      Clock clock) {
        return new RecusarSugestao(
                sugestaoRecusadaRepositorio, eventoOutboxRepositorio, recursoConsultaRepositorio, clock);
    }

    @Bean
    ResolverOuCriarPaciente resolverOuCriarPaciente(PacienteRepositorio pacienteRepositorio) {
        return new ResolverOuCriarPaciente(pacienteRepositorio);
    }

    @Bean
    CriarEntradaListaEspera criarEntradaListaEspera(ResolverOuCriarPaciente resolverOuCriarPaciente,
                                                     ListaEsperaEntradaRepositorio listaEsperaEntradaRepositorio,
                                                     Clock clock) {
        return new CriarEntradaListaEspera(resolverOuCriarPaciente, listaEsperaEntradaRepositorio, clock);
    }
}
