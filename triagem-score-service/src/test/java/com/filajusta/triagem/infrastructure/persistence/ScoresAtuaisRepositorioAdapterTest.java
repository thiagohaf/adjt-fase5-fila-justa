package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.query.ScoreAtual;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ScoresAtuaisRepositorioAdapter}: reconstrucao de {@link
 * ScoreAtual} a partir de {@code eventos_outbox} e, principalmente, a
 * resiliencia por linha (achado do code review) -- uma linha com payload
 * malformado/incompleto e ignorada (logada), sem derrubar o restante da
 * listagem nem propagar excecao para o chamador.
 */
class ScoresAtuaisRepositorioAdapterTest {

    private static final String EVENT_TYPE = "ScoreCalculado";

    private final EventoOutboxJpaRepository jpaRepository = mock(EventoOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScoresAtuaisRepositorioAdapter adapter =
            new ScoresAtuaisRepositorioAdapter(jpaRepository, objectMapper);

    private static EventoOutboxJpaEntity evento(UUID eventId, Instant occurredAt, String payloadJson) {
        return new EventoOutboxJpaEntity(eventId, EVENT_TYPE, occurredAt, 1, "corr-1", payloadJson);
    }

    @Test
    void payloadValidoReconstroiOScoreAtualCompleto() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
        String payload = "{\"pacienteId\":10,\"triagemId\":1,\"scoreValor\":75,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[{\"fator\":\"temperatura\",\"contribuicao\":0.5}]}";
        when(jpaRepository.findByEventTypeOrderByOccurredAtAsc(EVENT_TYPE))
                .thenReturn(List.of(evento(eventId, occurredAt, payload)));

        List<ScoreAtual> resultado = adapter.listarTodos();

        assertThat(resultado).hasSize(1);
        ScoreAtual scoreAtual = resultado.get(0);
        assertThat(scoreAtual.pacienteId()).isEqualTo(10L);
        assertThat(scoreAtual.eventId()).isEqualTo(eventId);
        assertThat(scoreAtual.occurredAt()).isEqualTo(occurredAt);
        assertThat(scoreAtual.score().getValor()).isEqualTo(75);
        assertThat(scoreAtual.score().getAlgoritmoVersao()).isEqualTo("v1");
        assertThat(scoreAtual.score().getFatores()).hasSize(1);
        assertThat(scoreAtual.numeroSequencialTriagem()).isEqualTo(1L);
    }

    @Test
    void linhaSemTriagemIdNoPayloadEIgnoradaSemDerrubarORestanteDaListagem() {
        // Story 3.2a, I/O & Edge-Case Matrix: payload sem triagemId (evento
        // legado/malformado) -- linha isolada e ignorada, demais linhas
        // validas continuam presentes.
        UUID eventIdSemTriagemId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
        String payloadSemTriagemId = "{\"pacienteId\":40,\"scoreValor\":60,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";

        UUID eventIdValido = UUID.randomUUID();
        String payloadValido = "{\"pacienteId\":41,\"triagemId\":5,\"scoreValor\":65,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";

        when(jpaRepository.findByEventTypeOrderByOccurredAtAsc(EVENT_TYPE))
                .thenReturn(List.of(
                        evento(eventIdSemTriagemId, occurredAt, payloadSemTriagemId),
                        evento(eventIdValido, occurredAt, payloadValido)));

        List<ScoreAtual> resultado = adapter.listarTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventIdValido);
        assertThat(resultado.get(0).numeroSequencialTriagem()).isEqualTo(5L);
    }

    @Test
    void linhaComTriagemIdDeTipoErradoEIgnoradaSemDerrubarORestanteDaListagem() {
        // Story 3.2a, I/O & Edge-Case Matrix: "payload.triagemId ausente ou
        // nao-numerico" -- este cobre a metade "nao-numerico" (triagemId
        // presente, mas como string em vez de numero).
        UUID eventIdTipoErrado = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
        String payloadTriagemIdTipoErrado = "{\"pacienteId\":50,\"triagemId\":\"abc\",\"scoreValor\":70,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";

        UUID eventIdValido = UUID.randomUUID();
        String payloadValido = "{\"pacienteId\":51,\"triagemId\":6,\"scoreValor\":72,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";

        when(jpaRepository.findByEventTypeOrderByOccurredAtAsc(EVENT_TYPE))
                .thenReturn(List.of(
                        evento(eventIdTipoErrado, occurredAt, payloadTriagemIdTipoErrado),
                        evento(eventIdValido, occurredAt, payloadValido)));

        List<ScoreAtual> resultado = adapter.listarTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventIdValido);
        assertThat(resultado.get(0).numeroSequencialTriagem()).isEqualTo(6L);
    }

    @Test
    void linhaComCampoAusenteEIgnoradaSemDerrubarORestanteDaListagem() {
        UUID eventIdMalformado = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
        // pacienteId ausente -- payload incompleto.
        String payloadMalformado = "{\"triagemId\":1,\"scoreValor\":75,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";

        UUID eventIdValido = UUID.randomUUID();
        String payloadValido = "{\"pacienteId\":20,\"triagemId\":2,\"scoreValor\":50,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";

        when(jpaRepository.findByEventTypeOrderByOccurredAtAsc(EVENT_TYPE))
                .thenReturn(List.of(
                        evento(eventIdMalformado, occurredAt, payloadMalformado),
                        evento(eventIdValido, occurredAt, payloadValido)));

        List<ScoreAtual> resultado = adapter.listarTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventIdValido);
        assertThat(resultado.get(0).pacienteId()).isEqualTo(20L);
    }

    @Test
    void linhaComJsonInvalidoEIgnoradaSemDerrubarORestanteDaListagem() {
        UUID eventIdInvalido = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");

        UUID eventIdValido = UUID.randomUUID();
        String payloadValido = "{\"pacienteId\":30,\"triagemId\":3,\"scoreValor\":40,"
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";

        when(jpaRepository.findByEventTypeOrderByOccurredAtAsc(EVENT_TYPE))
                .thenReturn(List.of(
                        evento(eventIdInvalido, occurredAt, "{isto-nao-e-json-valido"),
                        evento(eventIdValido, occurredAt, payloadValido)));

        List<ScoreAtual> resultado = adapter.listarTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventIdValido);
    }

    @Test
    void todasAsLinhasMalformadasNaoLancaExcecaoEDevolveListaVazia() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
        // scoreValor com tipo errado (texto em vez de numero).
        String payloadMalformado = "{\"pacienteId\":10,\"scoreValor\":\"nao-e-numero\","
                + "\"algoritmoVersao\":\"v1\",\"fatores\":[]}";
        when(jpaRepository.findByEventTypeOrderByOccurredAtAsc(EVENT_TYPE))
                .thenReturn(List.of(evento(eventId, occurredAt, payloadMalformado)));

        assertThatCode(adapter::listarTodos).doesNotThrowAnyException();
        assertThat(adapter.listarTodos()).isEmpty();
    }
}
