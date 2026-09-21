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

@DisplayName("ConsultarAuditoriaPaciente")
class ConsultarAuditoriaPacienteTest {

    @Mock
    private DecisaoAuditoriaRepositorio repositorio;

    private ConsultarAuditoriaPaciente consultar;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consultar = new ConsultarAuditoriaPaciente(repositorio);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("deve retornar histórico ordenado por timestamp crescente")
    void testConsultarComHistorico() {
        // Arrange
        Long pacienteId = 123L;
        Instant agora = Instant.now();

        DecisaoAuditoria decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                456L,
                pacienteId,
                TipoDecisao.NOTIFICACAO,
                null,
                agora,
                agora
        );

        DecisaoAuditoria decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                456L,
                pacienteId,
                TipoDecisao.CONFIRMACAO,
                null,
                agora.plusSeconds(10),
                agora.plusSeconds(10)
        );

        DecisaoAuditoria decisao3 = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                456L,
                pacienteId,
                TipoDecisao.LIBERACAO,
                "Paciente mudou de endereço",
                agora.plusSeconds(20),
                agora.plusSeconds(20)
        );

        List<DecisaoAuditoria> esperado = List.of(decisao1, decisao2, decisao3);
        when(repositorio.findByPacienteIdOrderByTimestamp(pacienteId)).thenReturn(esperado);

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(pacienteId);

        // Assert
        assertEquals(3, resultado.size());
        assertEquals(decisao1.getEventId(), resultado.get(0).getEventId());
        assertEquals(decisao2.getEventId(), resultado.get(1).getEventId());
        assertEquals(decisao3.getEventId(), resultado.get(2).getEventId());
        verify(repositorio, times(1)).findByPacienteIdOrderByTimestamp(pacienteId);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando paciente não tem histórico")
    void testConsultarSemHistorico() {
        // Arrange
        Long pacienteId = 999L;
        when(repositorio.findByPacienteIdOrderByTimestamp(pacienteId)).thenReturn(List.of());

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(pacienteId);

        // Assert
        assertTrue(resultado.isEmpty());
        verify(repositorio, times(1)).findByPacienteIdOrderByTimestamp(pacienteId);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando pacienteId é null")
    void testConsultarComPacienteIdNull() {
        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(null);

        // Assert
        assertTrue(resultado.isEmpty());
        verify(repositorio, never()).findByPacienteIdOrderByTimestamp(any());
    }

    @Test
    @DisplayName("deve retornar lista vazia quando pacienteId é <= 0")
    void testConsultarComPacienteIdInvalido() {
        // Act & Assert
        assertTrue(consultar.consultar(0L).isEmpty());
        assertTrue(consultar.consultar(-1L).isEmpty());

        verify(repositorio, never()).findByPacienteIdOrderByTimestamp(any());
    }

    @Test
    @DisplayName("deve preservar ordem crescente de timestamps")
    void testOrdenacaoTimestamp() {
        // Arrange
        Long pacienteId = 111L;
        Instant t1 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T11:00:00Z");
        Instant t3 = Instant.parse("2026-09-21T12:00:00Z");

        List<DecisaoAuditoria> esperado = List.of(
                DecisaoAuditoria.criar(UUID.randomUUID(), 999L, pacienteId, TipoDecisao.NOTIFICACAO, null, t1, t1),
                DecisaoAuditoria.criar(UUID.randomUUID(), 999L, pacienteId, TipoDecisao.CONFIRMACAO, null, t2, t2),
                DecisaoAuditoria.criar(UUID.randomUUID(), 999L, pacienteId, TipoDecisao.RECUSA, "Motivo", t3, t3)
        );
        when(repositorio.findByPacienteIdOrderByTimestamp(pacienteId)).thenReturn(esperado);

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(pacienteId);

        // Assert
        assertEquals(t1, resultado.get(0).getTimestamp());
        assertEquals(t2, resultado.get(1).getTimestamp());
        assertEquals(t3, resultado.get(2).getTimestamp());
    }

    @Test
    @DisplayName("deve propagar X-Correlation-Id ao MDC quando presente")
    void testPropagaCorrelationIdAoMDC() {
        // Arrange
        Long pacienteId = 123L;
        String correlationId = "trace-12345";
        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                456L,
                pacienteId,
                TipoDecisao.NOTIFICACAO,
                null,
                Instant.now(),
                Instant.now()
        );
        when(repositorio.findByPacienteIdOrderByTimestamp(pacienteId)).thenReturn(List.of(decisao));

        // Act
        consultar.consultar(pacienteId, Optional.of(correlationId));

        // Assert - MDC deve ter sido limpo após o processamento (finally block)
        assertNull(MDC.get("X-Correlation-Id"));
        verify(repositorio, times(1)).findByPacienteIdOrderByTimestamp(pacienteId);
    }

    @Test
    @DisplayName("deve remover X-Correlation-Id do MDC quando não fornecido")
    void testRemoveCorrelationIdQuandoNaoFornecido() {
        // Arrange
        Long pacienteId = 123L;
        MDC.put("X-Correlation-Id", "old-value");
        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(),
                456L,
                pacienteId,
                TipoDecisao.NOTIFICACAO,
                null,
                Instant.now(),
                Instant.now()
        );
        when(repositorio.findByPacienteIdOrderByTimestamp(pacienteId)).thenReturn(List.of(decisao));

        // Act
        consultar.consultar(pacienteId, Optional.empty());

        // Assert - MDC deve ter sido limpo
        assertNull(MDC.get("X-Correlation-Id"));
        verify(repositorio, times(1)).findByPacienteIdOrderByTimestamp(pacienteId);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando repositório retorna null")
    void testConsultarComRepositorioRetornandoNull() {
        // Arrange
        Long pacienteId = 123L;
        when(repositorio.findByPacienteIdOrderByTimestamp(pacienteId)).thenReturn(null);

        // Act
        List<DecisaoAuditoria> resultado = consultar.consultar(pacienteId, Optional.empty());

        // Assert
        assertTrue(resultado.isEmpty());
        verify(repositorio, times(1)).findByPacienteIdOrderByTimestamp(pacienteId);
    }
}
