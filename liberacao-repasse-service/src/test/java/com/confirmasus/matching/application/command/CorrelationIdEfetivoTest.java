package com.confirmasus.matching.application.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdEfetivoTest {

    @Test
    void geraUuidQuandoAusenteOuEmBranco() {
        assertThat(UUID.fromString(CorrelationIdEfetivo.de(null))).isNotNull();
        assertThat(UUID.fromString(CorrelationIdEfetivo.de("  "))).isNotNull();
    }

    @Test
    void preservaValorInformadoDentroDoLimite() {
        assertThat(CorrelationIdEfetivo.de("abc-123")).isEqualTo("abc-123");
        assertThat(CorrelationIdEfetivo.de("x".repeat(128))).hasSize(128);
    }

    @Test
    void rejeitaValorAcimaDoLimite() {
        assertThatThrownBy(() -> CorrelationIdEfetivo.de("x".repeat(129)))
                .isInstanceOf(CorrelationIdInvalidoException.class)
                .hasMessageContaining("128");
    }
}
