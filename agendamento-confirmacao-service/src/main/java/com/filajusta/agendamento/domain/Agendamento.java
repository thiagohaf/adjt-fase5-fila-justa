package com.filajusta.agendamento.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Agendamento (Story 1.1): associa um {@link Paciente} (resolvido
 * por CPF, {@code application.command.ResolverOuCriarPaciente}) a um
 * {@code recursoId} (validado so por formato -- AD-1, sem checagem de
 * existencia contra o catalogo real de Recurso) e uma
 * {@code dataHoraAgendamento} futura. Nasce sempre em
 * {@link StatusAgendamento#AGUARDANDO_JANELA} -- as demais transicoes de
 * estado sao adicionadas pelas proximas stories do Epic 1.
 *
 * <p>{@code id} e {@code null} antes da primeira persistencia (mesmo padrao
 * de {@link Paciente}).
 */
public final class Agendamento {

    private final Long id;
    private final Long pacienteId;
    private final UUID recursoId;
    private final Instant dataHoraAgendamento;
    private final StatusAgendamento status;
    private final Instant criadoEm;

    public Agendamento(Long id, Long pacienteId, UUID recursoId, Instant dataHoraAgendamento,
                        StatusAgendamento status, Instant criadoEm) {
        this.id = id;
        this.pacienteId = Objects.requireNonNull(pacienteId, "pacienteId");
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId");
        this.dataHoraAgendamento = Objects.requireNonNull(dataHoraAgendamento, "dataHoraAgendamento");
        this.status = Objects.requireNonNull(status, "status");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
    }

    /**
     * Fabrica um novo Agendamento ainda nao persistido (Boundaries da spec
     * 1.1: estado inicial sempre {@code AGUARDANDO_JANELA}).
     */
    public static Agendamento novo(Long pacienteId, UUID recursoId, Instant dataHoraAgendamento, Instant agora) {
        return new Agendamento(null, pacienteId, recursoId, dataHoraAgendamento,
                StatusAgendamento.AGUARDANDO_JANELA, agora);
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public Instant getDataHoraAgendamento() {
        return dataHoraAgendamento;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
