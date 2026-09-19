package com.filajusta.agendamento.infrastructure.web;

import com.filajusta.agendamento.application.command.ConfirmarPresenca;
import com.filajusta.agendamento.application.command.RegistrarAgendamento;
import com.filajusta.agendamento.domain.Agendamento;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos do agendamento-confirmacao-service. {@code POST
 * /v1/agendamentos} (Story 1.1) resolve/cria o Paciente por CPF internamente
 * e registra um Agendamento em {@code AGUARDANDO_JANELA} -- sempre {@code
 * 201} sincrono quando os dados sao validos (Boundaries da spec 1.1). {@code
 * POST /v1/agendamentos/{id}/confirmacao} (Story 1.3, FR-4) transiciona um
 * Agendamento {@code AGUARDANDO_CONFIRMACAO} para {@code CONFIRMADO} --
 * {@code 200} sincrono, inclusive em confirmacao duplicada (sucesso
 * silencioso, Boundaries da spec 1.3).
 *
 * <p>Erros de validacao/conflito/nao-encontrado viram RFC 7807 via
 * {@link AgendamentoExceptionHandler}.
 */
@RestController
public class AgendamentoController {

    private final RegistrarAgendamento registrarAgendamento;
    private final ConfirmarPresenca confirmarPresenca;

    public AgendamentoController(RegistrarAgendamento registrarAgendamento, ConfirmarPresenca confirmarPresenca) {
        this.registrarAgendamento = registrarAgendamento;
        this.confirmarPresenca = confirmarPresenca;
    }

    @PostMapping("/v1/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrarAgendamentoResponse registrar(@RequestBody RegistrarAgendamentoRequest request) {
        Agendamento agendamento = registrarAgendamento.registrar(
                request.cpf(), request.recursoId(), request.dataHoraAgendamento());
        return RegistrarAgendamentoResponse.de(agendamento);
    }

    @PostMapping("/v1/agendamentos/{id}/confirmacao")
    @ResponseStatus(HttpStatus.OK)
    public void confirmar(@PathVariable("id") Long id) {
        confirmarPresenca.confirmar(id);
    }
}
