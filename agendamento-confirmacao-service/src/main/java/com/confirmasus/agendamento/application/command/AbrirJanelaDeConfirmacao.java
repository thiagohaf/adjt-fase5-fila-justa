package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.EventoOutbox;
import com.confirmasus.agendamento.domain.StatusAgendamento;
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
 * Poller que abre a Janela de Confirmacao (spec 1.2, FR-3): transiciona
 * {@link StatusAgendamento#AGUARDANDO_JANELA} para
 * {@link StatusAgendamento#AGUARDANDO_CONFIRMACAO} via escrita condicional
 * (AD-4, {@code UPDATE ... WHERE status = 'AGUARDANDO_JANELA'}) e publica
 * {@code NotificacaoConfirmacaoPublicada} via outbox (AD-3) na mesma
 * transacao -- mesmo molde de {@code RelaySnsPublisherJob.publicarPendentes}
 * (matching-alocacao-service), adaptado para o dominio de Agendamento.
 *
 * <p>{@code @Transactional} inteiro (leitura + escrita condicional + grava
 * outbox): sem isso, {@code buscarPendentesAberturaJanela} (SELECT ... FOR
 * UPDATE SKIP LOCKED) e {@code atualizarStatusSeAtual} rodariam em
 * transacoes separadas e o lock de linha seria liberado assim que a leitura
 * retornasse, permitindo que duas tasks ECS concorrentes (I/O Matrix da spec
 * 1.2) processassem o MESMO Agendamento.
 *
 * <p>Vive em {@code application/command} (nao {@code infrastructure/relay})
 * porque conhece o dominio de Agendamento -- diferente de
 * {@code RelaySnsPublisherJob} (copiado do molde, ja generico, so conhece
 * {@code EventoOutbox}). {@code @Scheduled}/{@code @Transactional} aqui
 * seguem o mesmo precedente de {@code RegistrarAgendamento} (AD-2: anotacoes
 * de framework sao aceitas em application/command, o dominio em si
 * permanece framework-agnostico).
 *
 * <p>Cadencia do poller ({@code confirmasus.agendamento.abertura-janela.
 * poll-interval-ms}) e {@code [ASSUMPTION]} 5000ms -- mesmo default do
 * molde de outbox-relay (Ask First da spec 1.2: nenhuma cadencia exata foi
 * pedida ao humano).
 */
public class AbrirJanelaDeConfirmacao {

    private static final Logger log = LoggerFactory.getLogger(AbrirJanelaDeConfirmacao.class);
    private static final String EVENT_TYPE = "NotificacaoConfirmacaoPublicada";

    private final AgendamentoRepositorio agendamentoRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;
    private final int loteTamanho;

    public AbrirJanelaDeConfirmacao(AgendamentoRepositorio agendamentoRepositorio,
                                     EventoOutboxRepositorio eventoOutboxRepositorio,
                                     Clock clock,
                                     int loteTamanho) {
        this.agendamentoRepositorio = agendamentoRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
        // batch-size <= 0 quebraria a query de leitura (LIMIT invalido) a
        // cada execucao -- piso de 1 em vez de propagar o valor invalido
        // (mesmo precedente de RelaySnsPublisherJob).
        this.loteTamanho = Math.max(1, loteTamanho);
    }

    @Scheduled(fixedDelayString = "${confirmasus.agendamento.abertura-janela.poll-interval-ms:5000}")
    public void abrirJanelas() { // FIX-2: removido @Transactional (era aplicado ao lote inteiro, revertendo trabalho anterior)
        List<Agendamento> pendentes;
        try {
            pendentes = agendamentoRepositorio.buscarPendentesAberturaJanela(loteTamanho);
        } catch (RuntimeException e) {
            // Nenhuma excecao pode escapar do poller (nao pode derrubar a app).
            log.error("Falha ao ler Agendamentos pendentes de abertura de janela "
                    + "-- tenta de novo na proxima execucao", e);
            return;
        }

        for (Agendamento agendamento : pendentes) {
            try {
                processar(agendamento);
            } catch (RuntimeException e) {
                // Isola a falha por item (mesmo precedente de
                // RelaySnsPublisherJob.publicarPendentes): sem isso, uma
                // unica falha no meio do lote propagaria e, por o metodo
                // inteiro ser @Transactional, desfaria as transicoes/eventos
                // ja gravados para os Agendamentos anteriores nesta mesma
                // execucao (code review, step-04).
                log.error("Falha ao processar abertura de janela do Agendamento {} "
                        + "-- tenta de novo na proxima execucao", agendamento.getId(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // FIX-2: cada item é transação independente
    public void processar(Agendamento agendamento) {
        boolean transicionado = agendamentoRepositorio.atualizarStatusSeAtual(
                agendamento.getId(), StatusAgendamento.AGUARDANDO_JANELA, StatusAgendamento.AGUARDANDO_CONFIRMACAO);
        if (!transicionado) {
            // Reprocessamento do mesmo Agendamento (I/O Matrix): outra
            // instancia do poller ja o transicionou primeiro --
            // idempotencia por design, nao um erro; nenhum evento novo.
            log.warn("Agendamento {} ja nao estava mais em AGUARDANDO_JANELA -- "
                    + "outra instancia do poller ja processou, nenhum evento novo gravado",
                    agendamento.getId());
            return;
        }
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
