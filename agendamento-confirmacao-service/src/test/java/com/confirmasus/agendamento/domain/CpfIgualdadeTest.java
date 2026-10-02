package com.confirmasus.agendamento.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfIgualdadeTest {

    @Test
    void igualdadeEHashConsideramApenasOsDigitos() {
        Cpf a = new Cpf("52998224725");
        assertThat(a.getNumero()).isEqualTo("52998224725");
        assertThat(a).isEqualTo(new Cpf("529.982.247-25")).hasSameHashCodeAs(new Cpf("529.982.247-25"));
        assertThat(a).isEqualTo(a).isNotEqualTo(new Cpf("11144477735")).isNotEqualTo("52998224725");
    }
}
