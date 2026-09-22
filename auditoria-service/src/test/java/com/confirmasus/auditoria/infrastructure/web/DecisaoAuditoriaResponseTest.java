package com.confirmasus.auditoria.infrastructure.web;

import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DecisaoAuditoriaResponse")
class DecisaoAuditoriaResponseTest {

    @Test
    @DisplayName("de() deve converter DecisaoAuditoria corretamente")
    void testDeConversao() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        Long agendamentoId = 456L;
        Long pacienteId = 123L;
        TipoDecisao tipo = TipoDecisao.LIBERACAO;
        String motivo = "Paciente aprovado";
        Instant timestamp = Instant.now();
        Instant criadoEm = Instant.now();

        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                eventId, agendamentoId, pacienteId, tipo, motivo, timestamp, criadoEm
        );

        // Act
        DecisaoAuditoriaResponse response = DecisaoAuditoriaResponse.de(decisao);

        // Assert
        assertNotNull(response);
        assertEquals(eventId, response.eventId());
        assertEquals(agendamentoId, response.agendamentoId());
        assertEquals(pacienteId, response.pacienteId());
        assertEquals(tipo, response.tipoDecisao());
        assertEquals(motivo, response.motivo());
        assertEquals(timestamp, response.timestamp());
        assertEquals(criadoEm, response.criadoEm());
    }

    @Test
    @DisplayName("de() deve lançar IllegalArgumentException quando decisao é null")
    void testDeComNullDeveLancarExcecao() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> DecisaoAuditoriaResponse.de(null),
                "DecisaoAuditoria não pode ser null"
        );
    }

    @Test
    @DisplayName("de() deve preservar motivo null corretamente")
    void testDeComMotivoNull() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                eventId, 456L, 123L, TipoDecisao.NOTIFICACAO, null, Instant.now(), Instant.now()
        );

        // Act
        DecisaoAuditoriaResponse response = DecisaoAuditoriaResponse.de(decisao);

        // Assert
        assertNotNull(response);
        assertNull(response.motivo());
    }
}
