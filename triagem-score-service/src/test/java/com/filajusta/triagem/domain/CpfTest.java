package com.filajusta.triagem.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a validacao de formato/checksum de {@link Cpf} (Boundaries da spec
 * 2.1: "CPF validado (formato/checksum) ... retornando 400 no primeiro
 * campo invalido encontrado").
 */
class CpfTest {

    @Test
    void aceitaCpfValidoComDigitosPuros() {
        Cpf cpf = new Cpf("52998224725");

        assertThat(cpf.getNumero()).isEqualTo("52998224725");
    }

    @Test
    void aceitaCpfValidoFormatadoComMascara() {
        Cpf cpf = new Cpf("529.982.247-25");

        assertThat(cpf.getNumero()).isEqualTo("52998224725");
    }

    @Test
    void doisCpfsComOMesmoNumeroSaoIguais() {
        assertThat(new Cpf("52998224725")).isEqualTo(new Cpf("529.982.247-25"));
    }

    @Test
    void rejeitaDigitoVerificadorIncorreto() {
        assertThatThrownBy(() -> new Cpf("52998224726"))
                .isInstanceOf(CpfInvalidoException.class);
    }

    @Test
    void rejeitaTamanhoIncorreto() {
        assertThatThrownBy(() -> new Cpf("123456789"))
                .isInstanceOf(CpfInvalidoException.class);
    }

    @Test
    void rejeitaCpfNulo() {
        assertThatThrownBy(() -> new Cpf(null))
                .isInstanceOf(CpfInvalidoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    void rejeitaSequenciaDeDigitosRepetidos(String sequenciaRepetida) {
        assertThatThrownBy(() -> new Cpf(sequenciaRepetida))
                .isInstanceOf(CpfInvalidoException.class);
    }
}
