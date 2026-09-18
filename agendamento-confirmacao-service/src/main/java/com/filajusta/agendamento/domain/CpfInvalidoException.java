package com.filajusta.agendamento.domain;

/**
 * CPF com formato ou digito verificador invalido (Boundaries da spec 1.1:
 * "Toda rejeicao de entrada invalida (CPF, recursoId, dataHoraAgendamento)
 * retorna 422"). Traduzida para {@code 422} RFC 7807 pelo
 * {@code AgendamentoExceptionHandler} (infrastructure/web), nomeando o
 * campo {@code cpf}.
 */
public class CpfInvalidoException extends RuntimeException {

    public CpfInvalidoException(String mensagem) {
        super(mensagem);
    }
}
