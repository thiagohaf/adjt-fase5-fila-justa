package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.StatusAgendamento;

import java.util.List;

/**
 * Porta de saida para persistencia de {@link Agendamento}. Implementada em
 * {@code infrastructure/persistence} (JPA, schema
 * {@code agendamento_confirmacao}).
 *
 * <p>{@link #buscarPendentesAberturaJanela(int)} e
 * {@link #atualizarStatusSeAtual(Long, StatusAgendamento, StatusAgendamento)}
 * existem para o poller {@code AbrirJanelaDeConfirmacao} (spec 1.2, AD-4):
 * mesmo contrato transacional de {@code EventoOutboxRepositorio
 * #buscarNaoPublicados}/{@code #marcarComoPublicado} -- a leitura usa
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} e so protege de fato contra
 * corrida entre instancias do poller quando chamada dentro da MESMA
 * transacao que tambem executa a escrita condicional em seguida
 * ({@code AbrirJanelaDeConfirmacao.abrirJanelas} e {@code @Transactional}
 * justamente por isso).
 */
public interface AgendamentoRepositorio {

    Agendamento salvar(Agendamento agendamento);

    /**
     * Agendamentos com {@code status = AGUARDANDO_JANELA} e
     * {@code janelaAbreEm <= agora}, ordenados por {@code id} ascendente,
     * limitados a {@code limite} -- bloqueados via {@code FOR UPDATE SKIP
     * LOCKED} ate o fim da transacao do chamador (ver contrato transacional
     * acima).
     */
    List<Agendamento> buscarPendentesAberturaJanela(int limite);

    /**
     * {@code UPDATE ... SET status = novoStatus WHERE id = id AND status =
     * statusEsperado} (AD-4) -- escrita condicional que garante idempotencia
     * de reprocessamento: retorna {@code false} quando o Agendamento ja nao
     * estava mais em {@code statusEsperado} (outra instancia do poller ja
     * processou), sem regravar nada. Nao e um erro.
     */
    boolean atualizarStatusSeAtual(Long id, StatusAgendamento statusEsperado, StatusAgendamento novoStatus);
}
