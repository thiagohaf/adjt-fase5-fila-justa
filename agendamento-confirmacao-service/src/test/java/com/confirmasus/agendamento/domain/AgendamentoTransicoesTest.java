package com.confirmasus.agendamento.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Transições imutáveis do modelo de domínio do {@link Agendamento} (AD-4). */
class AgendamentoTransicoesTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    private Agendamento novo() {
        return Agendamento.novo(1L, UUID.randomUUID(), AGORA.plusSeconds(86_400), AGORA, AGORA.plusSeconds(1800));
    }

    @Test
    void novoAgendamentoComecaAguardandoJanelaSemMotivo() {
        Agendamento agendamento = novo();
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGUARDANDO_JANELA);
        assertThat(agendamento.getMotivoLiberacao()).isNull();
    }

    @Test
    void abrirJanelaTransicionaParaAguardandoConfirmacaoPreservandoIdentidade() {
        Agendamento original = novo();
        Agendamento aberto = original.abrirJanela();
        assertThat(aberto.getStatus()).isEqualTo(StatusAgendamento.AGUARDANDO_CONFIRMACAO);
        assertThat(aberto.getAgendamentoId()).isEqualTo(original.getAgendamentoId());
        assertThat(original.getStatus()).isEqualTo(StatusAgendamento.AGUARDANDO_JANELA);
    }

    @Test
    void confirmarTransicionaParaConfirmado() {
        assertThat(novo().abrirJanela().confirmar().getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
    }

    @Test
    void recusarLiberaAVagaComMotivoRecusa() {
        Agendamento recusado = novo().abrirJanela().recusar();
        assertThat(recusado.getStatus()).isEqualTo(StatusAgendamento.LIBERADO);
        assertThat(recusado.getMotivoLiberacao()).isEqualTo(MotivoLiberacao.RECUSA.name());
    }
}
