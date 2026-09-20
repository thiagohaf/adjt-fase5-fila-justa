package com.filajusta.triagem.infrastructure.config;

import com.filajusta.triagem.application.command.RegistrarTriagem;
import com.filajusta.triagem.application.command.ResolverOuCriarPaciente;
import com.filajusta.triagem.application.port.EventoOutboxRepositorio;
import com.filajusta.triagem.application.port.PacienteRepositorio;
import com.filajusta.triagem.application.port.TriagemRepositorio;
import com.filajusta.triagem.domain.CalculadorDeScore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de beans da aplicação (portas, use cases, serviços de domínio).
 */
@Configuration
public class TriagemConfiguration {
  @Bean
  public CalculadorDeScore calculadorDeScore() {
    return new CalculadorDeScore();
  }

  @Bean
  public ResolverOuCriarPaciente resolverOuCriarPaciente(PacienteRepositorio pacienteRepositorio) {
    return new ResolverOuCriarPaciente(pacienteRepositorio);
  }

  @Bean
  public RegistrarTriagem registrarTriagem(
    ResolverOuCriarPaciente resolverPaciente,
    TriagemRepositorio triagemRepositorio,
    EventoOutboxRepositorio eventoOutboxRepositorio,
    CalculadorDeScore calculador,
    ObjectMapper objectMapper
  ) {
    return new RegistrarTriagem(
      resolverPaciente,
      triagemRepositorio,
      eventoOutboxRepositorio,
      calculador,
      objectMapper
    );
  }
}
