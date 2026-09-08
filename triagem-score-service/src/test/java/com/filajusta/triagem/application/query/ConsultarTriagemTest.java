package com.filajusta.triagem.application.query;

import com.filajusta.triagem.domain.Triagem;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ConsultarTriagem}: id encontrado devolve a Triagem persistida
 * tal como veio da porta (sem recalculo), id nao encontrado lanca
 * {@link TriagemNaoEncontradaException} nomeando o id (I/O & Edge-Case
 * Matrix da spec 2.2).
 */
class ConsultarTriagemTest {

    private final ConsultaTriagemRepositorio consultaTriagemRepositorio = mock(ConsultaTriagemRepositorio.class);
    private final ConsultarTriagem consultarTriagem = new ConsultarTriagem(consultaTriagemRepositorio);

    @Test
    void idEncontradoRetornaATriagemPersistidaSemAlteracao() {
        Triagem triagem = mock(Triagem.class);
        when(consultaTriagemRepositorio.buscarPorId(1L)).thenReturn(Optional.of(triagem));

        Triagem resultado = consultarTriagem.consultar(1L);

        assertThat(resultado).isSameAs(triagem);
    }

    @Test
    void idNaoEncontradoLancaTriagemNaoEncontradaNomeandoOId() {
        when(consultaTriagemRepositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultarTriagem.consultar(99L))
                .isInstanceOf(TriagemNaoEncontradaException.class)
                .hasMessageContaining("99");
    }
}
