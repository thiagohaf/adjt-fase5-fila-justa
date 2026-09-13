package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.LiberacaoAgendada;

import java.util.List;
import java.util.UUID;

/**
 * Porta de saída para persistência de {@link LiberacaoAgendada} (Story
 * 3-4a1). Implementada em {@code infrastructure.persistence} (JPA, schema
 * {@code matching_alocacao}, tabela {@code liberacao_agendada}).
 *
 * <p>{@link #salvar(LiberacaoAgendada)} é chamado por {@link ConfirmarAlocacao}
 * na mesma transação da confirmação -- mesmo padrão de
 * {@link EventoOutboxRepositorio#salvar(com.filajusta.matching.domain.EventoOutbox)}.
 *
 * <p>{@link #buscarPendentes(int)} é o contrato que o relay da Story 3-4a2
 * vai consumir (poller que publica a mensagem de liberação e depois chama
 * {@link #marcarComoEnviado(UUID)}) -- mesmo molde de
 * {@code EventoOutboxRepositorio#buscarNaoPublicados(int)}: {@code SELECT
 * ... FOR UPDATE SKIP LOCKED}, só protege de fato contra duas instâncias do
 * job lendo/publicando a MESMA linha quando chamado dentro da MESMA
 * transação que também chama {@link #marcarComoEnviado(UUID)} em seguida.
 * Nenhuma story atual chama este método (Boundaries da spec 3-4a1: sem
 * relay, sem consumidor) -- é infraestrutura pura, coberta só pelo teste de
 * integração do adapter.
 *
 * <p>{@link #marcarComoEnviado(UUID)} fica declarado aqui mas só ganha
 * chamador real na Story 3-4a2 (Code Map da spec 3-4a1) -- mesmo raciocínio
 * de {@code EventoOutboxRepositorio#marcarComoPublicado(long)}.
 */
public interface LiberacaoAgendadaRepositorio {

    void salvar(LiberacaoAgendada liberacaoAgendada);

    /**
     * Linhas ainda não enviadas, ordenadas por {@code criadoEm} ascendente,
     * limitadas a {@code limite} -- bloqueadas via {@code FOR UPDATE SKIP
     * LOCKED} (ver contrato transacional acima) até o fim da transação do
     * chamador.
     */
    List<LiberacaoAgendada> buscarPendentes(int limite);

    /**
     * Marca a linha {@code alocacaoId} como enviada agora, apenas se ainda
     * não estava marcada. Retorna {@code false} quando outra instância do
     * job já a marcou primeiro (corrida entre instâncias) -- não é um erro.
     */
    boolean marcarComoEnviado(UUID alocacaoId);
}
