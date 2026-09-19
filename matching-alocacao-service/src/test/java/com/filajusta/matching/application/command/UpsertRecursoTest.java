package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.Recurso;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link UpsertRecurso}: constrói o {@link Recurso} candidato com um
 * {@code recursoId} gerado e delega ao port {@link RecursoRepositorio}, que
 * devolve o Recurso efetivamente persistido -- {@link
 * UpsertRecurso#upsertar} decide {@code criado} comparando o {@code
 * recursoId} devolvido com o candidato que enviou (ver javadoc da classe).
 */
class UpsertRecursoTest {

    private final RecursoRepositorio repositorio = mock(RecursoRepositorio.class);
    private final UpsertRecurso useCase = new UpsertRecurso(repositorio);

    @Test
    void primeiroUpsertDeUmCodigoRecursoIneditoEDetectadoComoCriado() {
        // O adapter real devolve exatamente o candidato quando a linha foi
        // inserida (recurso_id nao existia antes) -- aqui simulado
        // devolvendo o mesmo objeto recebido.
        when(repositorio.upsert(any(Recurso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpsertRecurso.Resultado resultado = useCase.upsertar("LEITO-01", 2, true);

        ArgumentCaptor<Recurso> captor = ArgumentCaptor.forClass(Recurso.class);
        verify(repositorio).upsert(captor.capture());
        Recurso candidato = captor.getValue();

        assertThat(candidato.getCodigoRecurso()).isEqualTo("LEITO-01");
        assertThat(candidato.getEspecificidadeRank()).isEqualTo(2);
        assertThat(candidato.isDisponivel()).isTrue();
        assertThat(candidato.getRecursoId()).isNotNull();

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.recurso()).isSameAs(candidato);
    }

    @Test
    void upsertDeCodigoRecursoJaCadastradoEDetectadoComoAtualizadoQuandoORecursoIdPersistidoDifereDoCandidato() {
        // O adapter real devolve o recurso_id JA existente (preservado pelo
        // ON CONFLICT DO UPDATE) quando o codigoRecurso ja estava
        // cadastrado -- diferente do candidato que UpsertRecurso gerou.
        UUID recursoIdJaExistente = UUID.randomUUID();
        Recurso persistidoComIdAntigo =
                new Recurso(recursoIdJaExistente, "LEITO-01", 4, false);
        when(repositorio.upsert(any(Recurso.class))).thenReturn(persistidoComIdAntigo);

        UpsertRecurso.Resultado resultado = useCase.upsertar("LEITO-01", 4, false);

        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.recurso().getRecursoId()).isEqualTo(recursoIdJaExistente);
        assertThat(resultado.recurso().getEspecificidadeRank()).isEqualTo(4);
        assertThat(resultado.recurso().isDisponivel()).isFalse();
    }
}
