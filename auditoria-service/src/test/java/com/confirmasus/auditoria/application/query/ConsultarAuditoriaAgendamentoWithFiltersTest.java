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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para ConsultarAuditoriaAgendamento com filtros (Story 4.3).
 *
 * <p>Cobre:
 * <ul>
 *   <li>Filtro por data range (startDate/endDate)
 *   <li>Filtro por tipo de decisão (tipoDecisao)
 *   <li>Paginação (limit/offset)
 *   <li>Casos extremos (resultado vazio, agendamentoId inválido)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultarAuditoriaAgendamento com Filtros (Story 4.3)")
class ConsultarAuditoriaAgendamentoWithFiltersTest {

    @Mock
    private DecisaoAuditoriaRepositorio repositorio;

    private ConsultarAuditoriaAgendamento useCase;

    @BeforeEach
    void setup() {
        useCase = new ConsultarAuditoriaAgendamento(repositorio);
    }

    @Test
    @DisplayName("FILTRO_DATA_RANGE_AGENDAMENTO: retorna decisões no range de data")
    void filtroDataRangeAgendamento() {
        // Arrange
        Long agendamentoId = 1L;
        Instant startDate = Instant.parse("2026-09-15T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-21T23:59:59Z");
        int limit = 50;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.NOTIFICACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );
        var item3 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.LIBERACAO, "Vagas abertas",
                Instant.parse("2026-09-21T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2, item3), 3L);
        when(repositorio.findByAgendamentoIdWithFilters(
                eq(agendamentoId), eq(startDate), eq(endDate), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                agendamentoId, startDate, endDate, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(3L, resultado_teste.total());
        assertEquals(3, resultado_teste.items().size());
        // Sem restrição de tipo
        assertTrue(resultado_teste.items().stream()
                .allMatch(d -> !d.getTimestamp().isBefore(startDate) && !d.getTimestamp().isAfter(endDate))
        );
    }

    @Test
    @DisplayName("PAGINACAO_LIMIT: retorna exatamente limit registros")
    void paginacaoLimit() {
        // Arrange
        Long agendamentoId = 1L;
        int limit = 5;
        int offset = 0;

        var items = List.of(
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.NOTIFICACAO, null,
                        Instant.parse("2026-09-15T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-16T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.LIBERACAO, "Liberado",
                        Instant.parse("2026-09-17T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.RECUSA, "Recusado",
                        Instant.parse("2026-09-18T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.NAO_CONFIRMADO, "Expirado",
                        Instant.parse("2026-09-19T10:00:00Z"), Instant.now())
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(items, 10L);
        when(repositorio.findByAgendamentoIdWithFilters(
                eq(agendamentoId), isNull(), isNull(), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                agendamentoId, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(10L, resultado_teste.total()); // Total é 10
        assertEquals(5, resultado_teste.items().size()); // Mas retorna apenas 5 (limit)
    }

    @Test
    @DisplayName("RESULTADO_VAZIO_COM_OFFSET: offset além do total retorna vazio")
    void resultadoVazioComOffset() {
        // Arrange
        Long agendamentoId = 1L;
        int limit = 50;
        int offset = 100; // offset maior que total

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado = new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 10L);
        when(repositorio.findByAgendamentoIdWithFilters(
                eq(agendamentoId), isNull(), isNull(), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                agendamentoId, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(10L, resultado_teste.total()); // Total é 10
        assertTrue(resultado_teste.items().isEmpty()); // Mas sem items neste offset
    }

    @Test
    @DisplayName("AGENDAMENTO_ID_INVALIDO: retorna resultado vazio")
    void agendamentoIdInvalido() {
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
    @DisplayName("TODOS_9_TIPOS_DECISAO: testa cada tipo isoladamente para agendamento")
    void todosTiposDecisaoAgendamento(TipoDecisao tipo) {
        // Arrange
        Long agendamentoId = 1L;
        int limit = 50;
        int offset = 0;

        var item = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, tipo, "Motivo teste",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item), 1L);
        when(repositorio.findByAgendamentoIdWithFilters(
                eq(agendamentoId), isNull(), isNull(), eq(tipo), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                agendamentoId, null, null, tipo, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(1L, resultado_teste.total());
        assertEquals(1, resultado_teste.items().size());
        assertEquals(tipo, resultado_teste.items().get(0).getTipoDecisao(),
                "Tipo " + tipo + " deve ser retornado quando filtrado para agendamento");
    }

    @Test
    @DisplayName("ORDENACAO_ASC_AGENDAMENTO: verificar que resultados estão em ordem crescente")
    void ordenacaoAscendenteAgendamento() {
        // Arrange
        Long agendamentoId = 1L;
        int limit = 50;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.NOTIFICACAO, null,
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item3 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-21T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2, item3), 3L);
        when(repositorio.findByAgendamentoIdWithFilters(
                eq(agendamentoId), isNull(), isNull(), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                agendamentoId, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(3, resultado_teste.items().size());
        for (int i = 0; i < resultado_teste.items().size() - 1; i++) {
            assertTrue(
                    resultado_teste.items().get(i).getTimestamp()
                            .isBefore(resultado_teste.items().get(i + 1).getTimestamp())
                            || resultado_teste.items().get(i).getTimestamp()
                                    .equals(resultado_teste.items().get(i + 1).getTimestamp()),
                    "Timestamps devem estar em ordem ASC para agendamento"
            );
        }
    }

    @Test
    @DisplayName("FILTRO_TIPO_E_DATA: combina tipo de decisão com date range")
    void filtroTipoEData() {
        // Arrange
        Long agendamentoId = 1L;
        Instant startDate = Instant.parse("2026-09-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-30T23:59:59Z");
        TipoDecisao tipo = TipoDecisao.RECUSA;
        int limit = 50;
        int offset = 0;

        var recusa1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.RECUSA, "Motivo 1",
                Instant.parse("2026-09-10T10:00:00Z"), Instant.now()
        );
        var recusa2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, 1L, TipoDecisao.RECUSA, "Motivo 2",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(recusa1, recusa2), 2L);
        when(repositorio.findByAgendamentoIdWithFilters(
                eq(agendamentoId), eq(startDate), eq(endDate), eq(tipo), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltros(
                agendamentoId, startDate, endDate, tipo, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado_teste.total());
        assertEquals(2, resultado_teste.items().size());
        // Ambos devem satisfazer AND logic: tipo E data range
        assertTrue(resultado_teste.items().stream()
                .allMatch(d -> d.getTipoDecisao() == TipoDecisao.RECUSA
                        && !d.getTimestamp().isBefore(startDate)
                        && !d.getTimestamp().isAfter(endDate)
                )
        );
    }
}
