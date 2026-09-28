package com.confirmasus.matching.domain;

import java.util.Objects;

/**
 * CPF do Paciente — value object com invariantes validadas no construtor
 * (formato: 11 dígitos; dígitos verificadores: algoritmo padrão mod 11;
 * rejeita sequências com todos os dígitos iguais).
 */
public final class Cpf {

    private final String numero;

    public Cpf(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");

        if (digitos.length() != 11) {
            throw new CpfInvalidoException("cpf deve conter 11 dígitos numéricos");
        }
        if (digitos.chars().distinct().count() == 1) {
            throw new CpfInvalidoException("cpf inválido (sequência de dígitos repetidos)");
        }
        if (!digitosVerificadoresConferem(digitos)) {
            throw new CpfInvalidoException("cpf inválido (dígito verificador não confere)");
        }

        this.numero = digitos;
    }

    private static boolean digitosVerificadoresConferem(String digitos) {
        int dv1 = calcularDigitoVerificador(digitos.substring(0, 9), 10);
        int dv2 = calcularDigitoVerificador(digitos.substring(0, 9) + dv1, 11);
        return (digitos.charAt(9) - '0') == dv1 && (digitos.charAt(10) - '0') == dv2;
    }

    private static int calcularDigitoVerificador(String base, int pesoInicial) {
        int soma = 0;
        int peso = pesoInicial;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0') * peso;
            peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    public String getNumero() {
        return numero;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cpf other)) return false;
        return numero.equals(other.numero);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numero);
    }
}
