package com.confirmasus.agendamento.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgendamentoComDadosIncompletosExceptionTest {

    @Test
    void guardaMensagem() {
        assertThat(new AgendamentoComDadosIncompletosException("faltou campo")).hasMessage("faltou campo");
    }

    @Test
    void guardaMensagemECausa() {
        Throwable causa = new IllegalStateException("origem");
        AgendamentoComDadosIncompletosException ex = new AgendamentoComDadosIncompletosException("faltou", causa);
        assertThat(ex).hasMessage("faltou").hasCause(causa);
    }
}
