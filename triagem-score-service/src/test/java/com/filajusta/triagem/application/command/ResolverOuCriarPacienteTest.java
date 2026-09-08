package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ResolverOuCriarPaciente}, incluindo a corrida de dois
 * requests concorrentes para o mesmo CPF novo (ambos passam o
 * {@code SELECT} antes de qualquer {@code INSERT}): o perdedor do
 * {@code INSERT} recebe {@link DataIntegrityViolationException} da
 * constraint {@code UNIQUE(cpf)} e deve reconsultar, nunca propagar um
 * {@code 500} para um cenario que e, na pratica, o mesmo "CPF ja usado" da
 * I/O Matrix da spec 2.1.
 */
class ResolverOuCriarPacienteTest {

    private final PacienteRepositorio pacienteRepositorio = mock(PacienteRepositorio.class);
    private final ResolverOuCriarPaciente resolverOuCriarPaciente = new ResolverOuCriarPaciente(pacienteRepositorio);
    private final Cpf cpf = new Cpf("52998224725");

    @Test
    void cpfJaExistenteNaoTentaSalvar() {
        Paciente existente = new Paciente(1L, cpf);
        when(pacienteRepositorio.buscarPorCpf(cpf)).thenReturn(Optional.of(existente));

        Paciente resolvido = resolverOuCriarPaciente.resolver(cpf);

        assertThat(resolvido).isSameAs(existente);
        verify(pacienteRepositorio, never()).salvar(any());
    }

    @Test
    void cpfNovoCriaPaciente() {
        Paciente salvo = new Paciente(2L, cpf);
        when(pacienteRepositorio.buscarPorCpf(cpf)).thenReturn(Optional.empty());
        when(pacienteRepositorio.salvar(any())).thenReturn(salvo);

        Paciente resolvido = resolverOuCriarPaciente.resolver(cpf);

        assertThat(resolvido).isSameAs(salvo);
    }

    @Test
    void corridaEntreDoisRequestsParaOMesmoCpfNovoReconsultaOVencedorAoInvesDeFalhar() {
        Paciente vencedor = new Paciente(3L, cpf);
        when(pacienteRepositorio.buscarPorCpf(cpf))
                .thenReturn(Optional.empty()) // 1a leitura: ainda nao existe.
                .thenReturn(Optional.of(vencedor)); // reconsulta apos a corrida: outro request ja criou.
        when(pacienteRepositorio.salvar(any())).thenThrow(new DataIntegrityViolationException("UNIQUE(cpf)"));

        Paciente resolvido = resolverOuCriarPaciente.resolver(cpf);

        assertThat(resolvido).isSameAs(vencedor);
    }

    @Test
    void relancaAExcecaoOriginalSeAReconsultaAindaNaoEncontrarNinguem() {
        DataIntegrityViolationException excecaoOriginal = new DataIntegrityViolationException("UNIQUE(cpf)");
        when(pacienteRepositorio.buscarPorCpf(cpf)).thenReturn(Optional.empty());
        when(pacienteRepositorio.salvar(any())).thenThrow(excecaoOriginal);

        assertThatThrownBy(() -> resolverOuCriarPaciente.resolver(cpf))
                .isSameAs(excecaoOriginal);
    }
}
