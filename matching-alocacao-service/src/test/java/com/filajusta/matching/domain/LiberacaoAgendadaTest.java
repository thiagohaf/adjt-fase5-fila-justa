package com.filajusta.matching.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre as invariantes do construtor de {@link LiberacaoAgendada} (Story
 * 3-4a1) -- até aqui só exercitadas indiretamente pelos caminhos felizes de
 * {@code ConfirmarAlocacaoTest} (achado do code review multi-agente:
 * nenhum teste direto provava cada guarda isoladamente).
 */
class LiberacaoAgendadaTest {

    private static final UUID ALOCACAO_ID = UUID.randomUUID();
    private static final UUID RECURSO_ID = UUID.randomUUID();
    private static final String CORRELATION_ID = "corr-1";
    private static final int DELAY_SEGUNDOS = 120;
    private static final Instant CRIADO_EM = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void construtorAceitaTodosOsCamposValidosComEnviadoEmNulo() {
        LiberacaoAgendada liberacao = new LiberacaoAgendada(
                ALOCACAO_ID, RECURSO_ID, CORRELATION_ID, DELAY_SEGUNDOS, CRIADO_EM, null);

        assertThat(liberacao.getAlocacaoId()).isEqualTo(ALOCACAO_ID);
        assertThat(liberacao.getRecursoId()).isEqualTo(RECURSO_ID);
        assertThat(liberacao.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(liberacao.getDelaySegundos()).isEqualTo(DELAY_SEGUNDOS);
        assertThat(liberacao.getCriadoEm()).isEqualTo(CRIADO_EM);
        assertThat(liberacao.getEnviadoEm()).isNull();
    }

    @Test
    void construtorAceitaEnviadoEmPreenchido() {
        Instant enviadoEm = CRIADO_EM.plusSeconds(DELAY_SEGUNDOS);

        LiberacaoAgendada liberacao = new LiberacaoAgendada(
                ALOCACAO_ID, RECURSO_ID, CORRELATION_ID, DELAY_SEGUNDOS, CRIADO_EM, enviadoEm);

        assertThat(liberacao.getEnviadoEm()).isEqualTo(enviadoEm);
    }

    @Test
    void alocacaoIdNuloLancaNullPointerException() {
        assertThatThrownBy(() -> new LiberacaoAgendada(
                null, RECURSO_ID, CORRELATION_ID, DELAY_SEGUNDOS, CRIADO_EM, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("alocacaoId");
    }

    @Test
    void recursoIdNuloLancaNullPointerException() {
        assertThatThrownBy(() -> new LiberacaoAgendada(
                ALOCACAO_ID, null, CORRELATION_ID, DELAY_SEGUNDOS, CRIADO_EM, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("recursoId");
    }

    @Test
    void correlationIdNuloLancaNullPointerException() {
        assertThatThrownBy(() -> new LiberacaoAgendada(
                ALOCACAO_ID, RECURSO_ID, null, DELAY_SEGUNDOS, CRIADO_EM, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void correlationIdEmBrancoLancaIllegalArgumentException() {
        assertThatThrownBy(() -> new LiberacaoAgendada(
                ALOCACAO_ID, RECURSO_ID, "   ", DELAY_SEGUNDOS, CRIADO_EM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void delaySegundosZeroLancaIllegalArgumentException() {
        assertThatThrownBy(() -> new LiberacaoAgendada(
                ALOCACAO_ID, RECURSO_ID, CORRELATION_ID, 0, CRIADO_EM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delaySegundos");
    }

    @Test
    void delaySegundosNegativoLancaIllegalArgumentException() {
        assertThatThrownBy(() -> new LiberacaoAgendada(
                ALOCACAO_ID, RECURSO_ID, CORRELATION_ID, -1, CRIADO_EM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delaySegundos");
    }

    @Test
    void criadoEmNuloLancaNullPointerException() {
        assertThatThrownBy(() -> new LiberacaoAgendada(
                ALOCACAO_ID, RECURSO_ID, CORRELATION_ID, DELAY_SEGUNDOS, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("criadoEm");
    }
}
