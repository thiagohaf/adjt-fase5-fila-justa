package com.confirmasus.auditoria.application.query;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.StatusAgendamento;
import com.confirmasus.auditoria.domain.TipoDecisao;
import com.confirmasus.auditoria.domain.TipoPaciente;
import com.confirmasus.auditoria.infrastructure.client.PacienteClient;
import com.confirmasus.auditoria.infrastructure.client.PacienteClient.PacienteDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultarAuditoriaAgendamento com Filtros de Paciente (Story 4.4b)")
class ConsultarAuditoriaAgendamentoComFiltrosPacienteTest {

    @Mock
    private DecisaoAuditoriaRepositorio repositorio;

    @Mock
    private PacienteClient pacienteClient;

    private ConsultarAuditoriaAgendamento useCase;

    private static final Long AGENDAMENTO_ID = 1L;
    private static final Long PACIENTE_ID_1 = 10L;
    private static final Long PACIENTE_ID_2 = 11L;
    private static final String CPF_JOAO = "12345678901";
    private static final String CPF_MARIA = "98765432109";
    private static final String NOME_JOAO = "João Silva";
    private static final String NOME_MARIA = "Maria Santos";

    @BeforeEach
    void setup() {
        useCase = new ConsultarAuditoriaAgendamento(repositorio, pacienteClient);
    }

    @Test
    @DisplayName("FILTRO_TIPO_PACIENTE_AGENDAMENTO: retorna registros com tipo específico")
    void filtroTipoPacienteAgendamento() {
        // Arrange
        Long agendamentoId = AGENDAMENTO_ID;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_2, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "PRIORITARIO");
        when(pacienteClient.obterPaciente(PACIENTE_ID_1)).thenReturn(Optional.of(paciente));
        when(pacienteClient.obterPaciente(PACIENTE_ID_2)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                agendamentoId, null, null, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("FILTRO_NOME_PACIENTE_AGENDAMENTO: retorna registros com nome contendo string case-insensitive")
    void filtroNomePacienteAgendamentoLikeInsensitive() {
        // Arrange
        Long agendamentoId = AGENDAMENTO_ID;
        String nomePaciente = "joão";
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_2, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(PACIENTE_ID_1)).thenReturn(Optional.of(paciente));
        when(pacienteClient.obterPaciente(PACIENTE_ID_2)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                agendamentoId, null, null, null, null, null, nomePaciente, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("FILTRO_CPF_PACIENTE_AGENDAMENTO: retorna registros com CPF exato")
    void filtroCpfPacienteAgendamento() {
        // Arrange
        Long agendamentoId = AGENDAMENTO_ID;
        String cpfPaciente = CPF_JOAO;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_2, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, cpfPaciente, "REGULAR");
        when(pacienteClient.obterPaciente(PACIENTE_ID_1)).thenReturn(Optional.of(paciente));
        when(pacienteClient.obterPaciente(PACIENTE_ID_2)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                agendamentoId, null, null, null, null, null, null, cpfPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("FILTRO_TRIPLO_AGENDAMENTO: combina tipoPaciente AND nomePaciente AND cpfPaciente")
    void filtroTriploAgendamento() {
        // Arrange
        Long agendamentoId = AGENDAMENTO_ID;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        String nomePaciente = "joão";
        String cpfPaciente = CPF_JOAO;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_2, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, cpfPaciente, "PRIORITARIO");
        when(pacienteClient.obterPaciente(PACIENTE_ID_1)).thenReturn(Optional.of(paciente));
        when(pacienteClient.obterPaciente(PACIENTE_ID_2)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                agendamentoId, null, null, null, null, tipoPaciente, nomePaciente, cpfPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("PACIENTE_NAO_ENCONTRADO_AGENDAMENTO: PacienteClient retorna empty")
    void pacienteNaoEncontradoAgendamento() {
        // Arrange
        Long agendamentoId = AGENDAMENTO_ID;
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1), 1L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        when(pacienteClient.obterPaciente(PACIENTE_ID_1)).thenReturn(Optional.empty());

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                agendamentoId, null, null, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("PAGINACAO_APOS_FILTRO_AGENDAMENTO: aplica offset/limit após filtros")
    void paginacaoAposFiltroAgendamento() {
        // Arrange
        Long agendamentoId = AGENDAMENTO_ID;
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        int limit = 2;
        int offset = 1;

        var decisoes = List.of(
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-01T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-05T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-10T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-15T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-20T10:00:00Z"), Instant.now())
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(decisoes, 5L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(PACIENTE_ID_1)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                agendamentoId, null, null, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(5L, resultado.total());
        assertEquals(2, resultado.items().size());
        assertEquals(decisoes.get(1).getId(), resultado.items().get(0).getId());
        assertEquals(decisoes.get(2).getId(), resultado.items().get(1).getId());
    }

    @Test
    @DisplayName("NENHUM_FILTRO_PACIENTE_AGENDAMENTO: sem filtros, retorna todos")
    void nenhumFiltroAgendamento() {
        // Arrange
        Long agendamentoId = AGENDAMENTO_ID;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_1, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), agendamentoId, PACIENTE_ID_2, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByAgendamentoIdWithFiltersAndStatusAgendamento(
                eq(agendamentoId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // Não mockamos PacienteClient pois não será chamado quando não há filtros de paciente

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                agendamentoId, null, null, null, null, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }
}
