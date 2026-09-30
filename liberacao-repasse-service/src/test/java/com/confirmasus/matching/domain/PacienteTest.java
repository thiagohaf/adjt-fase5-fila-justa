package com.confirmasus.matching.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PacienteTest {

    @Test
    void guardaIdECpf() {
        Cpf cpf = new Cpf("52998224725");
        Paciente paciente = new Paciente(7L, cpf);
        assertThat(paciente.getId()).isEqualTo(7L);
        assertThat(paciente.getCpf()).isEqualTo(cpf);
    }

    @Test
    void permiteIdNuloParaPacienteAindaNaoPersistido() {
        assertThat(new Paciente(null, new Cpf("52998224725")).getId()).isNull();
    }

    @Test
    void rejeitaCpfNulo() {
        assertThatThrownBy(() -> new Paciente(1L, null)).isInstanceOf(NullPointerException.class);
    }
}
