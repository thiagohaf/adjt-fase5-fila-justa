package com.confirmasus.auditoria.application.query;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ConsultarAuditoriaAgendamento")
class ConsultarAuditoriaAgendamentoTest {

    @Mock
    private DecisaoAuditoriaRepositorio repositorio;

    private ConsultarAuditoriaAgendamento consultar;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consultar = new ConsultarAuditoriaAgendamento(repositorio);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("deve retornar histórico de agendamento ordenado por timestamp crescente")
    void testConsultarComHistorico() {
        // Arrange
        Long agendamentoId = 456L;
        Instant agora = Instant.now();

        DecisaoAuditoria decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                agendamentoId,
                123L,
                TipoDecisao.NOTIFICACAO,
                null,
                agora,
                agora
        );

        DecisaoAuditoria decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                agendamentoId,
                123L,
                TipoDecisao.RECUSA,
                "Paciente recusou",
                agora.plusSeconds(15),
                agora.plusSeconds(15)
        );

        List<DecisaoAuditoria> esperado = List.of(decisao1, decisao2);
        when(repositorio.findByAgendamentoIdOrderByTimestamp(agendamentoId)).thenReturn(esperado);

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(agendamentoId);

        // Assert
        assertEquals(2, resultado.size());
        assertEquals(decisao1.getEventId(), resultado.get(0).getEventId());
        assertEquals(decisao2.getEventId(), resultado.get(1).getEventId());
        verify(repositorio, times(1)).findByAgendamentoIdOrderByTimestamp(agendamentoId);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando agendamento não tem histórico")
    void testConsultarSemHistorico() {
        // Arrange
        Long agendamentoId = 999L;
        when(repositorio.findByAgendamentoIdOrderByTimestamp(agendamentoId)).thenReturn(List.of());

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(agendamentoId);

        // Assert
        assertTrue(resultado.isEmpty());
        verify(repositorio, times(1)).findByAgendamentoIdOrderByTimestamp(agendamentoId);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando agendamentoId é null")
    void testConsultarComAgendamentoIdNull() {
        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(null);

        // Assert
        assertTrue(resultado.isEmpty());
        verify(repositorio, never()).findByAgendamentoIdOrderByTimestamp(any());
    }

    @Test
    @DisplayName("deve retornar lista vazia quando agendamentoId é <= 0")
    void testConsultarComAgendamentoIdInvalido() {
        // Act & Assert
        assertTrue(consultar.consultar(0L).isEmpty());
        assertTrue(consultar.consultar(-5L).isEmpty());

        verify(repositorio, never()).findByAgendamentoIdOrderByTimestamp(any());
    }

    @Test
    @DisplayName("deve preservar ordem crescente de timestamps para múltiplos eventos")
    void testOrdenacaoTimestampMultiplosEventos() {
        // Arrange
        Long agendamentoId = 222L;
        Instant t1 = Instant.parse("2026-09-21T09:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T09:30:00Z");
        Instant t3 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t4 = Instant.parse("2026-09-21T10:30:00Z");

        List<DecisaoAuditoria> esperado = List.of(
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 111L, TipoDecisao.NOTIFICACAO, null, t1, t1),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 111L, TipoDecisao.CONFIRMACAO, null, t2, t2),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 111L, TipoDecisao.LIBERACAO, "Vagas encerradas", t3, t3),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 111L, TipoDecisao.SUGESTAO_GERADA, null, t4, t4)
        );
        when(repositorio.findByAgendamentoIdOrderByTimestamp(agendamentoId)).thenReturn(esperado);

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(agendamentoId);

        // Assert
        assertEquals(4, resultado.size());
        assertEquals(t1, resultado.get(0).getTimestamp());
        assertEquals(t2, resultado.get(1).getTimestamp());
        assertEquals(t3, resultado.get(2).getTimestamp());
        assertEquals(t4, resultado.get(3).getTimestamp());
    }

    @Test
    @DisplayName("deve propagar X-Correlation-Id ao MDC quando presente")
    void testPropagaCorrelationIdAoMDC() {
        // Arrange
        Long agendamentoId = 456L;
        String correlationId = "trace-67890";
        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                agendamentoId,
                123L,
                TipoDecisao.RECUSA,
                "Motivo teste",
                Instant.now(),
                Instant.now()
        );
        when(repositorio.findByAgendamentoIdOrderByTimestamp(agendamentoId)).thenReturn(List.of(decisao));

        // Act
        consultar.consultar(agendamentoId, Optional.of(correlationId));

        // Assert - MDC deve ter sido limpo após o processamento (finally block)
        assertNull(MDC.get("X-Correlation-Id"));
        verify(repositorio, times(1)).findByAgendamentoIdOrderByTimestamp(agendamentoId);
    }

    @Test
    @DisplayName("deve remover X-Correlation-Id do MDC quando não fornecido")
    void testRemoveCorrelationIdQuandoNaoFornecido() {
        // Arrange
        Long agendamentoId = 456L;
        MDC.put("X-Correlation-Id", "old-value");
        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                agendamentoId,
                123L,
                TipoDecisao.RECUSA,
                "Motivo teste",
                Instant.now(),
                Instant.now()
        );
        when(repositorio.findByAgendamentoIdOrderByTimestamp(agendamentoId)).thenReturn(List.of(decisao));

        // Act
        consultar.consultar(agendamentoId, Optional.empty());

        // Assert - MDC deve ter sido limpo
        assertNull(MDC.get("X-Correlation-Id"));
        verify(repositorio, times(1)).findByAgendamentoIdOrderByTimestamp(agendamentoId);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando repositório retorna null")
    void testConsultarComRepositorioRetornandoNull() {
        // Arrange
        Long agendamentoId = 456L;
        when(repositorio.findByAgendamentoIdOrderByTimestamp(agendamentoId)).thenReturn(null);

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(agendamentoId, Optional.empty());

        // Assert
        assertTrue(resultado.isEmpty());
        verify(repositorio, times(1)).findByAgendamentoIdOrderByTimestamp(agendamentoId);
    }
}
