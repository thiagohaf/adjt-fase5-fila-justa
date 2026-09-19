package com.filajusta.matching.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha da tabela {@code liberacao_agendada} (Story 3-4a1): registra que o
 * Recurso de uma {@link Alocacao} confirmada precisa voltar ao pool depois
 * de {@code delaySegundos}, molde de {@link EventoOutbox} (mesmo par
 * "criado, ainda não processado" -- aqui {@code enviadoEm} em vez de
 * {@code publicadoEm}).
 *
 * <p>{@code alocacaoId} é a própria PK (não uma identidade sintética
 * separada, ao contrário de {@link EventoOutbox#getId()}) -- uma
 * {@link Alocacao} confirmada agenda no máximo 1 liberação, gravada na
 * mesma transação de {@code ConfirmarAlocacao} (Boundaries da spec 3-4a1).
 *
 * <p>{@code correlationId} é propagado explicitamente pelo chamador (o
 * mesmo já efetivo em {@code ConfirmarAlocacao}) -- não é recuperável
 * depois só pela tabela {@code alocacao}, que não tem essa coluna
 * (Boundaries da spec 3-4a1).
 *
 * <p>Story 3-4a1: nenhum relay ou consumidor real lê esta tabela ainda --
 * {@link #getEnviadoEm()} começa sempre {@code null} e só ganha valor a
 * partir da Story 3-4a2 (relay de publicação). Não é bug desta story linhas
 * pendentes se acumularem sem processamento (I/O Matrix da spec).
 */
public final class LiberacaoAgendada {

    private final UUID alocacaoId;
    private final UUID recursoId;
    private final String correlationId;
    private final int delaySegundos;
    private final Instant criadoEm;
    private final Instant enviadoEm;

    public LiberacaoAgendada(UUID alocacaoId,
                              UUID recursoId,
                              String correlationId,
                              int delaySegundos,
                              Instant criadoEm,
                              Instant enviadoEm) {
        this.alocacaoId = Objects.requireNonNull(alocacaoId, "alocacaoId");
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId nao pode ser em branco");
        }
        if (delaySegundos <= 0) {
            throw new IllegalArgumentException("delaySegundos deve ser positivo: " + delaySegundos);
        }
        this.delaySegundos = delaySegundos;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.enviadoEm = enviadoEm;
    }

    public UUID getAlocacaoId() {
        return alocacaoId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getDelaySegundos() {
        return delaySegundos;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }
}
