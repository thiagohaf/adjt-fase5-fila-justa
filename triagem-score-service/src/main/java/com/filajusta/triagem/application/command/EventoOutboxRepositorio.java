package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.EventoOutbox;

import java.util.List;

/**
 * Porta de saida para persistencia de {@link EventoOutbox} (AD-3).
 * Implementada em {@code infrastructure/persistence} (JPA, schema
 * {@code triagem_score}, tabela {@code eventos_outbox}).
 *
 * <p>{@link #buscarNaoPublicados(int)} e {@link #marcarComoPublicado(long)}
 * existem para o relay (Story 3.0, {@code RelaySnsPublisherJob},
 * {@code infrastructure/relay}): padrao outbox classico -- poller le linhas
 * com {@code publicado_em IS NULL} ordenadas por {@code id}, publica no SNS
 * FIFO e so marca publicada apos confirmacao do broker (nunca antes).
 *
 * <p><b>Contrato transacional (achado do code review):</b>
 * {@link #buscarNaoPublicados(int)} usa {@code SELECT ... FOR UPDATE SKIP
 * LOCKED} -- so protege de fato contra duas instancias do job lendo e
 * publicando a MESMA linha (exigido pela I/O Matrix, frozen na spec 3.0,
 * cenario "Multiplas instancias do job rodando") quando chamado dentro da
 * MESMA transacao que tambem chama {@link #marcarComoPublicado(long)} logo
 * em seguida ({@code RelaySnsPublisherJob.publicarPendentes} e
 * {@code @Transactional} justamente por isso) -- fora de uma transacao ja
 * aberta, o lock da leitura e liberado assim que o metodo retorna, antes do
 * ack do SNS, e a protecao vira inutil.
 */
public interface EventoOutboxRepositorio {

    void salvar(EventoOutbox evento);

    /**
     * Linhas ainda nao publicadas, ordenadas por {@code id} ascendente,
     * limitadas a {@code limite} -- bloqueadas via {@code FOR UPDATE SKIP
     * LOCKED} (ver contrato transacional acima) ate o fim da transacao do
     * chamador.
     */
    List<EventoOutbox> buscarNaoPublicados(int limite);

    /**
     * Marca a linha {@code id} como publicada agora, apenas se ainda nao
     * estava marcada. Retorna {@code false} quando outra instancia do job ja
     * a marcou primeiro (corrida entre instancias, Boundaries da spec 3.0) --
     * nao e um erro, o chamador nao deve tratar como falha de publicacao.
     */
    boolean marcarComoPublicado(long id);
}
