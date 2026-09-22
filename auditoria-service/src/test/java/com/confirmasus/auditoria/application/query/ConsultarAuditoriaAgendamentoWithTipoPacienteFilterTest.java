package com.confirmasus.auditoria.application.query;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.StatusAgendamento;
import com.confirmasus.auditoria.domain.TipoDecisao;
import com.confirmasus.auditoria.domain.TipoPaciente;
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
 * Testes unitários para ConsultarAuditoriaAgendamento com filtro de tipo de paciente (Story 4.4b).
 *
 * <p>Cobre:
 * <ul>
 *   <li>Filtro por tipo de paciente (tipoPaciente)
 *   <li>Composição com filtros anteriores: tipoDecisao, statusAgendamento, data range (AND logic)
 *   <li>Paginação (limit/offset)
 *   <li>Casos extremos (resultado vazio, paciente deletado/sem tipo)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultarAuditoriaAgendamento com Filtro Tipo Paciente (Story 4.4b)")
class ConsultarAuditoriaAgendamentoWithTipoPacienteFilterTest {

    @Mock
    private DecisaoAuditoriaRepositorio repositorio;

    private ConsultarAuditoriaAgendamento useCase;

    @BeforeEach
    void setup() {
        useCase = new ConsultarAuditoriaAgendamento(repositorio);
    }

