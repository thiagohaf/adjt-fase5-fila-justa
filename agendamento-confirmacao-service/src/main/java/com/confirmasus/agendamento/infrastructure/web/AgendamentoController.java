package com.confirmasus.agendamento.infrastructure.web;

import com.confirmasus.agendamento.application.command.ConfirmarPresenca;
import com.confirmasus.agendamento.application.command.RegistrarAgendamento;
import com.confirmasus.agendamento.application.command.RecusarPresenca;
import com.confirmasus.agendamento.application.query.ConsultarAgendamento;
import com.confirmasus.agendamento.application.query.ListarAgendamentos;
import com.confirmasus.agendamento.domain.Agendamento;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints publicos do agendamento-confirmacao-service. {@code POST
 * /v1/agendamentos} (Story 1.1) resolve/cria o Paciente por CPF internamente
 * e registra um Agendamento em {@code AGUARDANDO_JANELA} -- sempre {@code
 * 201} sincrono quando os dados sao validos (Boundaries da spec 1.1). {@code
 * POST /v1/agendamentos/{id}/confirmacao} (Story 1.3, FR-4) transiciona um
 * Agendamento {@code AGUARDANDO_CONFIRMACAO} para {@code CONFIRMADO} --
 * {@code 200} sincrono, inclusive em confirmacao duplicada (sucesso
 * silencioso, Boundaries da spec 1.3). {@code POST /v1/agendamentos/{id}/recusa}
 * (Story 1.4, FR-5) transiciona um Agendamento {@code AGUARDANDO_CONFIRMACAO}
 * para {@code LIBERADO} com {@code motivoLiberacao = RECUSA} -- {@code 200}
 * sincrono (mesmo padrao de ConfirmarPresenca).
 *
 * <p>Erros de validacao/conflito/nao-encontrado viram RFC 7807 via
 * {@link AgendamentoExceptionHandler}.
 */
@RestController
public class AgendamentoController {

    private final RegistrarAgendamento registrarAgendamento;
    private final ConfirmarPresenca confirmarPresenca;
    private final RecusarPresenca recusarPresenca;
    private final ConsultarAgendamento consultarAgendamento;
    private final ListarAgendamentos listarAgendamentos;

    public AgendamentoController(RegistrarAgendamento registrarAgendamento, ConfirmarPresenca confirmarPresenca,
                                  RecusarPresenca recusarPresenca, ConsultarAgendamento consultarAgendamento,
                                  ListarAgendamentos listarAgendamentos) {
        this.registrarAgendamento = registrarAgendamento;
        this.confirmarPresenca = confirmarPresenca;
        this.recusarPresenca = recusarPresenca;
        this.consultarAgendamento = consultarAgendamento;
        this.listarAgendamentos = listarAgendamentos;
    }

    @PostMapping("/v1/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrarAgendamentoResponse registrar(@RequestBody RegistrarAgendamentoRequest request) {
        Agendamento agendamento = registrarAgendamento.registrar(
                request.cpf(), request.recursoId(), request.dataHoraAgendamento());
        return RegistrarAgendamentoResponse.de(agendamento);
    }

    @GetMapping("/v1/agendamentos")
    @ResponseStatus(HttpStatus.OK)
    public List<RegistrarAgendamentoResponse> listar() {
        return listarAgendamentos.listar().stream()
                .map(RegistrarAgendamentoResponse::de)
                .toList();
    }

    @GetMapping("/v1/agendamentos/{id}")
    @ResponseStatus(HttpStatus.OK)
    public RegistrarAgendamentoResponse consultar(@PathVariable("id") Long id) {
        Agendamento agendamento = consultarAgendamento.consultar(id);
        return RegistrarAgendamentoResponse.de(agendamento);
    }

    @PostMapping("/v1/agendamentos/{id}/confirmacao")
    @ResponseStatus(HttpStatus.OK)
    public void confirmar(@PathVariable("id") Long id) {
        confirmarPresenca.confirmar(id);
    }

    @PostMapping("/v1/agendamentos/{id}/recusa")
    @ResponseStatus(HttpStatus.OK)
    public void recusar(@PathVariable("id") Long id) {
        recusarPresenca.recusar(id);
    }
}
