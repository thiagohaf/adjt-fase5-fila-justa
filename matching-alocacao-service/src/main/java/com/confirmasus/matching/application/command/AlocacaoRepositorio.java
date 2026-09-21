package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.Alocacao;

import java.util.UUID;

/**
 * Porta de saída para persistência de {@link Alocacao} (Story 3-3b1).
 * Implementada em {@code infrastructure.persistence} (JPA nativo, schema
 * {@code matching_alocacao}, tabela {@code alocacao}).
 *
 * <p>{@link #confirmar(Alocacao)} insere a linha; os 2 índices únicos
 * parciais do banco (não checagem em memória) decidem se a confirmação é
 * aceita -- uma violação vira {@link RecursoJaAlocadoException} ou
 * {@link PacienteJaAlocadoException} conforme a constraint violada
 * (Boundaries da spec 3-3b1).
 */
public interface AlocacaoRepositorio {

    Alocacao confirmar(Alocacao alocacao);

    /**
     * Transiciona a Alocação {@code alocacaoId} de {@code ATIVA} para
     * {@link Alocacao#STATUS_LIBERADA} (Story 3-4b1, {@code LiberarRecurso}) --
     * UPDATE condicional {@code WHERE status='ATIVA'}, idempotente por
     * natureza: devolve {@code true} quando a transição foi de fato aplicada
     * (exatamente 1 linha afetada) e {@code false} quando 0 linhas foram
     * afetadas -- Alocação já {@code LIBERADA} OU {@code alocacaoId} nunca
     * existiu, os 2 casos são indistinguíveis de propósito (Design Notes da
     * spec 3-4b1). Sem lock otimista: sob concorrência real, o próprio
     * UPDATE condicional do banco garante que só 1 chamada simultânea recebe
     * {@code true}.
     */
    boolean liberar(UUID alocacaoId);
}
