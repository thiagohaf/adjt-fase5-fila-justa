package com.confirmasus.triagem.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpfTest {
  @Test
  void cpfValido_deveCriar() {
    assertDoesNotThrow(() -> new Cpf("12345678909"));
  }

  @Test
  void cpfComDuasFormatacoes_deveCriar() {
    assertDoesNotThrow(() -> new Cpf("123.456.789-09"));
  }

  @Test
  void cpfNulo_deveLancarExcecao() {
    assertThrows(NullPointerException.class, () -> new Cpf(null));
  }

  @Test
  void cpfComMenosDe11Digitos_deveLancarExcecao() {
    assertThrows(IllegalArgumentException.class, () -> new Cpf("1234567890"));
  }

  @Test
  void cpfComTodosDigitosIguais_deveLancarExcecao() {
    assertThrows(IllegalArgumentException.class, () -> new Cpf("11111111111"));
  }

  @Test
  void cpfComChecksumInvalido_deveLancarExcecao() {
    assertThrows(IllegalArgumentException.class, () -> new Cpf("12345678900"));
  }
}
