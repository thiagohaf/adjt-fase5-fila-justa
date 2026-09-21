package com.confirmasus.agendamento;

import com.confirmasus.agendamento.application.command.AbrirJanelaDeConfirmacao;
import com.confirmasus.agendamento.application.command.AgendamentoRepositorio;
import com.confirmasus.agendamento.application.command.ConfirmarPresenca;
import com.confirmasus.agendamento.application.command.EventoOutboxRepositorio;
import com.confirmasus.agendamento.application.command.PacienteRepositorio;
import com.confirmasus.agendamento.application.command.RegistrarAgendamento;
import com.confirmasus.agendamento.application.command.RecusarPresenca;
import com.confirmasus.agendamento.application.command.ExpirarJanelaDeConfirmacao;
import com.confirmasus.agendamento.application.command.ResolverOuCriarPaciente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

/**
 * Ponto de entrada do agendamento-confirmacao-service (Story 1.1, AD-1 --
 * renomeado/podado do extinto triagem-score-service, produto ConfirmaSus
 * descartado). Raiz de composicao que conecta as portas de
 * {@code application.command} (framework-agnosticas por design) aos
 * adapters de {@code infrastructure} -- {@link RegistrarAgendamento},
 * {@link ResolverOuCriarPaciente} e {@link AbrirJanelaDeConfirmacao} nao
 * carregam nenhuma anotacao Spring de dominio (so {@code @Transactional}/
 * {@code @Scheduled}, ver seus javadocs).
 *
 * <p>{@code @EnableScheduling} (Story 1.2, AD-5) -- habilita o poller
 * {@link AbrirJanelaDeConfirmacao} e o relay
 * {@code infrastructure.relay.RelaySnsPublisherJob} (component-scanned).
 */
@SpringBootApplication
@EnableScheduling
public class AgendamentoConfirmacaoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgendamentoConfirmacaoServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ResolverOuCriarPaciente resolverOuCriarPaciente(PacienteRepositorio pacienteRepositorio) {
        return new ResolverOuCriarPaciente(pacienteRepositorio);
    }

    @Bean
    RegistrarAgendamento registrarAgendamento(ResolverOuCriarPaciente resolverOuCriarPaciente,
                                               AgendamentoRepositorio agendamentoRepositorio,
                                               Clock clock,
                                               @Value("${confirmasus.agendamento.janela.duracao:PT30M}")
                                               Duration janelaDuracao) {
        return new RegistrarAgendamento(resolverOuCriarPaciente, agendamentoRepositorio, clock, janelaDuracao);
    }

    @Bean
    AbrirJanelaDeConfirmacao abrirJanelaDeConfirmacao(AgendamentoRepositorio agendamentoRepositorio,
                                                       EventoOutboxRepositorio eventoOutboxRepositorio,
                                                       Clock clock,
                                                       @Value("${confirmasus.agendamento.abertura-janela.batch-size:50}")
                                                       int loteTamanho) {
        return new AbrirJanelaDeConfirmacao(agendamentoRepositorio, eventoOutboxRepositorio, clock, loteTamanho);
    }

    @Bean
    ConfirmarPresenca confirmarPresenca(AgendamentoRepositorio agendamentoRepositorio,
                                         EventoOutboxRepositorio eventoOutboxRepositorio,
                                         Clock clock) {
        return new ConfirmarPresenca(agendamentoRepositorio, eventoOutboxRepositorio, clock);
    }

    @Bean
    RecusarPresenca recusarPresenca(AgendamentoRepositorio agendamentoRepositorio,
                                     EventoOutboxRepositorio eventoOutboxRepositorio,
                                     Clock clock) {
        return new RecusarPresenca(agendamentoRepositorio, eventoOutboxRepositorio, clock);
    }


    @Bean
    ExpirarJanelaDeConfirmacao expirarJanelaDeConfirmacao(AgendamentoRepositorio agendamentoRepositorio,
                                                           EventoOutboxRepositorio eventoOutboxRepositorio,
                                                           Clock clock,
                                                           @Value("${confirmasus.agendamento.expiracao-janela.batch-size:50}")
                                                           int loteTamanho) {
        return new ExpirarJanelaDeConfirmacao(agendamentoRepositorio, eventoOutboxRepositorio, clock, loteTamanho);
    }
}
