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
     * Passa a Alocação {@code ATIVA} do Recurso (se houver) para {@code
     * LIBERADA} -- chamado quando a Vaga é liberada (Story 6.1).
     * Idempotente: sem Alocação ativa, não altera nada.
     */
    void liberarPorRecurso(UUID recursoId);
}
