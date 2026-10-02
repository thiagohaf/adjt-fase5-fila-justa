package com.confirmasus.matching.application.command;

import java.util.UUID;

/**
 * A Sugestão de Repasse não está mais {@code PENDENTE} (outro Gestor/duplo
 * clique decidiu antes) -- {@code 409} (AD-6).
 */
public class SugestaoRepasseJaDecididaException extends RuntimeException {

    public SugestaoRepasseJaDecididaException(UUID sugestaoId) {
        super("Sugestao de Repasse ja decidida ou inexistente como pendente: " + sugestaoId);
    }
}
