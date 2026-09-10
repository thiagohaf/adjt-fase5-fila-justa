package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.ScoreReplica;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Caso de uso de upsert da réplica local de Score (Story 3.1b, Code Map):
 * consumido por {@code ScoreCalculadoConsumerJob} (infrastructure/relay) a
 * cada mensagem {@code ScoreCalculado} recebida da fila SQS FIFO assinante
 * de {@code score-calculado.fifo}. Constrói o {@link ScoreReplica}
 * candidato -- validação de invariantes no construtor do domínio (AD-2) --
 * e delega a decisão de substituir (last-write-wins) à porta
 * {@link ScoreReplicaRepositorio#upsertSeMaisRecente(ScoreReplica)}, que é
 * atômica no banco (ver seu javadoc).
 *
 * <p>{@code updatedAt} (quando a réplica foi tocada por último, distinto de
 * {@code occurredAt} -- quando o Score foi calculado na origem) vem do
 * {@link Clock} injetado, não de {@code Instant.now()} direto -- mesmo
 * padrão de testabilidade do {@code triagem-score-service}.
 */
public class AtualizarScoreReplica {

    private final ScoreReplicaRepositorio repositorio;
    private final Clock clock;

    public AtualizarScoreReplica(ScoreReplicaRepositorio repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    public void atualizar(long pacienteId, int score, Instant occurredAt, UUID eventId) {
        ScoreReplica candidata = new ScoreReplica(pacienteId, score, occurredAt, eventId, clock.instant());
        repositorio.upsertSeMaisRecente(candidata);
    }
}
