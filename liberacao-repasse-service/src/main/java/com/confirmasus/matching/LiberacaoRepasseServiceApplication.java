package com.confirmasus.matching;

import com.confirmasus.matching.application.command.AlocacaoRepositorio;
import com.confirmasus.matching.application.command.ConfirmarRepasse;
import com.confirmasus.matching.application.command.CriarEntradaListaEspera;
import com.confirmasus.matching.application.command.GerarSugestaoRepasse;
import com.confirmasus.matching.application.command.RecusarSugestaoRepasse;
import com.confirmasus.matching.application.command.SelecionadorCandidatoFifo;
import com.confirmasus.matching.application.command.SugestaoRepasseRepositorio;
import com.confirmasus.matching.application.command.EventoOutboxRepositorio;
import com.confirmasus.matching.application.command.ListaEsperaEntradaRepositorio;
import com.confirmasus.matching.application.command.PacienteRepositorio;
import com.confirmasus.matching.application.command.RecursoRepositorio;
import com.confirmasus.matching.application.command.ResolverOuCriarPaciente;
import com.confirmasus.matching.application.command.SugestaoRecusadaRepositorio;
import com.confirmasus.matching.application.command.UpsertRecurso;
import com.confirmasus.matching.application.query.AlocacaoConsultaRepositorio;
import com.confirmasus.matching.application.query.ConsultarSugestaoRecurso;
import com.confirmasus.matching.application.query.ListaEsperaEntradaConsultaRepositorio;
import com.confirmasus.matching.application.query.RecursoConsultaRepositorio;
import com.confirmasus.matching.application.query.SugestaoRecusadaConsultaRepositorio;
import com.confirmasus.matching.application.query.SugestaoRepasseConsultaRepositorio;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Ponto de entrada do liberacao-repasse-service -- Clean Architecture,
 * schema {@code matching_alocacao}. Raiz de composição.
 *
 * <p>{@link UpsertRecurso} conecta a porta {@link RecursoRepositorio}. {@link
 * GerarSugestaoRepasse} (reação a {@code VagaLiberada}), {@link ConfirmarRepasse}
 * e {@link RecusarSugestaoRepasse} (Story 6.1, AD-6) usam {@link
 * SelecionadorCandidatoFifo} (Lista de Espera FIFO por {@code criadoEm});
 * {@link ConsultarSugestaoRecurso} lê a sugestão pendente.
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
 */
@SpringBootApplication
@EnableScheduling
public class LiberacaoRepasseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiberacaoRepasseServiceApplication.class, args);
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
                                                        SugestaoRepasseConsultaRepositorio sugestaoRepasseConsultaRepositorio) {
        return new ConsultarSugestaoRecurso(recursoConsultaRepositorio, sugestaoRepasseConsultaRepositorio);
    }

    @Bean
    SelecionadorCandidatoFifo selecionadorCandidatoFifo(
            ListaEsperaEntradaConsultaRepositorio listaEsperaEntradaConsultaRepositorio,
            AlocacaoConsultaRepositorio alocacaoConsultaRepositorio,
            SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio) {
        return new SelecionadorCandidatoFifo(
                listaEsperaEntradaConsultaRepositorio, alocacaoConsultaRepositorio,
                sugestaoRecusadaConsultaRepositorio);
    }

    @Bean
    GerarSugestaoRepasse gerarSugestaoRepasse(SugestaoRepasseRepositorio sugestaoRepasseRepositorio,
                                                SelecionadorCandidatoFifo selecionadorCandidatoFifo,
                                                AlocacaoRepositorio alocacaoRepositorio,
                                                RecursoRepositorio recursoRepositorio,
                                                RecursoConsultaRepositorio recursoConsultaRepositorio,
                                                EventoOutboxRepositorio eventoOutboxRepositorio,
                                                Clock clock) {
        return new GerarSugestaoRepasse(sugestaoRepasseRepositorio, selecionadorCandidatoFifo,
                alocacaoRepositorio, recursoRepositorio, recursoConsultaRepositorio, eventoOutboxRepositorio, clock);
    }

    @Bean
    ConfirmarRepasse confirmarRepasse(SugestaoRepasseRepositorio sugestaoRepasseRepositorio,
                                        AlocacaoRepositorio alocacaoRepositorio,
                                        RecursoRepositorio recursoRepositorio,
                                        EventoOutboxRepositorio eventoOutboxRepositorio,
                                        Clock clock) {
        return new ConfirmarRepasse(
                sugestaoRepasseRepositorio, alocacaoRepositorio, recursoRepositorio, eventoOutboxRepositorio, clock);
    }

    @Bean
    RecusarSugestaoRepasse recusarSugestaoRepasse(SugestaoRepasseRepositorio sugestaoRepasseRepositorio,
                                                    SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio,
                                                    SelecionadorCandidatoFifo selecionadorCandidatoFifo,
                                                    EventoOutboxRepositorio eventoOutboxRepositorio,
                                                    Clock clock) {
        return new RecusarSugestaoRepasse(sugestaoRepasseRepositorio, sugestaoRecusadaRepositorio,
                selecionadorCandidatoFifo, eventoOutboxRepositorio, clock);
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
