package com.filajusta.agendamento.infrastructure.web;

import com.filajusta.agendamento.application.command.RegistrarAgendamento;
import com.filajusta.agendamento.domain.Agendamento;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint publico do agendamento-confirmacao-service (Story 1.1): {@code
 * POST /v1/agendamentos} resolve/cria o Paciente por CPF internamente e
 * registra um Agendamento em {@code AGUARDANDO_JANELA} -- sempre {@code
 * 201} sincrono quando os dados sao validos (Boundaries da spec 1.1).
 * Erros de validacao viram RFC 7807 via {@link AgendamentoExceptionHandler}.
 */
@RestController
public class AgendamentoController {

    private final RegistrarAgendamento registrarAgendamento;

    public AgendamentoController(RegistrarAgendamento registrarAgendamento) {
        this.registrarAgendamento = registrarAgendamento;
    }

    @PostMapping("/v1/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrarAgendamentoResponse registrar(@RequestBody RegistrarAgendamentoRequest request) {
        Agendamento agendamento = registrarAgendamento.registrar(
                request.cpf(), request.recursoId(), request.dataHoraAgendamento());
        return RegistrarAgendamentoResponse.de(agendamento);
    }
}
