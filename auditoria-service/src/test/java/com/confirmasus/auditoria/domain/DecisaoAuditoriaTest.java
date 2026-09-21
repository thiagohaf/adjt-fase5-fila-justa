package com.confirmasus.auditoria.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para a entidade de domínio DecisaoAuditoria.
 *
 * <p>Valida:
 * - Imutabilidade pós-criação
 * - Validações de argumento (null checks)
 * - Factory method {@code criar()}
 * - Equals/hashCode por eventId
 */
class DecisaoAuditoriaTest {

    @Test
    @DisplayName("criar() retorna entidade com id=null (pré-persistência)")
    void testarCriarRetornaIdNull() {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        Instant criadoEm = Instant.now();

        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                eventId,
                123L,
                456L,
                TipoDecisao.CONFIRMACAO,
                null,
                timestamp,
                criadoEm
        );

        assertNull(decisao.getId());
        assertEquals(eventId, decisao.getEventId());
        assertEquals(TipoDecisao.CONFIRMACAO, decisao.getTipoDecisao());
    }

    @Test
    @DisplayName("Construtor rejeita eventId null")
    void testarConstructorRejectaEventIdNull() {
        assertThrows(NullPointerException.class, () -> {
            new DecisaoAuditoria(
                    null,
                    null,
                    123L,
                    456L,
                    TipoDecisao.CONFIRMACAO,
                    null,
                    Instant.now(),
                    Instant.now()
            );
        });
    }

    @Test
    @DisplayName("Construtor rejeita tipoDecisao null")
    void testarConstructorRejectaTipoDecisaoNull() {
        assertThrows(NullPointerException.class, () -> {
            new DecisaoAuditoria(
                    null,
                    UUID.randomUUID(),
                    123L,
                    456L,
                    null,
                    null,
                    Instant.now(),
                    Instant.now()
            );
        });
    }

    @Test
    @DisplayName("Construtor aceita agendamentoId/pacienteId null")
    void testarConstructorAceitaIdsNulls() {
        UUID eventId = UUID.randomUUID();
        DecisaoAuditoria decisao = new DecisaoAuditoria(
                null,
                eventId,
                null, // agendamentoId nullable
                null, // pacienteId nullable
                TipoDecisao.NOTIFICACAO,
                null,
                Instant.now(),
                Instant.now()
        );

        assertNull(decisao.getAgendamentoId());
        assertNull(decisao.getPacienteId());
    }

    @Test
    @DisplayName("equals() baseado em eventId")
    void testarEqualsBaseadoEmEventId() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        DecisaoAuditoria d1 = new DecisaoAuditoria(1L, eventId, 100L, 200L, TipoDecisao.CONFIRMACAO, null, now, now);
        DecisaoAuditoria d2 = new DecisaoAuditoria(2L, eventId, 300L, 400L, TipoDecisao.RECUSA, null, now, now);

        // Mesmo eventId → equals() = true (mesmo com outros campos diferentes)
        assertEquals(d1, d2);
    }

    @Test
    @DisplayName("hashCode() baseado em eventId")
    void testarHashCodeBaseadoEmEventId() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        DecisaoAuditoria d1 = new DecisaoAuditoria(1L, eventId, 100L, 200L, TipoDecisao.CONFIRMACAO, null, now, now);
        DecisaoAuditoria d2 = new DecisaoAuditoria(2L, eventId, 300L, 400L, TipoDecisao.RECUSA, null, now, now);

        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    @DisplayName("Diferentes eventIds → não são iguais")
    void testarDiferentesEventIds() {
        Instant now = Instant.now();

        DecisaoAuditoria d1 = new DecisaoAuditoria(1L, UUID.randomUUID(), 100L, 200L, TipoDecisao.CONFIRMACAO, null, now, now);
        DecisaoAuditoria d2 = new DecisaoAuditoria(2L, UUID.randomUUID(), 300L, 400L, TipoDecisao.RECUSA, null, now, now);

        assertNotEquals(d1, d2);
    }

    @Test
    @DisplayName("Campos nullable (motivo, agendamentoId, pacienteId) aceitam null")
    void testarCamposNullable() {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        Instant criadoEm = Instant.now();

        DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                eventId,
                null, // agendamentoId nullable
                null, // pacienteId nullable
                TipoDecisao.SUGESTAO_GERADA,
                null, // motivo nullable para este tipo
                timestamp,
                criadoEm
        );

        assertNull(decisao.getAgendamentoId());
        assertNull(decisao.getPacienteId());
        assertNull(decisao.getMotivo());
    }

    @Test
    @DisplayName("toString() não falha")
    void testarToString() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        DecisaoAuditoria decisao = new DecisaoAuditoria(
                1L, eventId, 100L, 200L, TipoDecisao.RECUSA, "motivo teste", now, now
        );

        String str = decisao.toString();
        assertNotNull(str);
        assertTrue(str.contains("DecisaoAuditoria"));
        assertTrue(str.contains("RECUSA"));
    }
}
