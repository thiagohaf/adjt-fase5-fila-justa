package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.domain.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link EventoOutboxRepositorioAdapter} (Story 3-3a): sem nenhum
 * produtor real ainda nesta story (Boundaries da spec 3-3a), o unico
 * comportamento a provar isoladamente e a serializacao/desserializacao do
 * payload via Jackson (o percurso ponta a ponta, contra Postgres real,
 * fica no teste de integracao {@code RelaySnsPublisherJobIntegrationTest}).
 * Mesmo padrao de teste de adapter com {@link EventoOutboxJpaRepository}
 * mockado ja usado em {@code triagem-score-service/.../
 * ScoresAtuaisRepositorioAdapterTest.java}.
 */
class EventoOutboxRepositorioAdapterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");

    private final EventoOutboxJpaRepository jpaRepository = mock(EventoOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final EventoOutboxRepositorioAdapter adapter =
            new EventoOutboxRepositorioAdapter(jpaRepository, objectMapper, clock);

    @Test
    void salvarSerializaOPayloadComoJsonEPersisteATabelaComOsDemaisCamposIntactos() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-11T11:00:00Z");
        Map<String, Object> payload = Map.of("recursoId", "leito-42", "pacienteId", 7L);
        EventoOutbox evento = new EventoOutbox(null, eventId, "AlocacaoConfirmada", occurredAt, 1, "corr-1", payload);

        adapter.salvar(evento);

        ArgumentCaptor<EventoOutboxJpaEntity> captor = ArgumentCaptor.forClass(EventoOutboxJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        EventoOutboxJpaEntity entidade = captor.getValue();

        assertThat(entidade.getEventId()).isEqualTo(eventId);
        assertThat(entidade.getEventType()).isEqualTo("AlocacaoConfirmada");
        assertThat(entidade.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(entidade.getVersion()).isEqualTo(1);
        assertThat(entidade.getCorrelationId()).isEqualTo("corr-1");

        // O payload gravado precisa ser JSON valido que reconstroi
        // exatamente o mapa original -- prova a serializacao Jackson, nao
        // so que "algum texto" foi passado adiante.
        Map<?, ?> payloadReconstruido = objectMapper.readValue(entidade.getPayload(), Map.class);
        assertThat(payloadReconstruido).isEqualTo(Map.of("recursoId", "leito-42", "pacienteId", 7));
    }

    @Test
    void buscarNaoPublicadosDesserializaOPayloadDeVoltaParaOMapaOriginal() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-11T11:00:00Z");
        String payloadJson = "{\"recursoId\":\"leito-42\",\"pacienteId\":7}";
        EventoOutboxJpaEntity entidade = new EventoOutboxJpaEntity(
                eventId, "AlocacaoConfirmada", occurredAt, 1, "corr-1", payloadJson);
        setId(entidade, 99L);
        when(jpaRepository.buscarPendentesParaAtualizar(50)).thenReturn(List.of(entidade));

        List<EventoOutbox> pendentes = adapter.buscarNaoPublicados(50);

        assertThat(pendentes).hasSize(1);
        EventoOutbox evento = pendentes.get(0);
        assertThat(evento.getId()).isEqualTo(99L);
        assertThat(evento.getEventId()).isEqualTo(eventId);
        assertThat(evento.getEventType()).isEqualTo("AlocacaoConfirmada");
        assertThat(evento.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(evento.getVersion()).isEqualTo(1);
        assertThat(evento.getCorrelationId()).isEqualTo("corr-1");
        assertThat(evento.getPayload()).isEqualTo(Map.of("recursoId", "leito-42", "pacienteId", 7));
    }

    @Test
    void buscarNaoPublicadosRespeitaOLimiteInformadoAoRepositorioJpa() {
        when(jpaRepository.buscarPendentesParaAtualizar(5)).thenReturn(List.of());

        List<EventoOutbox> pendentes = adapter.buscarNaoPublicados(5);

        assertThat(pendentes).isEmpty();
        verify(jpaRepository).buscarPendentesParaAtualizar(5);
    }

    @Test
    void marcarComoPublicadoUsaOClockInjetadoEDelegaParaOUpdateNativo() {
        when(jpaRepository.marcarPublicado(eq(1L), any(Instant.class))).thenReturn(1);

        boolean marcado = adapter.marcarComoPublicado(1L);

        assertThat(marcado).isTrue();
        verify(jpaRepository).marcarPublicado(1L, FIXED_NOW);
    }

    @Test
    void marcarComoPublicadoRetornaFalsoQuandoOUpdateNaoAtingeNenhumaLinha() {
        // Corrida entre instancias do job (Boundaries da spec 3-3a) -- outra
        // instancia ja marcou a linha primeiro; nao e um erro.
        when(jpaRepository.marcarPublicado(eq(1L), any(Instant.class))).thenReturn(0);

        boolean marcado = adapter.marcarComoPublicado(1L);

        assertThat(marcado).isFalse();
    }

    private static void setId(EventoOutboxJpaEntity entidade, Long id) {
        try {
            var campo = EventoOutboxJpaEntity.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entidade, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
