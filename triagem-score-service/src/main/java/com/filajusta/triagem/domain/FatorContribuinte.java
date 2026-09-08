package com.filajusta.triagem.domain;

/**
 * Um fator (sinal vital ou gravidade percebida) e sua subnota {@code 0..1}
 * no calculo do Score v1 (Design Notes da spec 2.1), ex.:
 * {@code {"fator": "frequencia_cardiaca", "contribuicao": 0.4}}.
 */
public record FatorContribuinte(String fator, double contribuicao) {
}
