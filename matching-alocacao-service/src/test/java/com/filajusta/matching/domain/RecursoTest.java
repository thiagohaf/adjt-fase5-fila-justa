package com.filajusta.matching.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a validação de invariantes de {@link Recurso} no construtor (AD-2)
 * -- I/O &amp; Edge-Case Matrix da spec 3.2b2: {@code codigoRecurso}
 * ausente/vazio e {@code especificidadeRank} inválido (ausente/zero/
 * negativo) são rejeitados, e {@code codigoRecurso} é normalizado (trim)
 * para preservar a idempotência do upsert (achado no code review
 * multi-agente da Story 3.2b2).
 */
class RecursoTest {

    private static final UUID RECURSO_ID = UUID.randomUUID();

    @Test
    void construtorRejeitaRecursoIdNulo() {
        assertThatThrownBy(() -> new Recurso(null, "LEITO-01", 1, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void construtorRejeitaCodigoRecursoNulo() {
        assertThatThrownBy(() -> new Recurso(RECURSO_ID, null, 1, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void construtorRejeitaCodigoRecursoVazioOuEmBranco() {
        assertThatThrownBy(() -> new Recurso(RECURSO_ID, "", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Recurso(RECURSO_ID, "   ", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorRejeitaEspecificidadeRankZeroOuNegativo() {
        assertThatThrownBy(() -> new Recurso(RECURSO_ID, "LEITO-01", 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Recurso(RECURSO_ID, "LEITO-01", -1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorAceitaEspecificidadeRankPositivo() {
        Recurso recurso = new Recurso(RECURSO_ID, "LEITO-01", 1, true);

        assertThat(recurso.getEspecificidadeRank()).isEqualTo(1);
    }

    @Test
    void construtorNormalizaCodigoRecursoComEspacosAoRedor() {
        Recurso recurso = new Recurso(RECURSO_ID, "  LEITO-01  ", 1, true);

        assertThat(recurso.getCodigoRecurso()).isEqualTo("LEITO-01");
    }

    @Test
    void gettersExpoemOsCamposConstruidos() {
        Recurso recurso = new Recurso(RECURSO_ID, "ESPECIALISTA-CARDIO", 3, false);

        assertThat(recurso.getRecursoId()).isEqualTo(RECURSO_ID);
        assertThat(recurso.getCodigoRecurso()).isEqualTo("ESPECIALISTA-CARDIO");
        assertThat(recurso.getEspecificidadeRank()).isEqualTo(3);
        assertThat(recurso.isDisponivel()).isFalse();
    }
}
