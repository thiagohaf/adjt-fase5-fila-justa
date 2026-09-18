package com.filajusta.agendamento;

import com.filajusta.agendamento.application.command.AgendamentoRepositorio;
import com.filajusta.agendamento.application.command.PacienteRepositorio;
import com.filajusta.agendamento.application.command.RegistrarAgendamento;
import com.filajusta.agendamento.application.command.ResolverOuCriarPaciente;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Ponto de entrada do agendamento-confirmacao-service (Story 1.1, AD-1 --
 * renomeado/podado do extinto triagem-score-service, produto FilaJusta
 * descartado). Raiz de composicao que conecta as portas de
 * {@code application.command} (framework-agnosticas por design) aos
 * adapters de {@code infrastructure} -- {@link RegistrarAgendamento} e
 * {@link ResolverOuCriarPaciente} nao carregam nenhuma anotacao Spring de
 * dominio (so {@code @Transactional} em {@link RegistrarAgendamento}, ver
 * seu javadoc).
 *
 * <p>Sem {@code @EnableScheduling} nesta story -- nenhum poller/relay ainda
 * (outbox entra a partir da Story 1.2, ver Boundaries da spec 1.1).
 */
@SpringBootApplication
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
                                               Clock clock) {
        return new RegistrarAgendamento(resolverOuCriarPaciente, agendamentoRepositorio, clock);
    }
}
