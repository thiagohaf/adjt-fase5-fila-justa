package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.SugestaoRecusadaRepositorio;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link SugestaoRecusadaRepositorio}
 * (application/command) usando {@link SugestaoRecusadaJpaRepository} (Spring
 * Data, schema {@code matching_alocacao}). {@link #registrar} delega direto
 * ao upsert nativo ({@link SugestaoRecusadaJpaRepository#upsert}) -- ao
 * contrário de {@code RecursoRepositorioAdapter#upsert}, não há releitura
 * pós-insert: não existe {@code recursoId}/PK gerado pela aplicação para
 * devolver, {@code RecusarSugestao} (application/command) já tem tudo que
 * precisa (o próprio {@code recursoId}/{@code pacienteId}/{@code motivo}
 * recebidos) para montar o payload do evento e a resposta HTTP.
 */
@Component
class SugestaoRecusadaRepositorioAdapter implements SugestaoRecusadaRepositorio {

    private final SugestaoRecusadaJpaRepository jpaRepository;

    SugestaoRecusadaRepositorioAdapter(SugestaoRecusadaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void registrar(UUID recursoId, long pacienteId, String motivo, Instant recusadoEm) {
        jpaRepository.upsert(recursoId, pacienteId, motivo, recusadoEm);
    }
}
