package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.command.SugestaoRepasseRepositorio;
import com.confirmasus.matching.application.query.SugestaoRepasseConsultaRepositorio;
import com.confirmasus.matching.domain.SugestaoRepasse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Adapter das portas de {@link SugestaoRepasse} (Story 6.1) sobre {@link SugestaoRepasseJpaRepository}. */
@Component
class SugestaoRepasseRepositorioAdapter implements SugestaoRepasseRepositorio, SugestaoRepasseConsultaRepositorio {

    private final SugestaoRepasseJpaRepository jpaRepository;

    SugestaoRepasseRepositorioAdapter(SugestaoRepasseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public boolean criarSeAusente(SugestaoRepasse s) {
        return jpaRepository.inserirSeAusente(s.getSugestaoId(), s.getAgendamentoId(), s.getRecursoId(),
                s.getPacienteId(), s.getStatus(), s.getCriadoEm()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SugestaoRepasse> buscarPorId(UUID sugestaoId) {
        return jpaRepository.findById(sugestaoId).map(SugestaoRepasseJpaEntity::paraDominio);
    }

    @Override
    @Transactional
    public boolean confirmar(UUID sugestaoId, Instant decididoEm) {
        return jpaRepository.confirmar(sugestaoId, decididoEm) == 1;
    }

    @Override
    @Transactional
    public boolean reatribuir(UUID sugestaoId, long pacienteAtual, Long proximoPacienteId, Instant decididoEm) {
        return jpaRepository.reatribuir(sugestaoId, pacienteAtual, proximoPacienteId, decididoEm) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SugestaoRepasse> buscarPendentePorRecurso(UUID recursoId) {
        return jpaRepository.pendentesPorRecurso(recursoId).stream().findFirst()
                .map(SugestaoRepasseJpaEntity::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SugestaoRepasse> buscarMaisRecentePorRecurso(UUID recursoId) {
        return jpaRepository.maisRecentePorRecurso(recursoId).stream().findFirst()
                .map(SugestaoRepasseJpaEntity::paraDominio);
    }
}
