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
  private final String triagemScoreBaseUrl;
  private final String agendamentoConfirmacaoBaseUrl;

  public PacienteClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
    this.triagemScoreBaseUrl = "http://triagem-score-service:8084";
    this.agendamentoConfirmacaoBaseUrl = "http://agendamento-confirmacao-service:8082";
  }

  public Optional<PacienteDTO> obterPaciente(Long pacienteId) {
    try {
      return tentarAgendamentoConfirmacao(pacienteId);
    } catch (RestClientException e) {
      logger.debug("Paciente {} não encontrado em agendamento-confirmacao, tentando triagem-score", pacienteId);
    }

    try {
      return tentarTriagemScore(pacienteId);
    } catch (RestClientException e) {
      logger.warn("Paciente {} não encontrado em nenhum serviço", pacienteId);
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

  private Optional<PacienteDTO> tentarTriagemScore(Long pacienteId) {
    // triagem-score usa UUID, mas vamos tentar com BIGINT como fallback
    String url = triagemScoreBaseUrl + "/internal/pacientes/" + pacienteId;
    try {
      PacienteDTO dto = restTemplate.getForObject(url, PacienteDTO.class);
      return Optional.ofNullable(dto);
    } catch (RestClientException e) {
      logger.debug("Erro ao chamar triagem-score para paciente {}: {}", pacienteId, e.getMessage());
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
