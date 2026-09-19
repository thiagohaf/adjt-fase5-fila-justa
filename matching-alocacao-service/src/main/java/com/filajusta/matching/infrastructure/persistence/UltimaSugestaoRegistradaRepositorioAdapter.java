package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.UltimaSugestaoRegistradaRepositorio;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link UltimaSugestaoRegistradaRepositorio}
 * (application/command) usando {@link UltimaSugestaoRegistradaJpaRepository}
 * (Spring Data, schema {@code matching_alocacao}). {@link #registrar} delega
 * direto ao upsert nativo ({@link
 * UltimaSugestaoRegistradaJpaRepository#upsert}) -- mesmo padrão de {@code
 * SugestaoRecusadaRepositorioAdapter}: não há releitura pós-insert.
 *
 * <p>Desde a Story 3-3c2b2, {@link #registrar} devolve {@code true}/{@code
 * false} conforme o número de linhas afetadas pelo upsert nativo (0 quando o
 * {@code WHERE} condicional bloqueia a escrita por valor repetido) -- ver
 * Javadoc da porta.
 */
@Component
class UltimaSugestaoRegistradaRepositorioAdapter implements UltimaSugestaoRegistradaRepositorio {

    private final UltimaSugestaoRegistradaJpaRepository jpaRepository;

    UltimaSugestaoRegistradaRepositorioAdapter(UltimaSugestaoRegistradaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> pacienteIdRegistrado(UUID recursoId) {
        return jpaRepository.findById(recursoId).map(UltimaSugestaoRegistradaJpaEntity::getPacienteId);
    }

    @Override
    @Transactional
    public boolean registrar(UUID recursoId, long pacienteId, Instant registradoEm) {
        return jpaRepository.upsert(recursoId, pacienteId, registradoEm) > 0;
    }
}
