package com.filajusta.triagem;

import com.filajusta.triagem.application.command.EventoOutboxRepositorio;
import com.filajusta.triagem.application.command.PacienteRepositorio;
import com.filajusta.triagem.application.command.RegistrarTriagem;
import com.filajusta.triagem.application.command.ResolverOuCriarPaciente;
import com.filajusta.triagem.application.command.TriagemRepositorio;
import com.filajusta.triagem.domain.CalculadorDeScore;
import com.filajusta.triagem.domain.FaixaVital;
import com.filajusta.triagem.domain.LimitesSinaisVitais;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Ponto de entrada do triagem-score-service (Story 2.1, primeiro servico de
 * dominio do FilaJusta -- FR-1, FR-3). Raiz de composicao que conecta as
 * portas de {@code application.command} (framework-agnosticas por design)
 * aos adapters de {@code infrastructure} -- {@link RegistrarTriagem} e
 * {@link ResolverOuCriarPaciente} nao carregam nenhuma anotacao Spring de
 * dominio (so {@code @Transactional} em {@link RegistrarTriagem}, ver seu
 * javadoc).
 *
 * <p>{@link LimitesSinaisVitais} (AD-11) e montada aqui a partir de
 * {@code filajusta.triagem.limites.*} (application.yml) -- fonte unica de
 * verdade, nao literais espalhados pelo dominio (Deferred da
 * ARCHITECTURE-SPINE.md).
 */
@SpringBootApplication
public class TriagemScoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriagemScoreServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CalculadorDeScore calculadorDeScore() {
        return new CalculadorDeScore();
    }

    @Bean
    LimitesSinaisVitais limitesSinaisVitais(
            @Value("${filajusta.triagem.limites.frequencia-cardiaca.min}") double frequenciaCardiacaMin,
            @Value("${filajusta.triagem.limites.frequencia-cardiaca.max}") double frequenciaCardiacaMax,
            @Value("${filajusta.triagem.limites.pressao-arterial-sistolica.min}") double pasMin,
            @Value("${filajusta.triagem.limites.pressao-arterial-sistolica.max}") double pasMax,
            @Value("${filajusta.triagem.limites.pressao-arterial-diastolica.min}") double padMin,
            @Value("${filajusta.triagem.limites.pressao-arterial-diastolica.max}") double padMax,
            @Value("${filajusta.triagem.limites.saturacao-oxigenio.min}") double spo2Min,
            @Value("${filajusta.triagem.limites.saturacao-oxigenio.max}") double spo2Max,
            @Value("${filajusta.triagem.limites.frequencia-respiratoria.min}") double frMin,
            @Value("${filajusta.triagem.limites.frequencia-respiratoria.max}") double frMax,
            @Value("${filajusta.triagem.limites.temperatura.min}") double temperaturaMin,
            @Value("${filajusta.triagem.limites.temperatura.max}") double temperaturaMax) {
        return new LimitesSinaisVitais(
                new FaixaVital(frequenciaCardiacaMin, frequenciaCardiacaMax),
                new FaixaVital(pasMin, pasMax),
                new FaixaVital(padMin, padMax),
                new FaixaVital(spo2Min, spo2Max),
                new FaixaVital(frMin, frMax),
                new FaixaVital(temperaturaMin, temperaturaMax));
    }

    @Bean
    ResolverOuCriarPaciente resolverOuCriarPaciente(PacienteRepositorio pacienteRepositorio) {
        return new ResolverOuCriarPaciente(pacienteRepositorio);
    }

    @Bean
    RegistrarTriagem registrarTriagem(ResolverOuCriarPaciente resolverOuCriarPaciente,
                                       TriagemRepositorio triagemRepositorio,
                                       EventoOutboxRepositorio eventoOutboxRepositorio,
                                       CalculadorDeScore calculadorDeScore,
                                       LimitesSinaisVitais limitesSinaisVitais,
                                       Clock clock) {
        return new RegistrarTriagem(resolverOuCriarPaciente, triagemRepositorio, eventoOutboxRepositorio,
                calculadorDeScore, limitesSinaisVitais, clock);
    }
}
