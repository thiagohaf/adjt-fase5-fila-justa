package com.filajusta.agendamento.domain;

/**
 * {@code dataHoraAgendamento} ausente ou nao estritamente futura (Boundaries
 * da spec 1.1: "deve ser um instante futuro (&gt; now()) -- passado ou
 * ausente e 422"). Traduzida para {@code 422} RFC 7807 pelo
 * {@code AgendamentoExceptionHandler} (infrastructure/web), nomeando o
 * campo {@code dataHoraAgendamento}.
 */
public class DataHoraAgendamentoInvalidaException extends RuntimeException {

    public DataHoraAgendamentoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
