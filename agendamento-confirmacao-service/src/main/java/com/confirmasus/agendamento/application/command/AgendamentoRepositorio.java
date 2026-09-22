package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.StatusAgendamento;

import java.util.List;
import java.util.Optional;

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

    /**
     * {@code UPDATE ... SET status = novoStatus, motivoLiberacao = motivo WHERE id = id AND status =
     * statusEsperado} (AD-4, spec 1.4) -- escrita condicional que inclui o campo motivoLiberacao.
     * Mesmo contrato de {@link #atualizarStatusSeAtual(Long, StatusAgendamento, StatusAgendamento)}.
     */
    boolean atualizarStatusComMotivo(Long id, StatusAgendamento statusEsperado, StatusAgendamento novoStatus, String motivoLiberacao);

    /**
     * Agendamentos com {@code status = AGUARDANDO_CONFIRMACAO} e
     * {@code janelaExpiraEm <= agora}, ordenados por {@code id} ascendente,
     * limitados a {@code limite} -- bloqueados via {@code FOR UPDATE SKIP
     * LOCKED} ate o fim da transacao do chamador (spec 1.5, poller
     * {@code ExpirarJanelaDeConfirmacao}). Mesmo contrato transacional de
     * {@link #buscarPendentesAberturaJanela(int)}.
     */
    List<Agendamento> buscarPendentesExpiracaoJanela(int limite);

    /**
     * Releitura pontual por {@code id} (spec 1.3, {@code ConfirmarPresenca}):
     * usada apos {@link #atualizarStatusSeAtual} para decidir, quando a
     * escrita condicional afeta 0 linhas, entre sucesso silencioso (ja
     * {@code CONFIRMADO}), {@code 409} (qualquer outro estado perdedor) e
     * {@code 404} ({@code Optional} vazio, id inexistente); tambem usada apos
     * uma transicao bem-sucedida para montar o payload do evento de outbox
     * (precisa de {@code pacienteId}, que a escrita condicional sozinha nao
     * retorna).
     */
    Optional<Agendamento> buscarPorId(Long id);
}
