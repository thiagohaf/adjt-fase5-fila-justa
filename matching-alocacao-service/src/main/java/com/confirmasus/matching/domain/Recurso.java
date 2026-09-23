package com.confirmasus.matching.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Recurso de atendimento (leito, especialista, sala etc., Story 3.2b2) --
 * agregado de capacidade do matching-alocacao-service, upsertado
 * idempotentemente por {@code codigoRecurso} via {@code POST
 * /internal/recursos} ({@code UpsertRecurso}, application/command).
 * Dependência da Story 3-2b3 (sugestão com tiers de {@code
 * especificidadeRank}) e do {@code seed-adapter} do Epic 5 (Intent da spec
 * 3.2b2).
 *
 * <p>{@code recursoId} (UUID v4) é a identidade sintética, estável entre
 * upserts -- {@code infrastructure.persistence} ({@code
 * RecursoJpaRepository#upsert}) nunca sobrescreve esta coluna num conflito
 * por {@code codigoRecurso}, então a mesma instância de domínio pode
 * carregar um {@code recursoId} "candidato" (gerado para o caso de
 * inserção) que acaba descartado pelo banco quando na verdade é uma
 * atualização -- ver javadoc de {@code UpsertRecurso}.
 *
 * <p>{@code especificidadeRank} (inteiro positivo, {@code >= 1}) -- esta
 * story só valida positividade; os valores concretos (1-4) vêm do seed do
 * Epic 5 (Boundaries da spec 3.2b2). {@code disponivel} é obrigatório, sem
 * default implícito -- quem chama sempre declara o estado explicitamente.
 *
 * <p>{@code especialidade} e {@code unidade} (Story 5.1) -- campos de texto
 * que categorizam o recurso por tipo de atendimento e localização física.
 * Opcionais para compatibilidade com dados legados.
 */
public final class Recurso {

    private final UUID recursoId;
    private final String codigoRecurso;
    private final int especificidadeRank;
    private final boolean disponivel;
    private final String especialidade;
    private final String unidade;

    public Recurso(UUID recursoId, String codigoRecurso, int especificidadeRank, boolean disponivel,
                   String especialidade, String unidade) {
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId");
        Objects.requireNonNull(codigoRecurso, "codigoRecurso");
        // Normaliza (trim) antes de persistir/comparar -- upsert eh
        // idempotente por codigoRecurso (invariante Always da spec 3.2b2), e
        // sem isso " LEITO-01 " e "LEITO-01" seriam tratados como codigos
        // diferentes pela UNIQUE(codigo_recurso), quebrando a idempotencia
        // (achado no code review multi-agente da Story 3.2b2).
        this.codigoRecurso = codigoRecurso.trim();
        if (this.codigoRecurso.isEmpty()) {
            throw new IllegalArgumentException("codigoRecurso nao pode ser vazio");
        }
        if (especificidadeRank < 1) {
            throw new IllegalArgumentException(
                    "especificidadeRank deve ser positivo (>= 1): " + especificidadeRank);
        }
        this.especificidadeRank = especificidadeRank;
        this.disponivel = disponivel;
        // Normaliza especialidade e unidade (trim) para preservar consistencia
        // com codigoRecurso (Story 5.1 spec change log)
        this.especialidade = especialidade != null ? especialidade.trim() : null;
        this.unidade = unidade != null ? unidade.trim() : null;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public String getCodigoRecurso() {
        return codigoRecurso;
    }

    public int getEspecificidadeRank() {
        return especificidadeRank;
    }

    public boolean isDisponivel() {
        return disponivel;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public String getUnidade() {
        return unidade;
    }
}
