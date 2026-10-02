package com.confirmasus.matching.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @Test
    void aceitaCpfValidoComOuSemMascara() {
        assertThat(new Cpf("52998224725").getNumero()).isEqualTo("52998224725");
        assertThat(new Cpf("529.982.247-25").getNumero()).isEqualTo("52998224725");
    }

    @Test
    void rejeitaNuloEQuantidadeErradaDeDigitos() {
        assertThatThrownBy(() -> new Cpf(null)).isInstanceOf(CpfInvalidoException.class);
        assertThatThrownBy(() -> new Cpf("123")).isInstanceOf(CpfInvalidoException.class)
                .hasMessageContaining("11 dígitos");
    }

    @Test
    void rejeitaSequenciaDeDigitosRepetidos() {
        assertThatThrownBy(() -> new Cpf("11111111111")).isInstanceOf(CpfInvalidoException.class)
                .hasMessageContaining("repetidos");
    }

    @Test
    void rejeitaDigitoVerificadorInvalido() {
        assertThatThrownBy(() -> new Cpf("52998224724")).isInstanceOf(CpfInvalidoException.class)
                .hasMessageContaining("verificador");
        assertThatThrownBy(() -> new Cpf("52998224735")).isInstanceOf(CpfInvalidoException.class);
    }

    @Test
    void igualdadeConsideraApenasONumero() {
        Cpf a = new Cpf("52998224725");
        assertThat(a).isEqualTo(new Cpf("529.982.247-25")).hasSameHashCodeAs(new Cpf("52998224725"));
        assertThat(a).isNotEqualTo(new Cpf("11144477735")).isNotEqualTo("52998224725").isEqualTo(a);
    }
}
