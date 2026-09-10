package com.filajusta.triagem.application.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ListarScoresAtuais}: delega a porta e devolve exatamente o
 * que ela retornar, sem transformacao -- lista populada e lista vazia (I/O
 * & Edge-Case Matrix da spec 3.1a).
 */
class ListarScoresAtuaisTest {

    private final ScoresAtuaisRepositorio scoresAtuaisRepositorio = mock(ScoresAtuaisRepositorio.class);
    private final ListarScoresAtuais listarScoresAtuais = new ListarScoresAtuais(scoresAtuaisRepositorio);

    @Test
    void consultaNormalRetornaListaCompletaDosScoresAtuais() {
        ScoreAtual scoreAtual1 = mock(ScoreAtual.class);
        ScoreAtual scoreAtual2 = mock(ScoreAtual.class);
        when(scoresAtuaisRepositorio.listarTodos()).thenReturn(List.of(scoreAtual1, scoreAtual2));

        List<ScoreAtual> resultado = listarScoresAtuais.listar();

        assertThat(resultado).containsExactly(scoreAtual1, scoreAtual2);
    }

    @Test
    void bancoVazioRetornaListaVazia() {
        when(scoresAtuaisRepositorio.listarTodos()).thenReturn(List.of());

        List<ScoreAtual> resultado = listarScoresAtuais.listar();

        assertThat(resultado).isEmpty();
    }
}
