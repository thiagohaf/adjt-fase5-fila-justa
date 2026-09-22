package com.confirmasus.agendamento.application.command;

/**
 * Lancada por {@link ConfirmarPresenca} quando {@code
 * AgendamentoRepositorio#buscarPorId} retorna vazio -- {@code id} sem
 * registro (I/O &amp; Edge-Case Matrix da spec 1.3: "agendamentoId
 * inexistente" -&gt; {@code 404} via {@code AgendamentoExceptionHandler}).
 */
public class AgendamentoNaoEncontradoException extends RuntimeException {

    public AgendamentoNaoEncontradoException(Long id) {
        super("Agendamento nao encontrado: " + id);
    }
}
