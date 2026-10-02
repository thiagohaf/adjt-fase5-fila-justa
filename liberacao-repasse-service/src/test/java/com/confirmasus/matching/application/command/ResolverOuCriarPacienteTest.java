package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.Cpf;
import com.confirmasus.matching.domain.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolverOuCriarPacienteTest {

    private static final Cpf CPF = new Cpf("52998224725");

    /** Fake em memória: opcionalmente simula a corrida em que outro request insere primeiro. */
    private static class FakeRepositorio implements PacienteRepositorio {
        final List<Paciente> salvos = new ArrayList<>();
        Paciente existente;
        boolean corridaNoSalvar;
        int buscas;

        @Override
        public Optional<Paciente> buscarPorCpf(Cpf cpf) {
            buscas++;
            return Optional.ofNullable(existente);
        }

        @Override
        public Paciente salvar(Paciente paciente) {
            if (corridaNoSalvar) {
                existente = new Paciente(42L, paciente.getCpf());
                throw new DataIntegrityViolationException("cpf duplicado");
            }
            Paciente salvo = new Paciente(1L, paciente.getCpf());
            salvos.add(salvo);
            existente = salvo;
            return salvo;
        }
    }

    @Test
    void reaproveitaPacienteExistente() {
        FakeRepositorio repo = new FakeRepositorio();
        repo.existente = new Paciente(5L, CPF);

        assertThat(new ResolverOuCriarPaciente(repo).resolver(CPF).getId()).isEqualTo(5L);
        assertThat(repo.salvos).isEmpty();
    }

    @Test
    void criaPacienteQuandoNaoExiste() {
        FakeRepositorio repo = new FakeRepositorio();

        Paciente paciente = new ResolverOuCriarPaciente(repo).resolver(CPF);

        assertThat(paciente.getId()).isEqualTo(1L);
        assertThat(repo.salvos).hasSize(1);
    }

    @Test
    void reconsultaAposCorridaDeInsercao() {
        FakeRepositorio repo = new FakeRepositorio();
        repo.corridaNoSalvar = true;

        assertThat(new ResolverOuCriarPaciente(repo).resolver(CPF).getId()).isEqualTo(42L);
        assertThat(repo.buscas).isEqualTo(2);
    }

    @Test
    void propagaAViolacaoQuandoNaoHaPacienteAposACorrida() {
        PacienteRepositorio repo = new PacienteRepositorio() {
            @Override
            public Optional<Paciente> buscarPorCpf(Cpf cpf) {
                return Optional.empty();
            }

            @Override
            public Paciente salvar(Paciente paciente) {
                throw new DataIntegrityViolationException("falha");
            }
        };

        assertThatThrownBy(() -> new ResolverOuCriarPaciente(repo).resolver(CPF))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
