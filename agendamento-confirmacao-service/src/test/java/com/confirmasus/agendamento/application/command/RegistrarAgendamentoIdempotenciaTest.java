package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Idempotência de {@link RegistrarAgendamento}: agendamento já existente e corrida de inserção (TOCTOU). */
class RegistrarAgendamentoIdempotenciaTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");
    private static final String CPF = "529.982.247-25";
    private static final UUID RECURSO = UUID.fromString("d290f1ee-6c54-4b01-90e6-d701748f0851");

    private final PacienteRepositorio pacientes = mock(PacienteRepositorio.class);
    private final AgendamentoRepositorio agendamentos = mock(AgendamentoRepositorio.class);
    private final RegistrarAgendamento registrar = new RegistrarAgendamento(
            new ResolverOuCriarPaciente(pacientes), agendamentos,
            Clock.fixed(AGORA, ZoneOffset.UTC), Duration.ofMinutes(30));

    private Agendamento existente() {
        return Agendamento.novo(1L, RECURSO, AGORA.plusSeconds(86_400), AGORA, AGORA.plusSeconds(1800));
    }

    private void pacienteJaExiste() {
        when(pacientes.buscarPorCpf(any())).thenAnswer(inv ->
                Optional.of(new Paciente(1L, inv.getArgument(0))));
    }

    @Test
    void devolveOAgendamentoExistenteSemCriarOutro() {
        pacienteJaExiste();
        Agendamento existente = existente();
        when(agendamentos.buscarPorPacienteIdERecursoId(eq(1L), eq(RECURSO))).thenReturn(Optional.of(existente));

        assertThat(registrar.registrar(CPF, RECURSO.toString(), AGORA.plusSeconds(86_400))).isSameAs(existente);
        verify(agendamentos, never()).salvar(any());
    }

    @Test
    void aposCorridaDeInsercaoDevolveOAgendamentoVencedor() {
        pacienteJaExiste();
        Agendamento vencedor = existente();
        when(agendamentos.buscarPorPacienteIdERecursoId(eq(1L), eq(RECURSO)))
                .thenReturn(Optional.empty(), Optional.of(vencedor));
        when(agendamentos.salvar(any())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThat(registrar.registrar(CPF, RECURSO.toString(), AGORA.plusSeconds(86_400))).isSameAs(vencedor);
    }

    @Test
    void propagaAViolacaoQuandoNaoHaAgendamentoAposACorrida() {
        pacienteJaExiste();
        when(agendamentos.buscarPorPacienteIdERecursoId(eq(1L), eq(RECURSO))).thenReturn(Optional.empty());
        when(agendamentos.salvar(any())).thenThrow(new DataIntegrityViolationException("outra violacao"));

        assertThatThrownBy(() -> registrar.registrar(CPF, RECURSO.toString(), AGORA.plusSeconds(86_400)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
