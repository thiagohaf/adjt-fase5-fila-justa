package com.filajusta.agendamento.domain;

public class AgendamentoComDadosIncompletosException extends RuntimeException {
    public AgendamentoComDadosIncompletosException(String message) {
        super(message);
    }

    public AgendamentoComDadosIncompletosException(String message, Throwable cause) {
        super(message, cause);
    }
}
