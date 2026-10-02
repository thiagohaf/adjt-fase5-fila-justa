package com.confirmasus.matching.application.command;

import java.util.UUID;

public class SugestaoRepasseNaoEncontradaException extends RuntimeException {

    public SugestaoRepasseNaoEncontradaException(UUID sugestaoId) {
        super("Sugestao de Repasse nao encontrada: " + sugestaoId);
    }
}
