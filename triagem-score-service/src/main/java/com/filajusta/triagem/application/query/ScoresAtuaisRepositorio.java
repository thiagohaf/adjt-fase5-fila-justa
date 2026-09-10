package com.filajusta.triagem.application.query;

import java.util.List;

/**
 * Porta de saida para listagem de {@link ScoreAtual} (Story 3.1a).
 * Implementada em {@code infrastructure/persistence} lendo diretamente de
 * {@code eventos_outbox} -- unica tabela que carrega {@code eventId} e
 * {@code occurredAt} do evento {@code ScoreCalculado} junto do {@code
 * pacienteId} e do Score (ver o adapter para detalhes).
 */
public interface ScoresAtuaisRepositorio {

    /** Todos os Scores atuais, sem paginacao nem filtros (Boundaries da spec 3.1a). */
    List<ScoreAtual> listarTodos();
}
