package com.filajusta.matching.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a validação de invariantes de {@link Alocacao} no construtor
 * (Story 3-3b1, mesmo padrão de {@code RecursoTest}).
 */
class AlocacaoTest {

    private static final UUID ALOCACAO_ID = UUID.randomUUID();
    private static final UUID RECURSO_ID = UUID.randomUUID();
    private static final Instant CONFIRMADO_EM = Instant.parse("2026-09-11T12:00:00Z");

    @Test
    void construtorRejeitaAlocacaoIdNulo() {
        assertThatThrownBy(() -> new Alocacao(null, RECURSO_ID, 1L, Alocacao.STATUS_ATIVA, CONFIRMADO_EM))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void construtorRejeitaRecursoIdNulo() {
        assertThatThrownBy(() -> new Alocacao(ALOCACAO_ID, null, 1L, Alocacao.STATUS_ATIVA, CONFIRMADO_EM))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void construtorRejeitaPacienteIdZeroOuNegativo() {
        assertThatThrownBy(() -> new Alocacao(ALOCACAO_ID, RECURSO_ID, 0L, Alocacao.STATUS_ATIVA, CONFIRMADO_EM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Alocacao(ALOCACAO_ID, RECURSO_ID, -1L, Alocacao.STATUS_ATIVA, CONFIRMADO_EM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorRejeitaStatusNuloOuEmBranco() {
        assertThatThrownBy(() -> new Alocacao(ALOCACAO_ID, RECURSO_ID, 1L, null, CONFIRMADO_EM))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Alocacao(ALOCACAO_ID, RECURSO_ID, 1L, "   ", CONFIRMADO_EM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorRejeitaConfirmadoEmNulo() {
        assertThatThrownBy(() -> new Alocacao(ALOCACAO_ID, RECURSO_ID, 1L, Alocacao.STATUS_ATIVA, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void gettersExpoemOsCamposConstruidos() {
        Alocacao alocacao = new Alocacao(ALOCACAO_ID, RECURSO_ID, 42L, Alocacao.STATUS_ATIVA, CONFIRMADO_EM);

        assertThat(alocacao.getAlocacaoId()).isEqualTo(ALOCACAO_ID);
        assertThat(alocacao.getRecursoId()).isEqualTo(RECURSO_ID);
        assertThat(alocacao.getPacienteId()).isEqualTo(42L);
        assertThat(alocacao.getStatus()).isEqualTo(Alocacao.STATUS_ATIVA);
        assertThat(alocacao.getConfirmadoEm()).isEqualTo(CONFIRMADO_EM);
    }
}
