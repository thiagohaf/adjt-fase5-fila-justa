package com.filajusta.agendamento.infrastructure.web;

import java.time.Instant;

/**
 * Corpo de {@code POST /v1/agendamentos}. Deliberadamente sem anotacoes de
 * Bean Validation: a validacao de {@code cpf}, {@code recursoId} e
 * {@code dataHoraAgendamento} e responsabilidade do dominio/aplicacao
 * ({@code Cpf}, {@code RegistrarAgendamento}), garantindo um unico caminho
 * de validacao (mesmo padrao do extinto {@code RegistrarTriagemRequest}).
 */
record RegistrarAgendamentoRequest(String cpf, String recursoId, Instant dataHoraAgendamento) {
}
