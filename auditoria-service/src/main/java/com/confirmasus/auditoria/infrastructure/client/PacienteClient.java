package com.confirmasus.auditoria.infrastructure.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Component
public class PacienteClient {

  private static final Logger logger = LoggerFactory.getLogger(PacienteClient.class);

  private final RestTemplate restTemplate;
  private final String agendamentoConfirmacaoBaseUrl;

  public PacienteClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
    this.agendamentoConfirmacaoBaseUrl = "http://agendamento-confirmacao-service:8082";
  }

  public Optional<PacienteDTO> obterPaciente(Long pacienteId) {
    try {
      return tentarAgendamentoConfirmacao(pacienteId);
    } catch (RestClientException e) {
      logger.warn("Paciente {} não encontrado em agendamento-confirmacao", pacienteId);
    }

    return Optional.empty();
  }

  private Optional<PacienteDTO> tentarAgendamentoConfirmacao(Long pacienteId) {
    String url = agendamentoConfirmacaoBaseUrl + "/internal/pacientes/" + pacienteId;
    try {
      PacienteDTO dto = restTemplate.getForObject(url, PacienteDTO.class);
      return Optional.ofNullable(dto);
    } catch (RestClientException e) {
      logger.debug("Erro ao chamar agendamento-confirmacao para paciente {}: {}", pacienteId, e.getMessage());
      throw e;
    }
  }

  public static class PacienteDTO {
    public Long id;
    public String nome;
    public String cpf;
    public String tipoPaciente;

    public PacienteDTO() {}

    public PacienteDTO(Long id, String nome, String cpf, String tipoPaciente) {
      this.id = id;
      this.nome = nome;
      this.cpf = cpf;
      this.tipoPaciente = tipoPaciente;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getCpf() { return cpf; }
    public String getTipoPaciente() { return tipoPaciente; }
  }
}
