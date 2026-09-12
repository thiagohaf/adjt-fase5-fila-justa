package com.filajusta.matching.application.command;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta de saída para persistência do par recusado {@code (recursoId,
 * pacienteId)} (Story 3-3c1). Implementada em {@code
 * infrastructure.persistence} (JPA nativo, schema {@code matching_alocacao},
 * tabela {@code sugestao_recusada}).
 *
 * <p>{@link #registrar(UUID, long, String, Instant)} é upsert idempotente
 * pela PK composta {@code (recurso_id, paciente_id)} -- ao contrário de
 * {@link AlocacaoRepositorio#confirmar}, não há índice único parcial nem
 * exceção de conflito: recusar o mesmo par de novo apenas atualiza {@code
 * motivo}/{@code recusado_em} (Boundaries da spec 3-3c1), nunca duplica
 * linha nem falha.
 */
public interface SugestaoRecusadaRepositorio {

    void registrar(UUID recursoId, long pacienteId, String motivo, Instant recusadoEm);
}
