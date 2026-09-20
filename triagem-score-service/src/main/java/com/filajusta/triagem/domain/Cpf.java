package com.filajusta.triagem.domain;

import java.util.Objects;

/**
 * Value object para CPF com validação de formato e checksum.
 * Rejeita sequências com dígitos repetidos (exceção 000.000.000-00, etc.)
 * que passariam em checksum ingênuo.
 */
public class Cpf {
  private final String valor;

  public Cpf(String valor) {
    Objects.requireNonNull(valor, "CPF não pode ser nulo");
    valor = valor.replaceAll("[^0-9]", "");

    if (valor.length() != 11) {
      throw new IllegalArgumentException("CPF deve conter exatamente 11 dígitos");
    }

    if (todosDigitosIguais(valor)) {
      throw new IllegalArgumentException("CPF com dígitos repetidos é inválido");
    }

    if (!validaChecksum(valor)) {
      throw new IllegalArgumentException("Checksum de CPF inválido");
    }

    this.valor = valor;
  }

  private static boolean todosDigitosIguais(String cpf) {
    return cpf.matches("^(\\d)\\1{10}$");
  }

  private static boolean validaChecksum(String cpf) {
    int[] numeros = new int[11];
    for (int i = 0; i < 11; i++) {
      numeros[i] = Character.getNumericValue(cpf.charAt(i));
    }

    // Primeiro dígito verificador
    int soma = 0;
    for (int i = 0; i < 9; i++) {
      soma += numeros[i] * (10 - i);
    }
    int resto = soma % 11;
    int dv1 = resto < 2 ? 0 : 11 - resto;

    if (numeros[9] != dv1) {
      return false;
    }

    // Segundo dígito verificador
    soma = 0;
    for (int i = 0; i < 10; i++) {
      soma += numeros[i] * (11 - i);
    }
    resto = soma % 11;
    int dv2 = resto < 2 ? 0 : 11 - resto;

    return numeros[10] == dv2;
  }

  public String valor() {
    return valor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Cpf cpf = (Cpf) o;
    return Objects.equals(valor, cpf.valor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valor);
  }

  @Override
  public String toString() {
    return "Cpf{" + valor + '}';
  }
}
