package com.confirmasus.matching.application.command;

public class EntradaJaExisteException extends RuntimeException {
    public EntradaJaExisteException(String message) {
        super(message);
    }
}
