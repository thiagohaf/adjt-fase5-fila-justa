package com.filajusta.matching.application.query;

/**
 * Porta de saída para o bootstrap síncrono a frio da réplica local de Score
 * (Story 3.1c): disparada por {@link ConsultarFilaPriorizada} quando
 * {@link FilaRepositorio#estaVazia()}. Implementada em
 * {@code infrastructure/bootstrap} ({@code ScoreBootstrapService}), que
 * chama {@code GET /internal/scores} (Story 3.1a, triagem-score-service) e
 * upserta cada linha reaproveitando {@code AtualizarScoreReplica} (Story
 * 3.1b) -- mesma disciplina de inversão de dependência de
 * {@link com.filajusta.matching.application.command.ScoreReplicaRepositorio}
 * (AD-2): esta camada nunca depende de {@code RestClient} nem de nenhum
 * detalhe HTTP diretamente.
 *
 * <p>{@link ScoreBootstrapIndisponivelException} -- falha do bootstrap
 * (Boundaries da spec 3.1c: "Falha no bootstrap retorna 503... réplica
 * permanece vazia, nunca fila incompleta silenciosa") -- propaga sem ser
 * capturada até {@code infrastructure/web}, traduzida para RFC 7807.
 */
public interface ScoreBootstrap {

    void bootstrapar();
}
