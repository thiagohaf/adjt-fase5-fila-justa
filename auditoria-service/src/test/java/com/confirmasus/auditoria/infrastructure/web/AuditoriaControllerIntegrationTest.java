package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.application.query.ConsultarAuditoriaAgendamento;
import com.confirmasus.auditoria.application.query.ConsultarAuditoriaPaciente;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuditoriaController Unit Tests")
class AuditoriaControllerIntegrationTest {

    @Mock
    private ConsultarAuditoriaPaciente consultarAuditoriaPaciente;

    @Mock
    private ConsultarAuditoriaAgendamento consultarAuditoriaAgendamento;

    private AuditoriaController controller;
    private Instant agora;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new AuditoriaController(consultarAuditoriaPaciente, consultarAuditoriaAgendamento);
        agora = Instant.now();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("consultarPaciente com histórico deve retornar array com 3 DecisaoAuditoriaResponse")
    void testConsultarPacienteComHistorico() {
        // Arrange
        Long pacienteId = 123L;
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        UUID eventId3 = UUID.randomUUID();

        DecisaoAuditoria decisao1 = DecisaoAuditoria.criar(
                eventId1, 456L, pacienteId, TipoDecisao.NOTIFICACAO, null,
                agora, agora
        );
        DecisaoAuditoria decisao2 = DecisaoAuditoria.criar(
                eventId2, 456L, pacienteId, TipoDecisao.CONFIRMACAO, null,
                agora.plusSeconds(10), agora.plusSeconds(10)
        );
        DecisaoAuditoria decisao3 = DecisaoAuditoria.criar(
                eventId3, 456L, pacienteId, TipoDecisao.LIBERACAO, "Paciente mudou",
                agora.plusSeconds(20), agora.plusSeconds(20)
        );

        when(consultarAuditoriaPaciente.consultar(eq(pacienteId), any(Optional.class)))
                .thenReturn(List.of(decisao1, decisao2, decisao3));

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarPaciente(pacienteId, null);

        // Assert
        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventId1);
        assertThat(resultado.get(0).tipoDecisao()).isEqualTo(TipoDecisao.NOTIFICACAO);
        assertThat(resultado.get(0).pacienteId()).isEqualTo(123L);
        assertThat(resultado.get(1).eventId()).isEqualTo(eventId2);
        assertThat(resultado.get(2).eventId()).isEqualTo(eventId3);
        assertThat(resultado.get(2).motivo()).isEqualTo("Paciente mudou");
        verify(consultarAuditoriaPaciente, times(1)).consultar(eq(pacienteId), any(Optional.class));
    }

    @Test
    @DisplayName("consultarPaciente sem histórico deve retornar array vazio")
    void testConsultarPacienteSemHistorico() {
        // Arrange
        Long pacienteId = 999L;
        when(consultarAuditoriaPaciente.consultar(eq(pacienteId), any(Optional.class))).thenReturn(List.of());

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarPaciente(pacienteId, null);

        // Assert
        assertThat(resultado).isEmpty();
        verify(consultarAuditoriaPaciente, times(1)).consultar(eq(pacienteId), any(Optional.class));
    }

    @Test
    @DisplayName("consultarAgendamento com histórico deve retornar array com 2 DecisaoAuditoriaResponse")
    void testConsultarAgendamentoComHistorico() {
        // Arrange
        Long agendamentoId = 456L;
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        DecisaoAuditoria decisao1 = DecisaoAuditoria.criar(
                eventId1, agendamentoId, 123L, TipoDecisao.NOTIFICACAO, null,
                agora, agora
        );
        DecisaoAuditoria decisao2 = DecisaoAuditoria.criar(
                eventId2, agendamentoId, 123L, TipoDecisao.RECUSA, "Paciente recusou",
                agora.plusSeconds(30), agora.plusSeconds(30)
        );

        when(consultarAuditoriaAgendamento.consultar(eq(agendamentoId), any(Optional.class)))
                .thenReturn(List.of(decisao1, decisao2));

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarAgendamento(agendamentoId, null);

        // Assert
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventId1);
        assertThat(resultado.get(0).tipoDecisao()).isEqualTo(TipoDecisao.NOTIFICACAO);
        assertThat(resultado.get(0).agendamentoId()).isEqualTo(456L);
        assertThat(resultado.get(1).eventId()).isEqualTo(eventId2);
        assertThat(resultado.get(1).tipoDecisao()).isEqualTo(TipoDecisao.RECUSA);
        assertThat(resultado.get(1).motivo()).isEqualTo("Paciente recusou");
        verify(consultarAuditoriaAgendamento, times(1)).consultar(eq(agendamentoId), any(Optional.class));
    }

    @Test
    @DisplayName("consultarAgendamento sem histórico deve retornar array vazio")
    void testConsultarAgendamentoSemHistorico() {
        // Arrange
        Long agendamentoId = 999L;
        when(consultarAuditoriaAgendamento.consultar(eq(agendamentoId), any(Optional.class))).thenReturn(List.of());

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarAgendamento(agendamentoId, null);

        // Assert
        assertThat(resultado).isEmpty();
        verify(consultarAuditoriaAgendamento, times(1)).consultar(eq(agendamentoId), any(Optional.class));
    }

    @Test
    @DisplayName("Ordem de timestamp deve ser sempre crescente (mais antigo primeiro)")
    void testOrdenacaoCrescente() {
        // Arrange
        Long pacienteId = 111L;
        Instant t1 = Instant.parse("2026-09-21T08:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T09:00:00Z");
        Instant t3 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t4 = Instant.parse("2026-09-21T11:00:00Z");

        List<DecisaoAuditoria> decisoes = List.of(
                DecisaoAuditoria.criar(UUID.randomUUID(), 999L, pacienteId, TipoDecisao.NOTIFICACAO, null, t1, t1),
                DecisaoAuditoria.criar(UUID.randomUUID(), 999L, pacienteId, TipoDecisao.CONFIRMACAO, null, t2, t2),
                DecisaoAuditoria.criar(UUID.randomUUID(), 999L, pacienteId, TipoDecisao.LIBERACAO, "motivo", t3, t3),
                DecisaoAuditoria.criar(UUID.randomUUID(), 999L, pacienteId, TipoDecisao.SUGESTAO_GERADA, null, t4, t4)
        );

        when(consultarAuditoriaPaciente.consultar(eq(pacienteId), any(Optional.class))).thenReturn(decisoes);

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarPaciente(pacienteId, null);

        // Assert
        assertThat(resultado).hasSize(4);
        assertThat(resultado.get(0).tipoDecisao()).isEqualTo(TipoDecisao.NOTIFICACAO);
        assertThat(resultado.get(1).tipoDecisao()).isEqualTo(TipoDecisao.CONFIRMACAO);
        assertThat(resultado.get(2).tipoDecisao()).isEqualTo(TipoDecisao.LIBERACAO);
        assertThat(resultado.get(3).tipoDecisao()).isEqualTo(TipoDecisao.SUGESTAO_GERADA);
    }

    @Test
    @DisplayName("Campos motivo nullable devem ser renderizados corretamente")
    void testCamposMotivoNullable() {
        // Arrange
        Long agendamentoId = 222L;
        Instant t1 = Instant.now();
        Instant t2 = Instant.now().plusSeconds(10);

        List<DecisaoAuditoria> decisoes = List.of(
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 111L, TipoDecisao.NOTIFICACAO, null, t1, t1),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 111L, TipoDecisao.RECUSA, "Urgência baixa", t2, t2)
        );

        when(consultarAuditoriaAgendamento.consultar(eq(agendamentoId), any(Optional.class))).thenReturn(decisoes);

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarAgendamento(agendamentoId, null);

        // Assert
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).motivo()).isNull();
        assertThat(resultado.get(1).motivo()).isEqualTo("Urgência baixa");
    }

    @Test
    @DisplayName("consultarPaciente deve propagar X-Correlation-Id ao use case")
    void testConsultarPacienteComCorrelationId() {
        // Arrange
        Long pacienteId = 123L;
        String correlationId = "trace-abc123";
        UUID eventId = UUID.randomUUID();

        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                eventId, 456L, pacienteId, TipoDecisao.LIBERACAO, "Aprovado",
                agora, agora
        );

        when(consultarAuditoriaPaciente.consultar(pacienteId, Optional.of(correlationId)))
                .thenReturn(List.of(decisao));

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarPaciente(pacienteId, correlationId);

        // Assert
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventId);
        verify(consultarAuditoriaPaciente, times(1)).consultar(pacienteId, Optional.of(correlationId));
    }

    @Test
    @DisplayName("consultarAgendamento deve propagar X-Correlation-Id ao use case")
    void testConsultarAgendamentoComCorrelationId() {
        // Arrange
        Long agendamentoId = 456L;
        String correlationId = "trace-xyz789";
        UUID eventId = UUID.randomUUID();

        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                eventId, agendamentoId, 123L, TipoDecisao.RECUSA, "Motivo",
                agora, agora
        );

        when(consultarAuditoriaAgendamento.consultar(agendamentoId, Optional.of(correlationId)))
                .thenReturn(List.of(decisao));

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarAgendamento(agendamentoId, correlationId);

        // Assert
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).eventId()).isEqualTo(eventId);
        verify(consultarAuditoriaAgendamento, times(1)).consultar(agendamentoId, Optional.of(correlationId));
    }

    @Test
    @DisplayName("consultarPaciente sem X-Correlation-Id deve passar Optional.empty()")
    void testConsultarPacienteSemCorrelationId() {
        // Arrange
        Long pacienteId = 123L;
        when(consultarAuditoriaPaciente.consultar(pacienteId, Optional.empty()))
                .thenReturn(List.of());

        // Act
        List<DecisaoAuditoriaResponse> resultado = controller.consultarPaciente(pacienteId, null);

        // Assert
        assertThat(resultado).isEmpty();
        verify(consultarAuditoriaPaciente, times(1)).consultar(pacienteId, Optional.empty());
    }
}
