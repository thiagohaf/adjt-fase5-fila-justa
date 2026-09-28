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
@DisplayName("ConsultarAuditoriaPaciente com Filtros de Paciente (Story 4.4b)")
class ConsultarAuditoriaPacienteComFiltrosPacienteTest {

    @Mock
    private DecisaoAuditoriaRepositorio repositorio;

    @Mock
    private PacienteClient pacienteClient;

    private ConsultarAuditoriaPaciente useCase;

    private static final Long PACIENTE_ID = 1L;
    private static final Long AGENDAMENTO_ID_1 = 10L;
    private static final Long AGENDAMENTO_ID_2 = 11L;
    private static final String CPF_JOAO = "12345678901";
    private static final String CPF_MARIA = "98765432109";
    private static final String NOME_JOAO = "João Silva";
    private static final String NOME_MARIA = "Maria Santos";

    @BeforeEach
    void setup() {
        useCase = new ConsultarAuditoriaPaciente(repositorio, pacienteClient);
    }

    @Test
    @DisplayName("FILTRO_TIPO_PACIENTE: retorna apenas registros com tipo específico")
    void filtroTipoPaciente() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_2, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        // Mock repositório retorna sem limite
        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // Mock PacienteClient
        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "PRIORITARIO");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
        assertTrue(resultado.items().stream()
                .allMatch(d -> d.getPacienteId() == pacienteId)
        );
    }

    @Test
    @DisplayName("FILTRO_NOME_PACIENTE: retorna registros com nome contendo string case-insensitive")
    void filtroNomePacienteLikeInsensitive() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        String nomePaciente = "joão";
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_2, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // PacienteClient retorna nome "João Silva" (maiúscula)
        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, null, nomePaciente, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
        // Ambos incluem "joão" quando convertido para minúsculas
    }

    @Test
    @DisplayName("FILTRO_CPF_PACIENTE: retorna registros com CPF exato")
    void filtroCpfPaciente() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        String cpfPaciente = CPF_JOAO;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_2, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, cpfPaciente, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, null, null, cpfPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("FILTRO_TRIPLO: combina tipoPaciente AND nomePaciente AND cpfPaciente")
    void filtroTriplo() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        String nomePaciente = "joão";
        String cpfPaciente = CPF_JOAO;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_2, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, cpfPaciente, "PRIORITARIO");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, tipoPaciente, nomePaciente, cpfPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("FILTRO_PACIENTE_COM_DATA: combina filtros de paciente com date range")
    void filtroPacienteComData() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        Instant startDate = Instant.parse("2026-09-10T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-25T23:59:59Z");
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_2, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), eq(startDate), eq(endDate), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, startDate, endDate, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("FILTRO_PACIENTE_COM_TIPO_DECISAO: combina filtros de paciente com tipo decisão")
    void filtroPacienteComTipoDecisao() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        TipoDecisao tipoDecisao = TipoDecisao.LIBERACAO;
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_2, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), eq(tipoDecisao), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, tipoDecisao, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("PACIENTE_NAO_ENCONTRADO: PacienteClient retorna empty, filtra fora")
    void pacienteNaoEncontrado() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1), 1L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // PacienteClient retorna empty (paciente não encontrado)
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.empty());

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("PAGINACAO_APOS_FILTRO_PACIENTE: aplica offset/limit após filtros")
    void paginacaoAposFiltro() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        int limit = 2;
        int offset = 1;

        // Cria 5 decisões, todas com o mesmo paciente tipo REGULAR
        var decisoes = List.of(
                DecisaoAuditoria.criar(UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-01T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-05T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-10T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-15T10:00:00Z"), Instant.now()),
                DecisaoAuditoria.criar(UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                        Instant.parse("2026-09-20T10:00:00Z"), Instant.now())
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(decisoes, 5L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(5L, resultado.total()); // Total após filtros
        assertEquals(2, resultado.items().size()); // Mas retorna apenas 2 (limit)
        // Verifica que offset começou do índice 1
        assertEquals(decisoes.get(1).getId(), resultado.items().get(0).getId());
        assertEquals(decisoes.get(2).getId(), resultado.items().get(1).getId());
    }

    @Test
    @DisplayName("NENHUM_FILTRO_PACIENTE: sem filtros de paciente, retorna todos")
    void nenhumFiltroDeleta() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        int limit = 50;
        int offset = 0;

        var decisao1 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );
        var decisao2 = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_2, pacienteId, TipoDecisao.LIBERACAO, "Liberado",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao1, decisao2), 2L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // Act - sem filtros de paciente (null, null, null)
        // Não mockamos PacienteClient pois não será chamado quando não há filtros de paciente
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, null, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(2L, resultado.total());
        assertEquals(2, resultado.items().size());
    }

    @Test
    @DisplayName("NOME_PARCIAL: filtro nome contém substring em qualquer posição")
    void nomeParcial() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        String nomePaciente = "silva"; // parte do nome
        int limit = 50;
        int offset = 0;

        var decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao), 1L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // Nome completo é "João Silva"
        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act - filtra por "silva"
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, null, nomePaciente, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(1L, resultado.total());
        assertEquals(1, resultado.items().size());
    }

    @Test
    @DisplayName("NOME_NAO_ENCONTRADO: retorna vazio quando nome não contém string")
    void nomeNaoEncontrado() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        String nomePaciente = "inexistente";
        int limit = 50;
        int offset = 0;

        var decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao), 1L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, null, nomePaciente, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("NOME_NULO_COM_FILTRO: quando nomePaciente é filtrado e paciente.nome é null, retorna vazio")
    void nomeNuloComFiltro() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        String nomePaciente = "João";
        int limit = 50;
        int offset = 0;

        var decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao), 1L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // Paciente com nome null
        PacienteDTO paciente = new PacienteDTO(1L, null, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, null, nomePaciente, null, limit, offset, Optional.empty()
        );

        // Assert - Quando nomePaciente é fornecido e nome é null, não retorna
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("CPF_NAO_ENCONTRADO: retorna vazio quando CPF não bate")
    void cpfNaoEncontrado() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        String cpfPaciente = "99999999999"; // CPF diferente
        int limit = 50;
        int offset = 0;

        var decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao), 1L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, null, null, cpfPaciente, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    @DisplayName("TIPO_PACIENTE_NAO_ENCONTRADO: retorna vazio quando tipo não bate")
    void tipoPacienteNaoEncontrado() {
        // Arrange
        Long pacienteId = PACIENTE_ID;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        int limit = 50;
        int offset = 0;

        var decisao = DecisaoAuditoria.criar(
                UUID.randomUUID(), AGENDAMENTO_ID_1, pacienteId, TipoDecisao.CONFIRMACAO, null,
                Instant.parse("2026-09-15T10:00:00Z"), Instant.now()
        );

        DecisaoAuditoriaRepositorio.PaginatedResult<DecisaoAuditoria> allResults =
                new DecisaoAuditoriaRepositorio.PaginatedResult<>(List.of(decisao), 1L);
        when(repositorio.findByPacienteIdWithFiltersAndStatusAgendamento(
                eq(pacienteId), isNull(), isNull(), isNull(), isNull(), eq(Integer.MAX_VALUE), eq(0)
        )).thenReturn(allResults);

        // Paciente é REGULAR, não PRIORITARIO
        PacienteDTO paciente = new PacienteDTO(1L, NOME_JOAO, CPF_JOAO, "REGULAR");
        when(pacienteClient.obterPaciente(pacienteId)).thenReturn(Optional.of(paciente));

        // Act
        var resultado = useCase.consultarComFiltrosPaciente(
                pacienteId, null, null, null, null, tipoPaciente, null, null, limit, offset, Optional.empty()
        );

        // Assert
        assertEquals(0L, resultado.total());
        assertTrue(resultado.items().isEmpty());
    }
}
