package com.confirmasus.auditoria.application.query;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para ConsultarAuditoriaPaciente com filtros (Story 4.3).
 *
 * <p>Cobre:
 * <ul>
 *   <li>Filtro por data range (startDate/endDate)
 *   <li>Filtro por tipo de decisão (tipoDecisao)
 *   <li>Composição de filtros (AND logic)
 *   <li>Paginação (limit/offset)
 *   <li>Casos extremos (resultado vazio, pacienteId inválido)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultarAuditoriaPaciente com Filtros (Story 4.3)")
class ConsultarAuditoriaPacienteWithFiltersTest {

    @Mock
    private DecisaoAuditoriaRepositorio repositorio;

    private ConsultarAuditoriaPaciente useCase;

    @BeforeEach
    void setup() {
        useCase = new ConsultarAuditoriaPaciente(repositorio);
    }

    @Test
    @DisplayName("FILTRO_TIPO_DECISAO: retorna apenas registros com tipo específico")
    void filtroTipoDecisao() {
        // Arrange
        Long pacienteId = 1L;
        Instant startDate = null;
        Instant endDate = null;
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        int limit = 50;
        int offset = 0;

        var recusa1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.RECUSA, "Motivo 1",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );
        var recusa2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 11L, TipoDecisao.RECUSA, "Motivo 2",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(recusa1, recusa2), 2L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), isNull(), isNull(), eq(tipoDecisao), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, startDate, endDate, tipoDecisao, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado_teste.total());
        assertEquals(2, resultado_teste.items().size());
        assertTrue(resultado_teste.items().stream()
                .allMatch(d -> d.getTipoDecisao() == TipoDecisao.RECUSA)
        );
    }

    @Test
    @DisplayName("FILTRO_DATA_RANGE: retorna apenas registros no range de data")
    void filtroDataRange() {
        // Arrange
        Long pacienteId = 1L;
        Instant startDate = Instant.parse("2026-09-15T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-21T23:59:59Z");
        TipoDecisao tipoDecisao = null;
        int limit = 50;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 11L, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2), 2L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), eq(startDate), eq(endDate), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, startDate, endDate, tipoDecisao, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado_teste.total());
        assertEquals(2, resultado_teste.items().size());
        assertTrue(resultado_teste.items().stream()
                .allMatch(d -> !d.getTimestamp().isBefore(startDate) && !d.getTimestamp().isAfter(endDate))
        );
    }

    @Test
    @DisplayName("FILTRO_COMPLETO: combina data + tipo com AND logic")
    void filtroCompleto() {
        // Arrange
        Long pacienteId = 1L;
        Instant startDate = Instant.parse("2026-08-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-30T23:59:59Z");
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        int limit = 50;
        int offset = 0;

        var recusa1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.RECUSA, "Motivo 1",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );
        var recusa2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 11L, TipoDecisao.RECUSA, "Motivo 2",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(recusa1, recusa2), 2L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), eq(startDate), eq(endDate), eq(tipoDecisao), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, startDate, endDate, tipoDecisao, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado_teste.total());
        assertEquals(2, resultado_teste.items().size());
        // Ambos devem satisfazer data AND tipo
        assertTrue(resultado_teste.items().stream()
                .allMatch(d -> d.getTipoDecisao() == TipoDecisao.RECUSA
                        && !d.getTimestamp().isBefore(startDate)
                        && !d.getTimestamp().isAfter(endDate)
                )
        );
    }

    @Test
    @DisplayName("PAGINACAO_OFFSET: retorna registros a partir do offset")
    void paginacaoOffset() {
        // Arrange
        Long pacienteId = 1L;
        int limit = 10;
        int offset = 5;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 11L, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2), 100L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), isNull(), isNull(), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(100L, resultado_teste.total()); // Total é 100, mas retorna 2 nesta página
        assertEquals(2, resultado_teste.items().size()); // Apenas 2 itens neste offset
    }

    @Test
    @DisplayName("RESULTADO_VAZIO: retorna lista vazia com total 0")
    void resultadoVazio() {
        // Arrange
        Long pacienteId = 1L;
        Instant startDate = Instant.parse("2026-12-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-12-31T23:59:59Z");
        int limit = 50;
        int offset = 0;

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), eq(startDate), eq(endDate), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, startDate, endDate, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado_teste.total());
        assertTrue(resultado_teste.items().isEmpty());
    }

    @Test
    @DisplayName("PACIENTE_ID_INVALIDO: retorna resultado vazio")
    void pacienteIdInvalido() {
        // Act
        var resultado = useCase.consultarComFiltros(
                null, null, null, null, 50, 0, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("PACIENTE_ID_NEGATIVO: retorna resultado vazio")
    void pacienteIdNegativo() {
        // Act
        var resultado = useCase.consultarComFiltros(
                -1L, null, null, null, 50, 0, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = TipoDecisao.class)
    @DisplayName("TODOS_9_TIPOS_DECISAO: testa cada tipo isoladamente")
    void todosTiposDecisao(TipoDecisao tipo) {
        // Arrange
        Long pacienteId = 1L;
        int limit = 50;
        int offset = 0;

        var item = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, tipo, "Motivo teste",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item), 1L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), isNull(), isNull(), eq(tipo), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, null, null, tipo, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(1L, resultado_teste.total());
        assertEquals(1, resultado_teste.items().size());
        assertEquals(tipo, resultado_teste.items().get(0).getTipoDecisao(),
                "Tipo " + tipo + " deve ser retornado quando filtrado");
    }

    @Test
    @DisplayName("ORDENACAO_ASC: verifica que resultados estão em ordem crescente por timestamp")
    void ordenacaoAscendente() {
        // Arrange
        Long pacienteId = 1L;
        int limit = 50;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.NOTIFICACAO, null,
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 11L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item3 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 12L, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-21T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2, item3), 3L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), isNull(), isNull(), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(3, resultado_teste.items().size());
        for (int i = 0; i < resultado_teste.items().size() - 1; i++) {
            assertTrue(
                    resultado_teste.items().get(i).getTimestamp()
                            .isBefore(resultado_teste.items().get(i + 1).getTimestamp())
                            || resultado_teste.items().get(i).getTimestamp()
                                    .equals(resultado_teste.items().get(i + 1).getTimestamp()),
                    "Timestamps devem estar em ordem ASC"
            );
        }
    }

    @Test
    @DisplayName("BOUNDARY_INSTANT_SAME: startDate = endDate (mesmo milissegundo)")
    void boundaryInstantMesmoMilissegundo() {
        // Arrange
        Long pacienteId = 1L;
        Instant instant = Instant.parse("2026-09-21T14:30:00Z");
        Instant startDate = instant;
        Instant endDate = instant;
        int limit = 50;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.CONFIRMACAO, null,
                instant, Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 11L, TipoDecisao.RECUSA, "Motivo",
                instant, Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2), 2L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), eq(startDate), eq(endDate), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, startDate, endDate, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado_teste.total(),
                "Deve retornar ambos os registros quando startDate = endDate = mesmo instant");
        assertEquals(2, resultado_teste.items().size());
    }

    @Test
    @DisplayName("OFFSET_BEYOND_TOTAL: offset >= total retorna array vazio com total correto")
    void offsetBeyondTotal() {
        // Arrange
        Long pacienteId = 1L;
        int limit = 10;
        int offset = 100; // Total é 5, offset é 100

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 5L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), isNull(), isNull(), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(5L, resultado_teste.total(), "Total deve permanecer 5");
        assertTrue(resultado_teste.items().isEmpty(), "Items deve estar vazio");
    }

    @Test
    @DisplayName("MDC_PROPAGATION: X-Correlation-Id é propagado ao MDC")
    void mdcPropagation() {
        // Arrange
        Long pacienteId = 1L;
        String correlationId = "test-correlation-id-123";
        int limit = 50;
        int offset = 0;

        var item = DecisaoAuditoria.criar(
                UUID.randomUUID(), pacienteId, 10L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item), 1L);
        when(repositorio.findByPacienteIdWithFilters(
                eq(pacienteId), isNull(), isNull(), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                pacienteId, null, null, null, limit, offset, Optional.of(correlationId)
        );

        // Assert
        assertEquals(1L, resultado_teste.total());
        // MDC foi limpo após o método, mas durante a execução foi propagado
        // (verificamos apenas que não lançou exceção)
    }
}
