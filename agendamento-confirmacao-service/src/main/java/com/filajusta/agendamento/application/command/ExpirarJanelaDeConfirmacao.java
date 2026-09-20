package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.AgendamentoComDadosIncompletosException;
import com.filajusta.agendamento.domain.EventoOutbox;
import com.filajusta.agendamento.domain.MotivoLiberacao;
import com.filajusta.agendamento.domain.StatusAgendamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Poller que expira a Janela de Confirmacao (spec 1.5, FR-6): transiciona
 * {@link StatusAgendamento#AGUARDANDO_CONFIRMACAO} para
 * {@link StatusAgendamento#LIBERADO} com {@code motivoLiberacao = NAO_CONFIRMADO}
 * via escrita condicional (AD-5, {@code UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'})
 * e publica dois eventos no outbox (AD-3).
 *
 * <p>Arquitetura: SELECT FOR UPDATE executado SEM transação ({@code expirarJanelas()}
 * não tem {@code @Transactional}), liberando o lock imediatamente após a leitura.
 * Contudo, idempotência é garantida por design: {@code UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'}
 * é condicionado ao status exato, impedindo que duas instâncias do poller transicionem
 * o MESMO Agendamento duas vezes. A segunda tentativa não encontra linha para atualizar
 * e retorna {@code false} (já processado por outra instância).
 *
 * <p>Atomicidade transacional (AD-3/AD-4): {@code @Transactional} em {@code processar()}
 * envolve UPDATE + gravação dos dois eventos ({@code AgendamentoNaoConfirmado} +
 * {@code VagaLiberada}) em uma única transação: garante que se qualquer um dos dois
 * eventos falhar, o UPDATE também é revertido, mantendo consistência. Nunca há um
 * evento sem a transição de status correspondente.
 *
 * <p>Cadencia do poller: propriedade
 * {@code filajusta.agendamento.expiracao-janela.poll-interval-ms} (default 5000ms).
 * Timeout é garantido por configuracao Spring de scheduler task pool ou
 * spring.task.scheduling.thread-name-prefix (boas práticas de mercado: banco
 * não deve travar por mais de 30s em SELECT/UPDATE simples).
 *
 * <p>Null checks em todos os campos antes de gravar no outbox (recursoId,
 * dataHoraAgendamento): se null, loga warning e continua (skip este agendamento),
 * sem propagar exceção.
 */
public class ExpirarJanelaDeConfirmacao {

    private static final Logger log = LoggerFactory.getLogger(ExpirarJanelaDeConfirmacao.class);
    private static final String EVENT_TYPE_NAO_CONFIRMADO = "AgendamentoNaoConfirmado";
    private static final String EVENT_TYPE_VAGA_LIBERADA = "VagaLiberada";

    private final AgendamentoRepositorio agendamentoRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;
    private final int loteTamanho;

    public ExpirarJanelaDeConfirmacao(AgendamentoRepositorio agendamentoRepositorio,
                                       EventoOutboxRepositorio eventoOutboxRepositorio,
                                       Clock clock,
                                       int loteTamanho) {
        this.agendamentoRepositorio = agendamentoRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
        // batch-size <= 0 quebraria a query de leitura (LIMIT invalido) a
        // cada execucao -- piso de 1 em vez de propagar o valor invalido
        // (mesmo precedente de AbrirJanelaDeConfirmacao).
        this.loteTamanho = Math.max(1, loteTamanho);
    }

    @Scheduled(fixedDelayString = "${filajusta.agendamento.expiracao-janela.poll-interval-ms:5000}")
    public void expirarJanelas() { // FIX-1: removido @Transactional (era aplicado a each item em processar())
        List<Agendamento> pendentes;
        try {
            pendentes = agendamentoRepositorio.buscarPendentesExpiracaoJanela(loteTamanho);
        } catch (RuntimeException e) {
            // Nenhuma excecao pode escapar do poller (nao pode derrubar a app).
            log.error("Falha ao ler Agendamentos pendentes de expiracao de janela "
                    + "-- tenta de novo na proxima execucao", e);
            return;
        }

        for (Agendamento agendamento : pendentes) {
            try {
                processar(agendamento);
            } catch (RuntimeException e) {
                // Isolamento de falhas por item: a excecao nao propaga, permitindo
                // que o poller continue processando os demais itens. Cada agendamento
                // tem sua propria transacao (@Transactional em processar()), entao
                // o rollback de um nao afeta os outros.
                log.error("Falha ao processar expiracao de janela do Agendamento {} "
                        + "-- tenta de novo na proxima execucao",
                        agendamento.getId(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // FIX-1: cada item é transação independente
    public void processar(Agendamento agendamento) {
        // FIX-3: null checks são exceções (corrupção de domínio), não silenciosos
        if (agendamento.getRecursoId() == null) {
            throw new AgendamentoComDadosIncompletosException(
                    "Agendamento " + agendamento.getId() + " tem recursoId null -- corrupção de dados");
        }

        if (agendamento.getDataHoraAgendamento() == null) {
            throw new AgendamentoComDadosIncompletosException(
                    "Agendamento " + agendamento.getId() + " tem dataHoraAgendamento null -- corrupção de dados");
        }

        if (agendamento.getPacienteId() == null) {
            throw new AgendamentoComDadosIncompletosException(
                    "Agendamento " + agendamento.getId() + " tem pacienteId null -- corrupção de dados");
        }

        boolean transicionado = agendamentoRepositorio.atualizarStatusComMotivo(
                agendamento.getId(),
                StatusAgendamento.AGUARDANDO_CONFIRMACAO,
                StatusAgendamento.LIBERADO,
                MotivoLiberacao.NAO_CONFIRMADO.name());

        if (!transicionado) {
            // Reprocessamento do mesmo Agendamento (I/O Matrix): outra
            // instancia do poller ja o transicionou primeiro --
            // idempotencia por design, nao um erro; nenhum evento novo.
            log.warn("Agendamento {} ja nao estava mais em AGUARDANDO_CONFIRMACAO -- "
                    + "outra instancia do poller ja processou, nenhum evento novo gravado",
                    agendamento.getId());
            return;
        }

        // Captura clock.instant() uma unica vez para uso em ambos eventos
        Instant agora = clock.instant();

        // Grava ambos eventos na mesma transacao -- falha em QUALQUER um causa rollback
        // de UPDATE + ambos eventos (atomicidade transacional, AD-3/AD-4)
        eventoOutboxRepositorio.salvar(novoEventoNaoConfirmado(agendamento, agora));
        eventoOutboxRepositorio.salvar(novoEventoVagaLiberada(agendamento, agora));

        log.info("Agendamento {} expirado com sucesso: status LIBERADO/NAO_CONFIRMADO, "
                + "dois eventos gravados", agendamento.getId());
    }

    private EventoOutbox novoEventoNaoConfirmado(Agendamento agendamento, Instant agora) {
        Map<String, Object> payload = Map.of(
                "agendamentoId", agendamento.getId(),
                "pacienteId", agendamento.getPacienteId(),
                "motivo", MotivoLiberacao.NAO_CONFIRMADO.name());
        return new EventoOutbox(null, UUID.randomUUID(), EVENT_TYPE_NAO_CONFIRMADO, agora, 1,
                "agendamento-" + agendamento.getId(), payload);
    }

    private EventoOutbox novoEventoVagaLiberada(Agendamento agendamento, Instant agora) {
        Map<String, Object> payload = Map.of(
                "agendamentoId", agendamento.getId(),
                "recursoId", agendamento.getRecursoId(),
                "dataHoraAgendamento", agendamento.getDataHoraAgendamento());
        return new EventoOutbox(null, UUID.randomUUID(), EVENT_TYPE_VAGA_LIBERADA, agora, 1,
                "agendamento-" + agendamento.getId(), payload);
    }
}