    @ParameterizedTest
    @EnumSource(value = TipoPaciente.class)
    @DisplayName("FILTRO_TIPO_PACIENTE: retorna apenas registros com tipo específico")
    void filtroTipoPaciente(TipoPaciente tipo) {
        // Arrange
        Long agendamentoId = 100L;
        TipoPaciente tipoPaciente = tipo;
        int limit = 50;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.RECUSA, "Motivo 1",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.RECUSA, "Motivo 2",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2), 2L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(tipoPaciente), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, null, null, null, null, tipoPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado_teste.total());
        assertEquals(2, resultado_teste.items().size());
    }

    @Test
    @DisplayName("FILTRO_COMBINADO: tipoPaciente + tipoDecisao (AND)")
    void filtroCombinado_TipoPacienteEtipoDecisao() {
        // Arrange
        Long agendamentoId = 100L;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        int limit = 50;
        int offset = 0;

        var recusa1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.RECUSA, "Motivo",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(recusa1), 1L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), isNull(), isNull(), eq(tipoDecisao), isNull(), eq(tipoPaciente), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, null, null, tipoDecisao, null, tipoPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(1L, resultado_teste.total());
        assertEquals(1, resultado_teste.items().size());
        assertEquals(TipoDecisao.RECUSA, resultado_teste.items().get(0).getTipoDecisao());
    }

    @Test
    @DisplayName("FILTRO_COMBINADO_3WAY: tipoPaciente + tipoDecisao + statusAgendamento (AND)")
    void filtroCombinado_3Way() {
        // Arrange
        Long agendamentoId = 100L;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        StatusAgendamento statusAgendamento = StatusAgendamento.CONFIRMADO;
        int limit = 50;
        int offset = 0;

        var recusa1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.RECUSA, "Motivo",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(recusa1), 1L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), isNull(), isNull(), eq(tipoDecisao), eq(statusAgendamento), eq(tipoPaciente), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, null, null, tipoDecisao, statusAgendamento, tipoPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(1L, resultado_teste.total());
        assertEquals(1, resultado_teste.items().size());
        assertEquals(TipoDecisao.RECUSA, resultado_teste.items().get(0).getTipoDecisao());
    }

    @Test
    @DisplayName("FILTRO_COM_RANGE_DATA: tipoPaciente + data range (AND)")
    void filtroComRangeData() {
        // Arrange
        Long agendamentoId = 100L;
        Instant startDate = Instant.parse("2026-09-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-30T23:59:59Z");
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        int limit = 50;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var item2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1, item2), 2L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), eq(startDate), eq(endDate), isNull(), isNull(), eq(tipoPaciente), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, startDate, endDate, null, null, tipoPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado_teste.total());
        assertEquals(2, resultado_teste.items().size());
    }

    @Test
    @DisplayName("PAGINACAO_COM_FILTRO: tipoPaciente + limit/offset")
    void paginacaoComFiltro() {
        // Arrange
        Long agendamentoId = 100L;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        int limit = 5;
        int offset = 0;

        var item1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item1), 20L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(tipoPaciente), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, null, null, null, null, tipoPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(20L, resultado_teste.total()); // Total é 20
        assertEquals(1, resultado_teste.items().size()); // Mas retorna 1 neste offset
    }

    @Test
    @DisplayName("PACIENTE_SEM_TIPO: retorna array vazio quando paciente deletado/sem tipo")
    void pacienteSemTipo() {
        // Arrange
        Long agendamentoId = 100L;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        int limit = 50;
        int offset = 0;

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(tipoPaciente), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, null, null, null, null, tipoPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado_teste.total());
        assertTrue(resultado_teste.items().isEmpty());
    }

    @Test
    @DisplayName("RESULTADO_VAZIO: sem registros que satisfazem todos os filtros")
    void resultadoVazio() {
        // Arrange
        Long agendamentoId = 100L;
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        int limit = 50;
        int offset = 0;

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(), 0L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), isNull(), isNull(), eq(tipoDecisao), isNull(), eq(tipoPaciente), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, null, null, tipoDecisao, null, tipoPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado_teste.total());
        assertTrue(resultado_teste.items().isEmpty());
    }

    @Test
    @DisplayName("AGENDAMENTO_ID_INVALIDO: retorna resultado vazio")
    void agendamentoIdInvalido() {
        // Act
        var resultado = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                null, null, null, null, null, TipoPaciente.PRIORITARIO, 50, 0, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("AGENDAMENTO_ID_NEGATIVO: retorna resultado vazio")
    void agendamentoIdNegativo() {
        // Act
        var resultado = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                -1L, null, null, null, null, TipoPaciente.PRIORITARIO, 50, 0, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("SEM_TIPOPACIENTE: função anterior de 4.4a continua funcionando")
    void semTipoPaciente_compatibilidade() {
        // Arrange
        Long agendamentoId = 100L;
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        StatusAgendamento statusAgendamento = StatusAgendamento.CONFIRMADO;
        int limit = 50;
        int offset = 0;

        var recusa1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.RECUSA, "Motivo",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(recusa1), 1L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                eq(agendamentoId), isNull(), isNull(), eq(tipoDecisao), eq(statusAgendamento), isNull(), eq(limit), eq(offset)
        )).thenReturn(resultado);

        // Act
        var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                agendamentoId, null, null, tipoDecisao, statusAgendamento, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(1L, resultado_teste.total());
        assertEquals(1, resultado_teste.items().size());
    }

    @Test
    @DisplayName("TODOS_TIPOS_PACIENTE: parametrizado para ambos valores")
    void todosTiposPaciente() {
        // Este teste é coberto pelo @ParameterizedTest acima, mas deixamos explícito
        for (TipoPaciente tipo : TipoPaciente.values()) {
            // Arrange
            Long agendamentoId = 100L;
            int limit = 50;
            int offset = 0;

            var item = DecisaoAuditoria.criar(
                    UUID.randomUUID(), 1L, agendamentoId, TipoDecisao.CONFIRMACAO, null,
                    Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
            );

            DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> resultado =
                    new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(item), 1L);
            when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamentoAndTipoPaciente(
                    eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(tipo), eq(limit), eq(offset)
            )).thenReturn(resultado);

            // Act
            var resultado_teste = useCase.consultarComFiltrosEStatusAgendamentoETipoPaciente(
                    agendamentoId, null, null, null, null, tipo, limit, offset, Optional.empty()
            );

            // Assert
            assertEquals(1L, resultado_teste.total(), "Tipo " + tipo + " deve ser filtrado corretamente");
            assertEquals(1, resultado_teste.items().size());
        }
    }
}
