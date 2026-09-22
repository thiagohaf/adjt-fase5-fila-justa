package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.EventoOutbox;
import com.confirmasus.agendamento.domain.MotivoLiberacao;
import com.confirmasus.agendamento.domain.StatusAgendamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso de {@code POST /v1/agendamentos/{id}/recusa} (spec 1.4,
 * FR-5): transiciona {@link StatusAgendamento#AGUARDANDO_CONFIRMACAO} para
 * {@link StatusAgendamento#LIBERADO} com {@code motivoLiberacao = RECUSA} via
 * escrita condicional (AD-4, {@code UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'},
 * reaproveitando {@link AgendamentoRepositorio#atualizarStatusComMotivo}) e
 * publica dois eventos via outbox (AD-3) na mesma transacao: {@code RecusaRegistrada}
 * + {@code VagaLiberada}.
 *
 * <p>Padrão idêntico ao {@code ConfirmarPresenca} (Story 1.3): {@code @Transactional}
 * inteiro (escrita condicional + releitura + grava outbox), com releitura após
 * falha da escrita condicional para decidir entre sucesso silencioso, {@code 409}
 * ou {@code 404}.
 *
 * <p>Fluxo (nunca leitura-depois-escrita sem guarda, AD-4): tenta a escrita
 * condicional primeiro, sem nenhuma leitura previa. Se afetar 1 linha
 * (venceu a corrida), releh o Agendamento so para completar o payload dos eventos
 * (precisa de {@code pacienteId}) e grava os dois eventos no outbox. Se afetar 0
 * linhas, releh por {@code id}: vazio -> {@link
 * AgendamentoNaoEncontradoException} ({@code 404}); ja {@code LIBERADO/RECUSA} --
 * mesma chamada de recusa reprocessada ou corrida perdida contra outra
 * recusa identica -> sucesso silencioso, nenhum evento novo (I/O
 * Matrix: "Recusa duplicada"); qualquer outro estado ({@code
 * AGUARDANDO_JANELA}, {@code CONFIRMADO}, {@code LIBERADO/NAO_CONFIRMADO}) ->
 * {@link AgendamentoForaDaJanelaException} ({@code 409}).
 *
 * <p><b>Atomicidade transacional</b> (AD-3/AD-4, spec 1.4): {@code @Transactional}
 * garante que se qualquer um dos dois eventos ({@code RecusaRegistrada} ou
 * {@code VagaLiberada}) falhar ao ser persistido no outbox, a escrita condicional
 * também é revertida (rollback de toda a transação), mantendo consistência:
 * nunca há um evento sem a transição de status correspondente.
 */
public class RecusarPresenca {

    private static final Logger log = LoggerFactory.getLogger(RecusarPresenca.class);
    private static final String EVENT_TYPE_RECUSA = "RecusaRegistrada";
    private static final String EVENT_TYPE_VAGA_LIBERADA = "VagaLiberada";
    private static final int RETRY_ATTEMPTS = 1;

    private final AgendamentoRepositorio agendamentoRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public RecusarPresenca(AgendamentoRepositorio agendamentoRepositorio,
                            EventoOutboxRepositorio eventoOutboxRepositorio,
                            Clock clock) {
        this.agendamentoRepositorio = agendamentoRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
    public void recusar(Long id) {
        Objects.requireNonNull(id, "id nao pode ser null");
        if (id <= 0) {
            throw new IllegalArgumentException("id deve ser positivo");
        }

        log.info("Iniciando recusa do agendamento {}", id);

        String motivoLiberacao = MotivoLiberacao.RECUSA.name();
        try {
            MotivoLiberacao.valueOf(motivoLiberacao);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("MotivoLiberacao inválido: " + motivoLiberacao, e);
        }

        boolean transicionado = agendamentoRepositorio.atualizarStatusComMotivo(
                id, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                motivoLiberacao);

        if (transicionado) {
            log.debug("Escrita condicional sucedeu na primeira tentativa para agendamento {}", id);
            registrarRecusa(id);
            log.info("Recusa registrada com sucesso para agendamento {}", id);
            return;
        }

        log.debug("Escrita condicional falhou, executando releitura para agendamento {}", id);
        Agendamento atual = agendamentoRepositorio.buscarPorId(id)
                .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
        if (atual.getStatus() == StatusAgendamento.LIBERADO &&
            motivoLiberacao.equals(atual.getMotivoLiberacao())) {
            log.info("Recusa duplicada detectada para agendamento {} -- sucesso silencioso", id);
            return;
        }

        if (atual.getStatus() == StatusAgendamento.AGUARDANDO_CONFIRMACAO) {
            log.debug("Agendamento {} ainda em AGUARDANDO_CONFIRMACAO, tentando retentativa", id);
            boolean transicionadoNaRetentativa = agendamentoRepositorio.atualizarStatusComMotivo(
                    id, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.LIBERADO,
                    motivoLiberacao);
            if (transicionadoNaRetentativa) {
                log.debug("Retentativa sucedeu para agendamento {}", id);
                registrarRecusa(id);
                log.info("Recusa registrada com sucesso para agendamento {} (retentativa)", id);
                return;
            }
            atual = agendamentoRepositorio.buscarPorId(id)
                    .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
            if (atual.getStatus() == StatusAgendamento.LIBERADO &&
                motivoLiberacao.equals(atual.getMotivoLiberacao())) {
                log.info("Recusa duplicada após retentativa para agendamento {} -- sucesso silencioso", id);
                return;
            }
        }

        log.warn("Agendamento {} fora da janela de confirmação, status atual: {}", id, atual.getStatus());
        throw new AgendamentoForaDaJanelaException(id, atual.getStatus());
    }

    private void registrarRecusa(Long id) {
        Agendamento agendamento = agendamentoRepositorio.buscarPorId(id)
                .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
        // Publica dois eventos na mesma transacao
        eventoOutboxRepositorio.salvar(novoEventoRecusa(agendamento));
        eventoOutboxRepositorio.salvar(novoEventoVaga(agendamento));
    }

    private EventoOutbox novoEventoRecusa(Agendamento agendamento) {
        Instant agora = clock.instant();
        Map<String, Object> payload = Map.of(
                "agendamentoId", agendamento.getId(),
                "pacienteId", agendamento.getPacienteId(),
                "motivo", MotivoLiberacao.RECUSA.name());
        return new EventoOutbox(null, UUID.randomUUID(), EVENT_TYPE_RECUSA, agora, 1,
                "agendamento-" + agendamento.getId(), payload);
    }

    private EventoOutbox novoEventoVaga(Agendamento agendamento) {
        Instant agora = clock.instant();
        Map<String, Object> payload = Map.of(
                "agendamentoId", agendamento.getId(),
                "recursoId", agendamento.getRecursoId(),
                "dataHoraAgendamento", agendamento.getDataHoraAgendamento());
        return new EventoOutbox(null, UUID.randomUUID(), EVENT_TYPE_VAGA_LIBERADA, agora, 1,
                "agendamento-" + agendamento.getId(), payload);
    }
}
