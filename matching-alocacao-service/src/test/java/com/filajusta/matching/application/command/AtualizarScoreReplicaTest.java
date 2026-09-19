package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.ScoreReplica;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Cobre {@link AtualizarScoreReplica}: constrói o {@link ScoreReplica}
 * candidato com {@code updatedAt} vindo do {@link Clock} injetado e delega
 * ao port {@link ScoreReplicaRepositorio} -- a decisão de last-write-wins em
 * si é responsabilidade do adapter (SQL nativo), coberta em
 * {@code ScoreReplicaTest} (regra pura) e no teste de integração do
 * adapter.
 */
class AtualizarScoreReplicaTest {

    private static final Instant AGORA = Instant.parse("2026-09-08T15:00:00Z");

    private final ScoreReplicaRepositorio repositorio = mock(ScoreReplicaRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final AtualizarScoreReplica useCase = new AtualizarScoreReplica(repositorio, clock);

    @Test
    void constroiAReplicaComOsCamposRecebidosEUpdatedAtDoRelogioEDelegaAoRepositorio() {
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
        UUID eventId = UUID.randomUUID();

        useCase.atualizar(42L, 77, occurredAt, eventId, 7L);

        ArgumentCaptor<ScoreReplica> captor = ArgumentCaptor.forClass(ScoreReplica.class);
        verify(repositorio).upsertSeMaisRecente(captor.capture());
        ScoreReplica replica = captor.getValue();

        assertThat(replica.getPacienteId()).isEqualTo(42L);
        assertThat(replica.getScore()).isEqualTo(77);
        assertThat(replica.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(replica.getEventId()).isEqualTo(eventId);
        assertThat(replica.getUpdatedAt()).isEqualTo(AGORA);
        assertThat(replica.getNumeroSequencialTriagem()).isEqualTo(7L);
    }

    @Test
    void numeroSequencialTriagemNuloConstroiAReplicaComOCampoNulo() {
        // I/O Matrix da spec 3.2b1: ausencia do dado (bootstrap defensivo ou
        // SQS sem triagemId) grava null, nunca falha.
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
        UUID eventId = UUID.randomUUID();

        useCase.atualizar(42L, 77, occurredAt, eventId, null);

        ArgumentCaptor<ScoreReplica> captor = ArgumentCaptor.forClass(ScoreReplica.class);
        verify(repositorio).upsertSeMaisRecente(captor.capture());
        assertThat(captor.getValue().getNumeroSequencialTriagem()).isNull();
    }
}
