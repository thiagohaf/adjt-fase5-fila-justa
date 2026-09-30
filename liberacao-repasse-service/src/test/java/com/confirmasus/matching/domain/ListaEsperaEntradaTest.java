package com.confirmasus.matching.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListaEsperaEntradaTest {

    private static final UUID RECURSO = UUID.randomUUID();
    private static final Instant SOLICITADO = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant AGORA = Instant.parse("2026-09-20T12:00:00Z");

    @Test
    void novaEntradaNaoTemIdEGuardaOsCampos() {
        ListaEsperaEntrada entrada = ListaEsperaEntrada.nova(3L, RECURSO, SOLICITADO, AGORA);
        assertThat(entrada.getId()).isNull();
        assertThat(entrada.getPacienteId()).isEqualTo(3L);
        assertThat(entrada.getRecursoId()).isEqualTo(RECURSO);
        assertThat(entrada.getDataSolicitacao()).isEqualTo(SOLICITADO);
        assertThat(entrada.getCriadoEm()).isEqualTo(AGORA);
    }

    @Test
    void rejeitaCamposObrigatoriosNulos() {
        assertThatThrownBy(() -> ListaEsperaEntrada.nova(null, RECURSO, SOLICITADO, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ListaEsperaEntrada.nova(1L, null, SOLICITADO, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ListaEsperaEntrada.nova(1L, RECURSO, null, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ListaEsperaEntrada.nova(1L, RECURSO, SOLICITADO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void igualdadeEPorParPacienteERecurso() {
        ListaEsperaEntrada a = ListaEsperaEntrada.nova(1L, RECURSO, SOLICITADO, AGORA);
        ListaEsperaEntrada mesmoPar = new ListaEsperaEntrada(9L, 1L, RECURSO, AGORA, SOLICITADO);
        assertThat(a).isEqualTo(mesmoPar).hasSameHashCodeAs(mesmoPar).isEqualTo(a);
        assertThat(a).isNotEqualTo(ListaEsperaEntrada.nova(2L, RECURSO, SOLICITADO, AGORA))
                .isNotEqualTo(ListaEsperaEntrada.nova(1L, UUID.randomUUID(), SOLICITADO, AGORA))
                .isNotEqualTo("outro tipo");
    }
}
