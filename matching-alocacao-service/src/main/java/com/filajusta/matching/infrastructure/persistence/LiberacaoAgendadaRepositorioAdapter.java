package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.LiberacaoAgendadaRepositorio;
import com.filajusta.matching.domain.LiberacaoAgendada;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Adapter que implementa a porta {@link LiberacaoAgendadaRepositorio}
 * (application/command) usando {@link LiberacaoAgendadaJpaRepository}
 * (Spring Data, schema {@code matching_alocacao}, tabela
 * {@code liberacao_agendada}) -- mesmo padrão de
 * {@code EventoOutboxRepositorioAdapter} (Story 3-3a).
 *
 * <p>{@link Clock} injetado (mesmo bean da raiz de composição) em vez de
 * {@code Instant.now()} direto -- consistente com o resto do serviço e
 * testável em {@link #marcarComoEnviado(UUID)}.
 *
 * <p>Registrado automaticamente via component scan (mesmo padrão de todos
 * os demais adapters deste serviço) -- não precisa de um {@code @Bean}
 * explícito em {@code MatchingAlocacaoServiceApplication}.
 */
@Component
class LiberacaoAgendadaRepositorioAdapter implements LiberacaoAgendadaRepositorio {

    private final LiberacaoAgendadaJpaRepository jpaRepository;
    private final Clock clock;

    LiberacaoAgendadaRepositorioAdapter(LiberacaoAgendadaJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public void salvar(LiberacaoAgendada liberacaoAgendada) {
        jpaRepository.save(new LiberacaoAgendadaJpaEntity(
                liberacaoAgendada.getAlocacaoId(), liberacaoAgendada.getRecursoId(),
                liberacaoAgendada.getCorrelationId(), liberacaoAgendada.getDelaySegundos(),
                liberacaoAgendada.getCriadoEm(), liberacaoAgendada.getEnviadoEm()));
    }

    @Override
    public List<LiberacaoAgendada> buscarPendentes(int limite) {
        // FOR UPDATE SKIP LOCKED -- so protege de fato contra corrida entre
        // instancias do futuro relay (Story 3-4a2) quando chamado dentro da
        // mesma transacao que tambem marca como enviada logo em seguida;
        // chamado fora de uma transacao ja aberta, o lock e liberado assim
        // que este metodo retorna.
        return jpaRepository.buscarPendentesParaAtualizar(limite)
                .stream()
                .map(this::paraDominio)
                .toList();
    }

    @Override
    @Transactional
    public boolean marcarComoEnviado(UUID alocacaoId) {
        return jpaRepository.marcarEnviado(alocacaoId, clock.instant()) > 0;
    }

    private LiberacaoAgendada paraDominio(LiberacaoAgendadaJpaEntity entity) {
        return new LiberacaoAgendada(entity.getAlocacaoId(), entity.getRecursoId(), entity.getCorrelationId(),
                entity.getDelaySegundos(), entity.getCriadoEm(), entity.getEnviadoEm());
    }
}
