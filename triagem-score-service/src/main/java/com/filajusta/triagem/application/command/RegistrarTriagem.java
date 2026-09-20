package com.filajusta.triagem.application.command;

import com.filajusta.triagem.application.port.EventoOutboxRepositorio;
import com.filajusta.triagem.application.port.TriagemRepositorio;
import com.filajusta.triagem.domain.CalculadorDeScore;
import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.EventoOutbox;
import com.filajusta.triagem.domain.GravidadePercebida;
import com.filajusta.triagem.domain.Score;
import com.filajusta.triagem.domain.SinaisVitais;
import com.filajusta.triagem.domain.Triagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;

/**
 * Use case (Command) para registrar uma Triagem completa.
 * Orquestra: validação → resolução do Paciente → cálculo do Score →
 * persistência + outbox em mesma transação.
 */
public class RegistrarTriagem {
  private final ResolverOuCriarPaciente resolverPaciente;
  private final TriagemRepositorio triagemRepositorio;
  private final EventoOutboxRepositorio eventoOutboxRepositorio;
  private final CalculadorDeScore calculador;
  private final ObjectMapper objectMapper;

  public RegistrarTriagem(
    ResolverOuCriarPaciente resolverPaciente,
    TriagemRepositorio triagemRepositorio,
    EventoOutboxRepositorio eventoOutboxRepositorio,
    CalculadorDeScore calculador,
    ObjectMapper objectMapper
  ) {
    this.resolverPaciente = Objects.requireNonNull(resolverPaciente, "resolverPaciente não pode ser nulo");
    this.triagemRepositorio = Objects.requireNonNull(triagemRepositorio, "triagemRepositorio não pode ser nulo");
    this.eventoOutboxRepositorio = Objects.requireNonNull(eventoOutboxRepositorio, "eventoOutboxRepositorio não pode ser nulo");
    this.calculador = Objects.requireNonNull(calculador, "calculador não pode ser nulo");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper não pode ser nulo");
  }

  /**
   * Executa o fluxo de registro de Triagem completo em uma transação.
   */
  public TriagemRegistradaResponse executar(RegistrarTriagemInput request) {
    Objects.requireNonNull(request, "request não pode ser nulo");

    // 1. Validação de CPF
    Cpf cpf = new Cpf(request.getCpf());

    // 2. Validação de sinais vitais (lança exceção se inválido)
    SinaisVitais sinaisVitais = new SinaisVitais(
      request.getFrequenciaCardiaca(),
      request.getPressaoArterialSistolica(),
      request.getPressaoArterialDiastolica(),
      request.getSaturacaoOxigenio(),
      request.getFrequenciaRespiratoria(),
      request.getTemperatura()
    );

    // 3. Validação de gravidade
    GravidadePercebida gravidade = GravidadePercebida.valueOf(request.getGravidade().toUpperCase());

    // 4. Resolve ou cria Paciente
    UUID pacienteId = resolverPaciente.executar(cpf);

    // 5. Calcula Score
    Score score = calculador.calcular(sinaisVitais, gravidade);

    // 6. Cria entidade Triagem
    Triagem triagem = Triagem.criar(pacienteId, sinaisVitais, gravidade, request.getSintomas(), score);

    // 7. Persiste Triagem e evento outbox na mesma transação
    triagemRepositorio.salvar(triagem);

    EventoOutbox evento = EventoOutbox.scoreCalculado(triagem.registradoEm(), serializarPayload(triagem, pacienteId));
    eventoOutboxRepositorio.salvar(evento);

    // 8. Retorna resposta (nunca bloqueia em relay/publicação)
    return new TriagemRegistradaResponse(triagem, score);
  }

  private String serializarPayload(Triagem triagem, UUID pacienteId) {
    try {
      return objectMapper.writeValueAsString(new ScoreCalculadoPayload(triagem, pacienteId));
    } catch (Exception e) {
      throw new RuntimeException("Falha ao serializar payload do evento", e);
    }
  }

  /**
   * Payload do evento ScoreCalculado para o outbox.
   */
  private static class ScoreCalculadoPayload {
    public final UUID triagemId;
    public final UUID pacienteId;
    public final int score;
    public final String scoreVersao;
    public final String gravidade;
    public final long ocorridoEm;

    ScoreCalculadoPayload(Triagem triagem, UUID pacienteId) {
      this.triagemId = triagem.id();
      this.pacienteId = pacienteId;
      this.score = triagem.score().valor();
      this.scoreVersao = triagem.score().versao();
      this.gravidade = triagem.gravidade().toString();
      this.ocorridoEm = triagem.registradoEm().toEpochMilli();
    }
  }
}
