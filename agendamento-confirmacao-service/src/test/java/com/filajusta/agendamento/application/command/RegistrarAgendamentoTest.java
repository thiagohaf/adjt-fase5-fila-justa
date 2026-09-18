package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.Cpf;
import com.filajusta.agendamento.domain.CpfInvalidoException;
import com.filajusta.agendamento.domain.DataHoraAgendamentoInvalidaException;
import com.filajusta.agendamento.domain.Paciente;
import com.filajusta.agendamento.domain.RecursoIdInvalidoException;
import com.filajusta.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link RegistrarAgendamento} contra a I/O &amp; Edge-Case Matrix da
 * spec 1.1: paciente novo, paciente ja existente (mesmo pacienteId
 * reutilizado), CPF invalido, recursoId malformado/ausente e
 * dataHoraAgendamento no passado/ausente -- nenhum dos 3 cenarios de
 * entrada invalida persiste Paciente nem Agendamento (Boundaries).
 */
class RegistrarAgendamentoTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");
    private static final String CPF_VALIDO = "529.982.247-25";
    private static final String RECURSO_ID_VALIDO = "d290f1ee-6c54-4b01-90e6-d701748f0851";

    private final PacienteRepositorio pacienteRepositorio = mock(PacienteRepositorio.class);
    private final AgendamentoRepositorio agendamentoRepositorio = mock(AgendamentoRepositorio.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final ResolverOuCriarPaciente resolverOuCriarPaciente = new ResolverOuCriarPaciente(pacienteRepositorio);
    private final RegistrarAgendamento registrarAgendamento =
            new RegistrarAgendamento(resolverOuCriarPaciente, agendamentoRepositorio, clock);

    private static Instant dataFutura() {
        return AGORA.plus(Duration.ofDays(1));
    }

    @BeforeEach
    void devolveOAgendamentoPersistidoComIdGerado() {
        when(agendamentoRepositorio.salvar(any())).thenAnswer(invocation -> {
            Agendamento agendamento = invocation.getArgument(0);
            return new Agendamento(99L, agendamento.getPacienteId(), agendamento.getRecursoId(),
                    agendamento.getDataHoraAgendamento(), agendamento.getStatus(), agendamento.getCriadoEm());
        });
    }

    @Test
    void registroFelizComPacienteNovoCriaOPacienteEOAgendamentoEmAguardandoJanela() {
        when(pacienteRepositorio.buscarPorCpf(any())).thenReturn(Optional.empty());
        when(pacienteRepositorio.salvar(any())).thenAnswer(invocation -> {
            Paciente paciente = invocation.getArgument(0);
            return new Paciente(1L, paciente.getCpf());
        });

        Agendamento agendamento = registrarAgendamento.registrar(CPF_VALIDO, RECURSO_ID_VALIDO, dataFutura());

        assertThat(agendamento.getPacienteId()).isEqualTo(1L);
        assertThat(agendamento.getRecursoId()).isEqualTo(UUID.fromString(RECURSO_ID_VALIDO));
        assertThat(agendamento.getDataHoraAgendamento()).isEqualTo(dataFutura());
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGUARDANDO_JANELA);
        assertThat(agendamento.getCriadoEm()).isEqualTo(AGORA);
        verify(pacienteRepositorio).salvar(any());
    }

    @Test
    void registroFelizComPacienteJaExistenteReutilizaOMesmoPacienteIdSemCriarNovoPaciente() {
        Paciente existente = new Paciente(7L, new Cpf(CPF_VALIDO));
        when(pacienteRepositorio.buscarPorCpf(any())).thenReturn(Optional.of(existente));

        Agendamento agendamento = registrarAgendamento.registrar(CPF_VALIDO, RECURSO_ID_VALIDO, dataFutura());

        assertThat(agendamento.getPacienteId()).isEqualTo(7L);
        verify(pacienteRepositorio, never()).salvar(any());
    }

    @Test
    void doisAgendamentosParaOMesmoCpfEmRecursosDiferentesReutilizamOMesmoPacienteId() {
        Paciente existente = new Paciente(7L, new Cpf(CPF_VALIDO));
        when(pacienteRepositorio.buscarPorCpf(any())).thenReturn(Optional.of(existente));
        String outroRecursoId = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

        Agendamento primeiro = registrarAgendamento.registrar(CPF_VALIDO, RECURSO_ID_VALIDO, dataFutura());
        Agendamento segundo = registrarAgendamento.registrar(CPF_VALIDO, outroRecursoId, dataFutura());

        assertThat(primeiro.getPacienteId()).isEqualTo(segundo.getPacienteId());
        assertThat(primeiro.getRecursoId()).isNotEqualTo(segundo.getRecursoId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"111.111.111-11", "123456789", "52998224726"})
    void cpfInvalidoNaoResolvePacienteNemPersisteAgendamento(String cpfInvalido) {
        assertThatThrownBy(() -> registrarAgendamento.registrar(cpfInvalido, RECURSO_ID_VALIDO, dataFutura()))
                .isInstanceOf(CpfInvalidoException.class);

        verifyNoInteractions(pacienteRepositorio, agendamentoRepositorio);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "nao-e-um-uuid", "12345"})
    void recursoIdMalformadoOuAusenteNaoResolvePacienteNemPersisteAgendamento(String recursoIdInvalido) {
        assertThatThrownBy(() -> registrarAgendamento.registrar(CPF_VALIDO, recursoIdInvalido, dataFutura()))
                .isInstanceOf(RecursoIdInvalidoException.class);

        verifyNoInteractions(pacienteRepositorio, agendamentoRepositorio);
    }

    @Test
    void dataHoraAgendamentoAusenteNaoResolvePacienteNemPersisteAgendamento() {
        assertThatThrownBy(() -> registrarAgendamento.registrar(CPF_VALIDO, RECURSO_ID_VALIDO, null))
                .isInstanceOf(DataHoraAgendamentoInvalidaException.class);

        verifyNoInteractions(pacienteRepositorio, agendamentoRepositorio);
    }

    @Test
    void dataHoraAgendamentoNoPassadoNaoResolvePacienteNemPersisteAgendamento() {
        Instant passado = AGORA.minus(Duration.ofDays(1));

        assertThatThrownBy(() -> registrarAgendamento.registrar(CPF_VALIDO, RECURSO_ID_VALIDO, passado))
                .isInstanceOf(DataHoraAgendamentoInvalidaException.class);

        verifyNoInteractions(pacienteRepositorio, agendamentoRepositorio);
    }

    @Test
    void dataHoraAgendamentoIgualAAgoraNaoResolvePacienteNemPersisteAgendamento() {
        // Boundaries da spec 1.1: "> now()" -- igual a agora nao e futuro.
        assertThatThrownBy(() -> registrarAgendamento.registrar(CPF_VALIDO, RECURSO_ID_VALIDO, AGORA))
                .isInstanceOf(DataHoraAgendamentoInvalidaException.class);

        verifyNoInteractions(pacienteRepositorio, agendamentoRepositorio);
    }
}
