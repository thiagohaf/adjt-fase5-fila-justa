package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.domain.SugestaoRepasse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento JPA de {@code matching_alocacao.sugestao_repasse} (Story 6.1).
 * Escritas passam pelas queries nativas de {@link SugestaoRepasseJpaRepository}
 * (escritas condicionais), nunca por {@code save()}.
 */
@Entity
@Table(name = "sugestao_repasse", schema = "matching_alocacao")
public class SugestaoRepasseJpaEntity {

    @Id
    @Column(name = "sugestao_id")
    private UUID sugestaoId;

    @Column(name = "agendamento_id", nullable = false)
    private long agendamentoId;

    @Column(name = "recurso_id", nullable = false)
    private UUID recursoId;

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(nullable = false)
    private String status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    protected SugestaoRepasseJpaEntity() {
        // Exigido pelo JPA.
    }

    SugestaoRepasse paraDominio() {
        return new SugestaoRepasse(sugestaoId, agendamentoId, recursoId, pacienteId, status, criadoEm);
    }
}
