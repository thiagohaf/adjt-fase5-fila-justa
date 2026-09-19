package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.StatusAgendamento;

/**
 * Lancada por {@link ConfirmarPresenca} quando a escrita condicional
 * ({@code UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'}, AD-4) afeta 0
 * linhas e a releitura ({@code AgendamentoRepositorio#buscarPorId}) mostra
 * que o Agendamento esta em um estado perdedor diferente de {@code
 * CONFIRMADO} (esse caso e sucesso silencioso, nao uma excecao) -- {@code
 * 409} via {@code AgendamentoExceptionHandler} (spec 1.3, Boundaries; molde
 * {@code RecursoJaAlocadoException}, matching-alocacao-service).
 *
 * <p>Mensagem distingue os dois estados perdedores possiveis: {@code
 * AGUARDANDO_JANELA} ("janela ainda nao aberta") e {@code LIBERADO} ("vaga ja
 * liberada"), conforme a I/O &amp; Edge-Case Matrix da spec.
 */
public class AgendamentoForaDaJanelaException extends RuntimeException {

    public AgendamentoForaDaJanelaException(Long id, StatusAgendamento statusAtual) {
        super(mensagem(id, statusAtual));
    }

    private static String mensagem(Long id, StatusAgendamento statusAtual) {
        return switch (statusAtual) {
            case AGUARDANDO_JANELA -> "Agendamento " + id + ": janela de confirmacao ainda nao foi aberta";
            case LIBERADO -> "Agendamento " + id + ": vaga ja foi liberada";
            default -> "Agendamento " + id + ": nao esta aguardando confirmacao (status atual: " + statusAtual + ")";
        };
    }
}
