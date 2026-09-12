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
    public void registrar(UUID recursoId, long pacienteId, Instant registradoEm) {
        jpaRepository.upsert(recursoId, pacienteId, registradoEm);
    }
}
