package com.filajusta.matching.application.command;

/**
 * Lançada por {@link AlocacaoRepositorio#confirmar(com.filajusta.matching.domain.Alocacao)}
 * quando o {@code pacienteId} já tem uma {@code Alocacao} com {@code status="ATIVA"}
 * para outro Recurso -- mapeada do índice único parcial
 * {@code ux_alocacao_paciente_ativa} (Boundaries da spec 3-3b1). O Paciente
 * permanece elegível para qualquer outro Recurso; apenas uma segunda
 * confirmação simultânea é rejeitada.
 */
public class PacienteJaAlocadoException extends RuntimeException {

    public PacienteJaAlocadoException(long pacienteId) {
        super("Paciente ja possui uma Alocacao ativa: " + pacienteId);
    }
}
