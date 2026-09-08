package com.filajusta.triagem.infrastructure.web;

import java.util.List;

/**
 * Corpo de {@code POST /v1/triagens}. Deliberadamente sem anotacoes de Bean
 * Validation: presenca/faixa de cada campo (CPF, sinais vitais, gravidade
 * percebida) e responsabilidade do dominio ({@code Cpf}, {@code SinaisVitais},
 * {@code GravidadePercebida}), garantindo um unico caminho de validacao e a
 * ordem deterministica exigida pelas Boundaries da spec 2.1 ("400 no
 * primeiro campo invalido encontrado"). {@link TriagemController} trata
 * {@code sinaisVitais} nulo repassando campos nulos ao dominio, que reporta
 * o primeiro sinal vital ausente.
 */
record RegistrarTriagemRequest(
        String cpf,
        SinaisVitaisRequest sinaisVitais,
        String gravidadePercebida,
        List<String> sintomas) {

    record SinaisVitaisRequest(
            Double frequenciaCardiaca,
            Double pressaoArterialSistolica,
            Double pressaoArterialDiastolica,
            Double saturacaoOxigenio,
            Double frequenciaRespiratoria,
            Double temperatura) {
    }
}
