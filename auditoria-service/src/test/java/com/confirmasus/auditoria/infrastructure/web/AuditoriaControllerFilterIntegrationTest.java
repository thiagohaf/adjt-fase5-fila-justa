package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.application.query.ConsultarAuditoriaAgendamento;
import com.confirmasus.auditoria.application.query.ConsultarAuditoriaPaciente;
import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Testes de integração do AuditoriaController com filtros (Story 4.3).
 *
 * <p>Valida:
 * <ul>
 *   <li>Validação de datas (startDate > endDate)
 *   <li>Paginação (limit, offset)
 *   <li>Composição AND de filtros
 *   <li>Compatibilidade Story 4.2 (sem filtros)
 *   <li>Tipos de resposta (simples vs. paginada)
 *   <li>Metadados de paginação (total, limit, offset)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditoriaController - Filtros e Paginação (Story 4.3)")
class AuditoriaControllerFilterIntegrationTest {

    @Mock
    private ConsultarAuditoriaPaciente consultarAuditoriaPaciente;

    @Mock
    private ConsultarAuditoriaAgendamento consultarAuditoriaAgendamento;

    private AuditoriaController controller;

    @BeforeEach
    void setup() {
        controller = new AuditoriaController(consultarAuditoriaPaciente, consultarAuditoriaAgendamento);
    }

    @Test
    @DisplayName("SEM_FILTROS: retorna lista simples (compatibilidade Story 4.2)")
    void semFiltros() {
        // Arrange
        Long pacienteId = 1L;
        var decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        when(consultarAuditoriaPaciente.consultar(eq(pacienteId), any()))
                .thenReturn(List.of(decisao));

        // Act
        Object resultado = controller.consultarPaciente(pacienteId, null, null, null, null, null, null);

        // Assert
        assertTrue(resultado instanceof List);
        @SuppressWarnings("unchecked")
        List<DecisaoAuditoriaResponse> items = (List<DecisaoAuditoriaResponse>) resultado;
        assertEquals(1, items.size());
    }

