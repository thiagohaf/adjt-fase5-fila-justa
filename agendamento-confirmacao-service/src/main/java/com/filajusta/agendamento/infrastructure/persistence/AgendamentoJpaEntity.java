package com.filajusta.agendamento.infrastructure.persistence;

import com.filajusta.agendamento.domain.StatusAgendamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code agendamento_confirmacao.agendamentos} (schema
 * proprio, AD-9). {@code status} mapeado por nome ({@link EnumType#STRING})
 * -- nunca por posicao ordinal, para que reordenar
 * {@link StatusAgendamento} no futuro nao corrompa dados ja persistidos.
 */
@Entity
@Table(name = "agendamentos", schema = "agendamento_confirmacao")
public class AgendamentoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "recurso_id", nullable = false)
    private UUID recursoId;

    @Column(name = "data_hora_agendamento", nullable = false)
    private Instant dataHoraAgendamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StatusAgendamento status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AgendamentoJpaEntity() {
        // Exigido pelo JPA.
    }

    public AgendamentoJpaEntity(Long pacienteId, UUID recursoId, Instant dataHoraAgendamento,
                                 StatusAgendamento status, Instant criadoEm) {
        this.pacienteId = pacienteId;
        this.recursoId = recursoId;
        this.dataHoraAgendamento = dataHoraAgendamento;
        this.status = status;
        this.criadoEm = criadoEm;
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
