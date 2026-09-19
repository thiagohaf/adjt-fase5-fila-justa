package com.filajusta.matching.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Réplica local, somente-leitura, do Score de um Paciente (Story 3.1b) --
 * mantida por consumo do evento {@code ScoreCalculado} (tópico SNS FIFO
 * {@code score-calculado.fifo}, publicado por {@code triagem-score-service},
 * Story 3.0). Upsert idempotente: last-write-wins por {@code occurredAt},
 * desempate por {@code eventId} em ordem lexicográfica quando
 * {@code occurredAt} empata (Boundaries da spec 3.1b, I/O &amp; Edge-Case
 * Matrix).
 *
 * <p>{@link #maisRecenteQue(ScoreReplica)} é a regra pura, testável sem
 * banco. {@code infrastructure.persistence} ({@code
 * ScoreReplicaJpaRepository#upsertSeMaisRecente}) aplica a MESMA regra
 * atomicamente via SQL nativo ({@code INSERT ... ON CONFLICT ... WHERE}),
 * em vez de um read-then-write em Java -- um read-then-write reabriria a
 * corrida entre duas instâncias do consumidor lendo o mesmo Paciente antes
 * de qualquer uma escrever (mesmo raciocínio do {@code FOR UPDATE SKIP
 * LOCKED} do relay da Story 3.0, ver
 * {@code EventoOutboxRepositorio} do {@code triagem-score-service}).
 *
 * <p>Sem cálculo de Prioridade Efetiva/Aging aqui -- isso é a Story 3.1c,
 * onde a réplica finalmente tem um consumidor real via {@code GET /v1/fila}.
 */
public final class ScoreReplica {

    private final long pacienteId;
    private final int score;
    private final Instant occurredAt;
    private final UUID eventId;
    private final Instant updatedAt;
    private final Long numeroSequencialTriagem;

    /**
     * {@code numeroSequencialTriagem} (Story 3.2b1) -- numero sequencial da
     * Triagem que originou o Score, espelhado de {@code GET
     * /internal/scores} (bootstrap) ou {@code payload.triagemId} (evento
     * SQS {@code ScoreCalculado}). Nullable: campo de CARGA, nunca entra em
     * {@link #maisRecenteQue(ScoreReplica)} (que continua comparando só
     * {@code occurredAt}/{@code eventId}, Boundaries da spec 3.2b1) -- sua
     * ausência (origem sem o dado, defensivo) nunca falha o construtor nem o
     * upsert.
     */
    public ScoreReplica(long pacienteId, int score, Instant occurredAt, UUID eventId, Instant updatedAt,
                         Long numeroSequencialTriagem) {
        if (pacienteId <= 0) {
            throw new IllegalArgumentException("pacienteId deve ser positivo: " + pacienteId);
        }
        // Mesma faixa de Score.valor do triagem-score-service (0..100,
        // algoritmo v1) -- a réplica nunca inventa um valor fora do domínio
        // que a originou.
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score deve estar entre 0 e 100: " + score);
        }
        this.pacienteId = pacienteId;
        this.score = score;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.numeroSequencialTriagem = numeroSequencialTriagem;
    }

    /**
     * {@code true} quando esta versão deve substituir {@code atual} na
     * réplica: {@code occurredAt} estritamente mais recente, ou empatado e
     * {@code eventId} lexicograficamente maior (desempate determinístico,
     * Boundaries da spec 3.1b). Comparação por {@code eventId.toString()}
     * (não {@link UUID#compareTo(UUID)}, que compara os bits mais/menos
     * significativos como {@code long} assinado e NÃO corresponde à ordem
     * lexicográfica da forma textual em todos os casos) -- consistente com
     * a comparação nativa de {@code UUID} do Postgres (bytes brutos,
     * equivalente à ordem da forma textual canônica), usada pelo adapter.
     *
     * <p>{@code atual == null} -- réplica ainda não existe para este
     * Paciente -- sempre substitui (insere). O mesmo {@code eventId}
     * entregue duas vezes (redelivery, I/O Matrix) nunca é "mais recente"
     * que ele mesmo -- retorna {@code false}, a réplica não muda
     * (idempotência: não duplica, não retrocede).
     */
    public boolean maisRecenteQue(ScoreReplica atual) {
        if (atual == null) {
            return true;
        }
        if (this.pacienteId != atual.pacienteId) {
            throw new IllegalArgumentException(
                    "comparacao entre replicas de Pacientes diferentes: " + this.pacienteId + " x " + atual.pacienteId);
        }
        int comparacaoOccurredAt = this.occurredAt.compareTo(atual.occurredAt);
        if (comparacaoOccurredAt != 0) {
            return comparacaoOccurredAt > 0;
        }
        return this.eventId.toString().compareTo(atual.eventId.toString()) > 0;
    }

    public long getPacienteId() {
        return pacienteId;
    }

    public int getScore() {
        return score;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getNumeroSequencialTriagem() {
        return numeroSequencialTriagem;
    }
}
