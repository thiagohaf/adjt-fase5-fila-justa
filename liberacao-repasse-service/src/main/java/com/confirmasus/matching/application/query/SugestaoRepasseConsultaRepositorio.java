package com.confirmasus.matching.application.query;

import com.confirmasus.matching.domain.SugestaoRepasse;

import java.util.Optional;
import java.util.UUID;

/** Porta de leitura de {@link SugestaoRepasse} (Story 6.1). */
public interface SugestaoRepasseConsultaRepositorio {

    /** Sugestão {@code PENDENTE} mais recente do Recurso; vazio quando não há. */
    Optional<SugestaoRepasse> buscarPendentePorRecurso(UUID recursoId);

    /** Sugestão mais recente do Recurso em qualquer status; vazio quando nunca houve. */
    Optional<SugestaoRepasse> buscarMaisRecentePorRecurso(UUID recursoId);
}
