package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.domain.StatusAgendamento;
import com.confirmasus.auditoria.domain.TipoDecisao;
import com.confirmasus.auditoria.domain.TipoPaciente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditoriaFiltrosRequest - Validações")
class AuditoriaFiltrosRequestTest {

    @Test
    @DisplayName("FACTORY_METHOD_VALIDO: todos os parâmetros válidos")
    void factoryMethodValido() {
        // Arrange
        Instant startDate = Instant.parse("2026-09-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-30T23:59:59Z");
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        StatusAgendamento statusAgendamento = StatusAgendamento.CONFIRMADO;
        TipoPaciente tipoPaciente = TipoPaciente.REGULAR;
        String nomePaciente = "João Silva";
        String cpfPaciente = "12345678901";
        Integer limit = 100;
        Integer offset = 10;

        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                startDate, endDate, tipoDecisao, statusAgendamento,
                tipoPaciente, nomePaciente, cpfPaciente, limit, offset
        );

        // Assert
        assertEquals(startDate, request.startDate());
        assertEquals(endDate, request.endDate());
        assertEquals(tipoDecisao, request.tipoDecisao());
        assertEquals(statusAgendamento, request.statusAgendamento());
        assertEquals(tipoPaciente, request.tipoPaciente());
        assertEquals(nomePaciente, request.nomePaciente());
        assertEquals(cpfPaciente, request.cpfPaciente());
        assertEquals(limit, request.limit());
        assertEquals(offset, request.offset());
    }

    @Test
    @DisplayName("FACTORY_METHOD_TODOS_NULL: todos os parâmetros opcionais como null")
    void factoryMethodTodosNull() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, null, null
        );

        // Assert
        assertNull(request.startDate());
        assertNull(request.endDate());
        assertNull(request.tipoDecisao());
        assertNull(request.statusAgendamento());
        assertNull(request.tipoPaciente());
        assertNull(request.nomePaciente());
        assertNull(request.cpfPaciente());
        assertNull(request.limit());
        assertNull(request.offset());
    }

    @Test
    @DisplayName("VALIDACAO_DATA_RANGE: startDate > endDate lança exceção")
    void validacaoDataRangeInvalido() {
        // Arrange
        Instant startDate = Instant.parse("2026-09-30T00:00:00Z");
        Instant endDate = Instant.parse("2026-09-01T00:00:00Z");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        startDate, endDate, null, null, null, null, null, null, null
                )
        );
    }

    @Test
    @DisplayName("VALIDACAO_DATA_IGUAL: startDate = endDate é válido")
    void validacaoDataIgualValido() {
        // Arrange
        Instant instant = Instant.parse("2026-09-15T10:00:00Z");

        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                instant, instant, null, null, null, null, null, null, null
        );

        // Assert
        assertEquals(instant, request.startDate());
        assertEquals(instant, request.endDate());
    }

    @Test
    @DisplayName("VALIDACAO_LIMIT_MAXIMO: limit > MAX_LIMIT lança exceção")
    void validacaoLimitMaximo() {
        // Arrange
        Integer limitInvalido = AuditoriaFiltrosRequest.MAX_LIMIT + 1;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        null, null, null, null, null, null, null, limitInvalido, null
                )
        );
    }

    @Test
    @DisplayName("VALIDACAO_LIMIT_IGUAL_MAXIMO: limit = MAX_LIMIT é válido")
    void validacaoLimitIgualMaximo() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, AuditoriaFiltrosRequest.MAX_LIMIT, null
        );

        // Assert
        assertEquals(AuditoriaFiltrosRequest.MAX_LIMIT, request.limit());
    }

    @Test
    @DisplayName("VALIDACAO_CPF_VALIDO: apenas dígitos")
    void validacaoCpfValido() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, "12345678901", null, null
        );

        // Assert
        assertEquals("12345678901", request.cpfPaciente());
    }

    @Test
    @DisplayName("VALIDACAO_CPF_COM_PONTO: contém ponto lança exceção")
    void validacaoCpfComPonto() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        null, null, null, null, null, null, "123.456.789-01", null, null
                )
        );
    }

    @Test
    @DisplayName("VALIDACAO_CPF_COM_HIFEN: contém hífen lança exceção")
    void validacaoCpfComHifen() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        null, null, null, null, null, null, "12345678901-99", null, null
                )
        );
    }

    @Test
    @DisplayName("VALIDACAO_CPF_COM_LETRA: contém letra lança exceção")
    void validacaoCpfComLetra() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        null, null, null, null, null, null, "1234567890a", null, null
                )
        );
    }

    @Test
    @DisplayName("VALIDACAO_CPF_COMPRIMENTO_INVALIDO: CPF com comprimento != 11 lança exceção")
    void validacaoCpfComprimentoInvalido() {
        // CPF com 10 dígitos
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        null, null, null, null, null, null, "1234567890", null, null
                )
        );
        // CPF com 12 dígitos
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        null, null, null, null, null, null, "123456789012", null, null
                )
        );
    }

    @Test
    @DisplayName("VALIDACAO_CPF_NULL: CPF null é válido (opcional)")
    void validacaoCpfNull() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, null, null
        );

        // Assert
        assertNull(request.cpfPaciente());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_LIMIT_NULL: retorna DEFAULT_LIMIT quando null")
    void getEffectiveLimitNull() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, null, null
        );

        // Assert
        assertEquals(AuditoriaFiltrosRequest.DEFAULT_LIMIT, request.getEffectiveLimit());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_LIMIT_ZERO: retorna DEFAULT_LIMIT quando 0")
    void getEffectiveLimitZero() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, 0, null
        );

        // Assert
        assertEquals(AuditoriaFiltrosRequest.DEFAULT_LIMIT, request.getEffectiveLimit());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_LIMIT_NEGATIVO: retorna DEFAULT_LIMIT quando negativo")
    void getEffectiveLimitNegativo() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, -5, null
        );

        // Assert
        assertEquals(AuditoriaFiltrosRequest.DEFAULT_LIMIT, request.getEffectiveLimit());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_LIMIT_ACIMA_MAXIMO: factory lança exceção quando limit > MAX_LIMIT")
    void getEffectiveLimitAcimaMaximo() {
        // Act & Assert - factory deve lançar exceção quando limit > MAX_LIMIT
        assertThrows(IllegalArgumentException.class, () ->
                AuditoriaFiltrosRequest.of(
                        null, null, null, null, null, null, null, 300, null
                )
        );
    }

    @Test
    @DisplayName("GET_EFFECTIVE_LIMIT_VALIDO: retorna o valor quando válido")
    void getEffectiveLimitValido() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, 75, null
        );

        // Assert
        assertEquals(75, request.getEffectiveLimit());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_OFFSET_NULL: retorna DEFAULT_OFFSET quando null")
    void getEffectiveOffsetNull() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, null, null
        );

        // Assert
        assertEquals(AuditoriaFiltrosRequest.DEFAULT_OFFSET, request.getEffectiveOffset());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_OFFSET_NEGATIVO: retorna DEFAULT_OFFSET quando negativo")
    void getEffectiveOffsetNegativo() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, null, -5
        );

        // Assert
        assertEquals(AuditoriaFiltrosRequest.DEFAULT_OFFSET, request.getEffectiveOffset());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_OFFSET_ZERO: retorna 0 quando 0")
    void getEffectiveOffsetZero() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, null, 0
        );

        // Assert
        assertEquals(0, request.getEffectiveOffset());
    }

    @Test
    @DisplayName("GET_EFFECTIVE_OFFSET_VALIDO: retorna o valor quando válido")
    void getEffectiveOffsetValido() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, null, null, 25
        );

        // Assert
        assertEquals(25, request.getEffectiveOffset());
    }

    @Test
    @DisplayName("GET_OPTIONAL_METHODS: retornam Optional correto")
    void getOptionalMethods() {
        // Arrange
        Instant startDate = Instant.parse("2026-09-01T00:00:00Z");
        TipoDecisao tipoDecisao = TipoDecisao.RECUSA;
        TipoPaciente tipoPaciente = TipoPaciente.PRIORITARIO;
        String nomePaciente = "João";

        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                startDate, null, tipoDecisao, null,
                tipoPaciente, nomePaciente, null, null, null
        );

        // Assert
        assertEquals(Optional.of(startDate), request.getStartDateOptional());
        assertEquals(Optional.empty(), request.getEndDateOptional());
        assertEquals(Optional.of(tipoDecisao), request.getTipoDecisaoOptional());
        assertEquals(Optional.empty(), request.getStatusAgendamentoOptional());
        assertEquals(Optional.of(tipoPaciente), request.getTipoPacienteOptional());
        assertEquals(Optional.of(nomePaciente), request.getNomePacienteOptional());
        assertEquals(Optional.empty(), request.getCpfPacienteOptional());
    }

    @Test
    @DisplayName("CPF_11_DIGITOS: CPF com exatamente 11 dígitos é válido")
    void cpf11DigitosValido() {
        // Act
        AuditoriaFiltrosRequest request = AuditoriaFiltrosRequest.of(
                null, null, null, null, null, null, "12345678901", null, null
        );

        // Assert
        assertEquals("12345678901", request.cpfPaciente());
    }
}
