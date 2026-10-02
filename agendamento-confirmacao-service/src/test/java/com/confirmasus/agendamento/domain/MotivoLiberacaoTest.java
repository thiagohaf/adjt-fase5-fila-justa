package com.confirmasus.agendamento.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MotivoLiberacaoTest {

    @Test
    void convertePraColunaDoBancoPeloNome() {
        assertThat(MotivoLiberacao.RECUSA.convertToDatabaseColumn(MotivoLiberacao.NAO_CONFIRMADO))
                .isEqualTo("NAO_CONFIRMADO");
        assertThat(MotivoLiberacao.RECUSA.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void converteDaColunaDoBancoParaOEnum() {
        assertThat(MotivoLiberacao.RECUSA.convertToEntityAttribute("RECUSA")).isEqualTo(MotivoLiberacao.RECUSA);
        assertThat(MotivoLiberacao.RECUSA.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> MotivoLiberacao.RECUSA.convertToEntityAttribute("INVALIDO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
