package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.EventoOutbox;
import com.confirmasus.agendamento.domain.StatusAgendamento;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso de {@code POST /v1/agendamentos/{id}/confirmacao} (spec 1.3,
 * FR-4): transiciona {@link StatusAgendamento#AGUARDANDO_CONFIRMACAO} para
 * {@link StatusAgendamento#CONFIRMADO} via escrita condicional (AD-4, {@code
 * UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'}, reaproveitando {@link
 * AgendamentoRepositorio#atualizarStatusSeAtual}) e publica {@code
 * ConfirmacaoRegistrada} via outbox (AD-3) na mesma transacao -- mesmo molde
 * de {@code AbrirJanelaDeConfirmacao} (Story 1.2), adaptado de poller em lote
 * para comando pontual disparado pela API.
 *
 * <p>{@code @Transactional} inteiro (escrita condicional + releitura +
 * grava outbox): a decisao de negocio (sucesso silencioso vs. {@code 409} vs.
 * {@code 404}) depende de uma releitura apos a escrita condicional falhar --
 * precisa da mesma atomicidade de {@code AbrirJanelaDeConfirmacao
 * .abrirJanelas} para que a corrida "Confirmacao vs. Confirmacao" (I/O
 * Matrix) nunca deixe as duas requisicoes concorrentes decidirem como se
 * tivessem vencido.
 *
 * <p>Fluxo (nunca leitura-depois-escrita sem guarda, AD-4): tenta a escrita
 * condicional primeiro, sem nenhuma leitura previa. Se afetar 1 linha
 * (venceu a corrida), releh o Agendamento so para completar o payload do
 * evento (precisa de {@code pacienteId}) e grava o outbox. Se afetar 0
 * linhas, releh por {@code id}: vazio -&gt; {@link
 * AgendamentoNaoEncontradoException} ({@code 404}); ja {@code CONFIRMADO} --
 * mesma chamada de confirmacao reprocessada ou corrida perdida contra outra
 * confirmacao identica -&gt; sucesso silencioso, nenhum evento novo (I/O
 * Matrix: "Confirmacao duplicada"); qualquer outro estado ({@code
 * AGUARDANDO_JANELA}, {@code LIBERADO}) -&gt; {@link
 * AgendamentoForaDaJanelaException} ({@code 409}).
 */
public class ConfirmarPresenca {

    private static final String EVENT_TYPE = "ConfirmacaoRegistrada";

    private final AgendamentoRepositorio agendamentoRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public ConfirmarPresenca(AgendamentoRepositorio agendamentoRepositorio,
                              EventoOutboxRepositorio eventoOutboxRepositorio,
                              Clock clock) {
        this.agendamentoRepositorio = agendamentoRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
    public void confirmar(Long id) {
        boolean transicionado = agendamentoRepositorio.atualizarStatusSeAtual(
                id, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO);

        if (transicionado) {
            registrarConfirmacao(id);
            return;
        }

        Agendamento atual = agendamentoRepositorio.buscarPorId(id)
                .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
        if (atual.getStatus() == StatusAgendamento.CONFIRMADO) {
            // Confirmacao duplicada (I/O Matrix): ja CONFIRMADO -- sucesso
            // silencioso, nenhum evento novo.
            return;
        }

        if (atual.getStatus() == StatusAgendamento.AGUARDANDO_CONFIRMACAO) {
            // Corrida entre a escrita condicional falhar e esta releitura: a
            // janela pode ter aberto nesse intervalo (ex.: o poller
            // AbrirJanelaDeConfirmacao rodou entre as duas chamadas) -- uma
            // unica retentativa antes de decidir, nunca loop infinito
            // (achado do code review adversarial da spec 1.3).
            boolean transicionadoNaRetentativa = agendamentoRepositorio.atualizarStatusSeAtual(
                    id, StatusAgendamento.AGUARDANDO_CONFIRMACAO, StatusAgendamento.CONFIRMADO);
            if (transicionadoNaRetentativa) {
                registrarConfirmacao(id);
                return;
            }
            atual = agendamentoRepositorio.buscarPorId(id)
                    .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
            if (atual.getStatus() == StatusAgendamento.CONFIRMADO) {
                return;
            }
        }

        throw new AgendamentoForaDaJanelaException(id, atual.getStatus());
    }

    private void registrarConfirmacao(Long id) {
        Agendamento agendamento = agendamentoRepositorio.buscarPorId(id)
                .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
        eventoOutboxRepositorio.salvar(novoEvento(agendamento));
    }

    private EventoOutbox novoEvento(Agendamento agendamento) {
        Instant agora = clock.instant();
        Map<String, Object> payload = Map.of(
                "agendamentoId", agendamento.getId(),
                "pacienteId", agendamento.getPacienteId());
        return new EventoOutbox(null, UUID.randomUUID(), EVENT_TYPE, agora, 1,
                "agendamento-" + agendamento.getId(), payload);
    }
}
