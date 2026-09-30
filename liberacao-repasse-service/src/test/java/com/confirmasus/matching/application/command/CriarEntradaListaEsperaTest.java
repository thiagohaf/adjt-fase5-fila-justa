package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.Cpf;
import com.confirmasus.matching.domain.CpfInvalidoException;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import com.confirmasus.matching.domain.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriarEntradaListaEsperaTest {

    private static final Instant AGORA = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant SOLICITADO = Instant.parse("2026-09-10T12:00:00Z");
    private static final String CPF = "52998224725";
    private static final UUID RECURSO = UUID.randomUUID();

    private static class FakePacientes implements PacienteRepositorio {
        @Override
        public Optional<Paciente> buscarPorCpf(Cpf cpf) {
            return Optional.of(new Paciente(10L, cpf));
        }

        @Override
        public Paciente salvar(Paciente paciente) {
            return paciente;
        }
    }

    private static class FakeLista implements ListaEsperaEntradaRepositorio {
        final List<ListaEsperaEntrada> salvas = new ArrayList<>();
        boolean jaExiste;
        boolean violacaoNoSalvar;
        boolean existeAposViolacao;
        int buscas;

        @Override
        public Optional<ListaEsperaEntrada> buscarPorPacienteIdERecursoId(Long pacienteId, UUID recursoId) {
            buscas++;
            boolean existe = buscas == 1 ? jaExiste : existeAposViolacao;
            return existe ? Optional.of(ListaEsperaEntrada.nova(pacienteId, recursoId, SOLICITADO, AGORA))
                    : Optional.empty();
        }

        @Override
        public ListaEsperaEntrada salvar(ListaEsperaEntrada entrada) {
            if (violacaoNoSalvar) {
                throw new DataIntegrityViolationException("uk");
            }
            salvas.add(entrada);
            return entrada;
        }
    }

    private final FakeLista lista = new FakeLista();
    private final CriarEntradaListaEspera criar = new CriarEntradaListaEspera(
            new ResolverOuCriarPaciente(new FakePacientes()), lista, Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    void criaEntradaComTimestampDeChegadaEDataDoRelogio() {
        ListaEsperaEntrada entrada = criar.criar(CPF, RECURSO.toString(), SOLICITADO);

        assertThat(entrada.getPacienteId()).isEqualTo(10L);
        assertThat(entrada.getRecursoId()).isEqualTo(RECURSO);
        assertThat(entrada.getDataSolicitacao()).isEqualTo(SOLICITADO);
        assertThat(entrada.getCriadoEm()).isEqualTo(AGORA);
        assertThat(lista.salvas).hasSize(1);
    }

    @Test
    void aceitaDataSolicitacaoIgualAoInstanteAtual() {
        assertThat(criar.criar(CPF, RECURSO.toString(), AGORA).getDataSolicitacao()).isEqualTo(AGORA);
    }

    @Test
    void rejeitaEntradaDuplicada() {
        lista.jaExiste = true;

        assertThatThrownBy(() -> criar.criar(CPF, RECURSO.toString(), SOLICITADO))
                .isInstanceOf(EntradaJaExisteException.class);
        assertThat(lista.salvas).isEmpty();
    }

    @Test
    void converteViolacaoDeUnicidadeEmEntradaJaExiste() {
        lista.violacaoNoSalvar = true;
        lista.existeAposViolacao = true;

        assertThatThrownBy(() -> criar.criar(CPF, RECURSO.toString(), SOLICITADO))
                .isInstanceOf(EntradaJaExisteException.class);
    }

    @Test
    void propagaViolacaoQuandoNaoForCorridaDeUnicidade() {
        lista.violacaoNoSalvar = true;

        assertThatThrownBy(() -> criar.criar(CPF, RECURSO.toString(), SOLICITADO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validaCpfRecursoEData() {
        assertThatThrownBy(() -> criar.criar("123", RECURSO.toString(), SOLICITADO))
                .isInstanceOf(CpfInvalidoException.class);
        assertThatThrownBy(() -> criar.criar(CPF, null, SOLICITADO)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatório");
        assertThatThrownBy(() -> criar.criar(CPF, " ", SOLICITADO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criar.criar(CPF, "nao-e-uuid", SOLICITADO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UUID");
        assertThatThrownBy(() -> criar.criar(CPF, RECURSO.toString(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obrigatória");
        assertThatThrownBy(() -> criar.criar(CPF, RECURSO.toString(), AGORA.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("passado");
    }
}
