package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.Cpf;
import com.filajusta.agendamento.domain.DataHoraAgendamentoInvalidaException;
import com.filajusta.agendamento.domain.Paciente;
import com.filajusta.agendamento.domain.RecursoIdInvalidoException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Caso de uso de {@code POST /v1/agendamentos} (spec 1.1, AC principal):
 * resolve/cria o {@link Paciente} por CPF (FR-2, idempotente, via
 * {@link ResolverOuCriarPaciente}), valida {@code recursoId} (formato --
 * UUID bem formado, nao nulo/vazio; sem checagem de existencia contra o
 * catalogo real de Recurso, AD-1) e {@code dataHoraAgendamento} (instante
 * estritamente futuro) e persiste o {@link Agendamento} resultante, sempre
 * nascendo em {@code AGUARDANDO_JANELA}.
 *
 * <p>Ordem deterministica de validacao (mesmo espirito do extinto
 * {@code RegistrarTriagem}, mas sem "primeiro campo invalido" cross-campo
 * exigido pela spec 1.1): CPF, depois {@code recursoId}, depois
 * {@code dataHoraAgendamento} -- qualquer falha lanca antes de resolver o
 * Paciente ou persistir qualquer coisa (Boundaries: "nenhuma rejeicao
 * persiste Paciente nem Agendamento").
 *
 * <p>{@code @Transactional} vive aqui (nao em {@code domain/}, que
 * permanece framework-agnostico por AD-2): resolver/criar o Paciente e
 * persistir o Agendamento precisam ser atomicos.
 *
 * <p>{@code janelaDuracao} (spec 1.2, Design Notes) calcula
 * {@code janelaAbreEm = agora + janelaDuracao} -- {@code [ASSUMPTION]}
 * valor fixo configuravel ({@code filajusta.agendamento.janela.duracao},
 * default {@code PT30M}), ja que o PRD nao define o numero exato. O poller
 * {@code AbrirJanelaDeConfirmacao} le essa coluna para decidir quando abrir
 * a Janela de Confirmacao.
 */
public class RegistrarAgendamento {

    private final ResolverOuCriarPaciente resolverOuCriarPaciente;
    private final AgendamentoRepositorio agendamentoRepositorio;
    private final Clock clock;
    private final Duration janelaDuracao;

    public RegistrarAgendamento(ResolverOuCriarPaciente resolverOuCriarPaciente,
                                 AgendamentoRepositorio agendamentoRepositorio,
                                 Clock clock,
                                 Duration janelaDuracao) {
        this.resolverOuCriarPaciente = resolverOuCriarPaciente;
        this.agendamentoRepositorio = agendamentoRepositorio;
        this.clock = clock;
        this.janelaDuracao = janelaDuracao;
    }

    @Transactional
    public Agendamento registrar(String cpfTexto, String recursoIdTexto, Instant dataHoraAgendamento) {
        // Ordem deterministica: CPF -> recursoId -> dataHoraAgendamento,
        // antes de qualquer resolucao/persistencia (Boundaries da spec 1.1).
        Cpf cpf = new Cpf(cpfTexto);
        UUID recursoId = validarRecursoId(recursoIdTexto);
        Instant agora = clock.instant();
        validarDataHoraAgendamento(dataHoraAgendamento, agora);

        Paciente paciente = resolverOuCriarPaciente.resolver(cpf);

        Instant janelaAbreEm = agora.plus(janelaDuracao);
        Agendamento agendamentoParaSalvar =
                Agendamento.novo(paciente.getId(), recursoId, dataHoraAgendamento, agora, janelaAbreEm);
        return agendamentoRepositorio.salvar(agendamentoParaSalvar);
    }

    private static UUID validarRecursoId(String recursoIdTexto) {
        if (recursoIdTexto == null || recursoIdTexto.isBlank()) {
            throw new RecursoIdInvalidoException("recursoId e obrigatorio");
        }
        try {
            return UUID.fromString(recursoIdTexto);
        } catch (IllegalArgumentException e) {
            throw new RecursoIdInvalidoException("recursoId deve ser um UUID valido");
        }
    }

    private static void validarDataHoraAgendamento(Instant dataHoraAgendamento, Instant agora) {
        if (dataHoraAgendamento == null) {
            throw new DataHoraAgendamentoInvalidaException("dataHoraAgendamento e obrigatorio");
        }
        if (!dataHoraAgendamento.isAfter(agora)) {
            throw new DataHoraAgendamentoInvalidaException("dataHoraAgendamento deve ser um instante futuro");
        }
    }
}