    @Test
    @DisplayName("COM_FILTRO_TIPO: retorna resposta paginada")
    void comFiltroTipo() {
        // Arrange
        Long pacienteId = 1L;
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        var decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.RECUSA, "Motivo",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> paginatedResult = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao), 1L);
        when(consultarAuditoriaPaciente.consultarComFiltros(
                eq(pacienteId), isNull(), isNull(), eq(tipoDecisao), eq(50), eq(0), any()
        )).thenReturn(paginatedResult);

        // Act
        Object resultado = controller.consultarPaciente(
                pacienteId, null, null, tipoDecisao, null, null, null
        );

        // Assert
        assertTrue(resultado instanceof AuditoriaPaginatedResponse);
        AuditoriaPaginatedResponse paginated = (AuditoriaPaginatedResponse) resultado;
        assertEquals(1L, paginated.total());
        assertEquals(1, paginated.items().size());
        assertEquals(50, paginated.limit());
        assertEquals(0, paginated.offset());
    }

    @Test
    @DisplayName("DATAS_INVALIDAS: startDate > endDate lança IllegalArgumentException")
    void datasInvalidas() {
        // Arrange
        Long pacienteId = 1L;
        Instant startDate = Instant.parse("2026-09-25T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-21T23:59:59Z");

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                controller.consultarPaciente(pacienteId, startDate, endDate, null, null, null, null)
        );
        assertEquals("Data inicial não pode ser maior que data final", ex.getMessage());
    }

    @Test
    @DisplayName("LIMIT_EXCEDE_MAXIMO: limit > 200 lança IllegalArgumentException")
    void limitExcedeMaximo() {
        // Arrange
        Long pacienteId = 1L;
        Integer limit = 300;

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                controller.consultarPaciente(pacienteId, null, null, null, limit, null, null)
        );
        assertEquals("Limit máximo é 200", ex.getMessage());
    }

    @Test
    @DisplayName("FILTRO_COMPLETO_PACIENTE: data + tipo + paginação")
    void filtroCompletoPaciente() {
        // Arrange
        Long pacienteId = 1L;
        Instant startDate = Instant.parse("2026-01-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-21T23:59:59Z");
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        Integer limit = 10;
        Integer offset = 0;

        var recusa = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.RECUSA, "Motivo",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> paginatedResult = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(recusa), 1L);
        when(consultarAuditoriaPaciente.consultarComFiltros(
                eq(pacienteId), eq(startDate), eq(endDate), eq(tipoDecisao), eq(10), eq(0), any()
        )).thenReturn(paginatedResult);

        // Act
        Object resultado = controller.consultarPaciente(
                pacienteId, startDate, endDate, tipoDecisao, limit, offset, null
        );

        // Assert
        assertTrue(resultado instanceof AuditoriaPaginatedResponse);
        AuditoriaPaginatedResponse paginated = (AuditoriaPaginatedResponse) resultado;
        assertEquals(1L, paginated.total());
        assertEquals(1, paginated.items().size());
        assertEquals(10, paginated.limit());
        assertEquals(0, paginated.offset());
    }

    @Test
    @DisplayName("FILTRO_DATA_RANGE_AGENDAMENTO: sem tipo, retorna todas as decisões")
    void filtroDataRangeAgendamento() {
        // Arrange
        Long agendamentoId = 1L;
        Instant startDate = Instant.parse("2026-09-15T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-21T23:59:59Z");

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.NOTIFICACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> paginatedResult = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2), 2L);
        when(consultarAuditoriaAgendamento.consultarComFiltros(
                eq(agendamentoId), eq(startDate), eq(endDate), isNull(), eq(50), eq(0), any()
        )).thenReturn(paginatedResult);

        // Act
        Object resultado = controller.consultarAgendamento(
                agendamentoId, startDate, endDate, null, null, null, null
        );

        // Assert
        assertTrue(resultado instanceof AuditoriaPaginatedResponse);
        AuditoriaPaginatedResponse paginated = (AuditoriaPaginatedResponse) resultado;
        assertEquals(2L, paginated.total());
        assertEquals(2, paginated.items().size());
    }

    @Test
    @DisplayName("PAGINACAO_OFFSET: retorna registros a partir do offset")
    void paginacaoOffset() {
        // Arrange
        Long pacienteId = 1L;
        Integer limit = 20;
        Integer offset = 40;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 11L, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        var paginatedResult = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2), 100L);
        when(consultarAuditoriaPaciente.consultarComFiltros(
                eq(pacienteId), isNull(), isNull(), isNull(), eq(20), eq(40), any()
        )).thenReturn(paginatedResult);

        // Act
        Object resultado = controller.consultarPaciente(
                pacienteId, null, null, null, limit, offset, null
        );

        // Assert
        assertTrue(resultado instanceof AuditoriaPaginatedResponse);
        AuditoriaPaginatedResponse paginated = (AuditoriaPaginatedResponse) resultado;
        assertEquals(100L, paginated.total());
        assertEquals(2, paginated.items().size());
        assertEquals(20, paginated.limit());
        assertEquals(40, paginated.offset());
    }

    @Test
    @DisplayName("RESULTADO_VAZIO: retorna array vazio com metadados")
    void resultadoVazio() {
        // Arrange
        Long pacienteId = 1L;
        Instant startDate = Instant.parse("2026-12-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-12-31T23:59:59Z");

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> paginatedResult = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        when(consultarAuditoriaPaciente.consultarComFiltros(
                eq(pacienteId), eq(startDate), eq(endDate), isNull(), eq(50), eq(0), any()
        )).thenReturn(paginatedResult);

        // Act
        Object resultado = controller.consultarPaciente(
                pacienteId, startDate, endDate, null, null, null, null
        );

        // Assert
        assertTrue(resultado instanceof AuditoriaPaginatedResponse);
        AuditoriaPaginatedResponse paginated = (AuditoriaPaginatedResponse) resultado;
        assertEquals(0L, paginated.total());
        assertTrue(paginated.items().isEmpty());
        assertEquals(50, paginated.limit());
        assertEquals(0, paginated.offset());
    }

    @Test
    @DisplayName("LIMIT_DEFAULT: sem limit, usa 50")
    void limitDefault() {
        // Arrange
        Long pacienteId = 1L;
        TipoDecisao tipoDecisao = TipoDecisao.LIBERACAO;

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> paginatedResult = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        when(consultarAuditoriaPaciente.consultarComFiltros(
                eq(pacienteId), isNull(), isNull(), eq(tipoDecisao), eq(50), eq(0), any()
        )).thenReturn(paginatedResult);

        // Act
        Object resultado = controller.consultarPaciente(
                pacienteId, null, null, tipoDecisao, null, null, null
        );

        // Assert
        assertTrue(resultado instanceof AuditoriaPaginatedResponse);
        AuditoriaPaginatedResponse paginated = (AuditoriaPaginatedResponse) resultado;
        assertEquals(50, paginated.limit());
    }

    @Test
    @DisplayName("OFFSET_DEFAULT: sem offset, usa 0")
    void offsetDefault() {
        // Arrange
        Long pacienteId = 1L;
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> paginatedResult = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        when(consultarAuditoriaPaciente.consultarComFiltros(
                eq(pacienteId), isNull(), isNull(), eq(tipoDecisao), eq(50), eq(0), any()
        )).thenReturn(paginatedResult);

        // Act
        Object resultado = controller.consultarPaciente(
                pacienteId, null, null, tipoDecisao, null, null, null
        );

        // Assert
        assertTrue(resultado instanceof AuditoriaPaginatedResponse);
        AuditoriaPaginatedResponse paginated = (AuditoriaPaginatedResponse) resultado;
        assertEquals(0, paginated.offset());
    }
}
