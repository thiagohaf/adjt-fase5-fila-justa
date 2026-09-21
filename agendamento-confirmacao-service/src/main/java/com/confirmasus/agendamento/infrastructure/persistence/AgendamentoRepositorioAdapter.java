package com.confirmasus.agendamento.infrastructure.persistence;

import com.confirmasus.agendamento.application.command.AgendamentoRepositorio;
import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.StatusAgendamento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Adapter que implementa a porta {@link AgendamentoRepositorio}
 * (application/command) usando {@link AgendamentoJpaRepository} (Spring
 * Data, schema {@code agendamento_confirmacao}).
 *
 * <p>{@link Clock} injetado (mesmo bean da raiz de composicao, mesmo padrao
 * de {@code EventoOutboxRepositorioAdapter}) -- usado como {@code agora} na
 * query de abertura de janela ({@link #buscarPendentesAberturaJanela(int)}),
 * nunca {@code Instant.now()} direto.
 */
@Component
class AgendamentoRepositorioAdapter implements AgendamentoRepositorio {

    private final AgendamentoJpaRepository jpaRepository;
    private final Clock clock;

    AgendamentoRepositorioAdapter(AgendamentoJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public Agendamento salvar(Agendamento agendamento) {
        AgendamentoJpaEntity entity = new AgendamentoJpaEntity(
                agendamento.getPacienteId(),
                agendamento.getRecursoId(),
                agendamento.getDataHoraAgendamento(),
                agendamento.getStatus(),
                agendamento.getCriadoEm(),
                agendamento.getJanelaAbreEm(),
                agendamento.getJanelaExpiraEm());
        AgendamentoJpaEntity salvo = jpaRepository.save(entity);
        return paraDominio(salvo);
    }

    @Override
    public List<Agendamento> buscarPendentesAberturaJanela(int limite) {
        // FOR UPDATE SKIP LOCKED -- so protege de fato contra corrida entre
        // instancias do poller quando chamado dentro da mesma transacao que
        // tambem executa a escrita condicional (AbrirJanelaDeConfirmacao
        // .abrirJanelas, @Transactional); chamado fora de uma transacao ja
        // aberta, o lock e liberado assim que este metodo retorna.
        return jpaRepository.buscarPendentesAberturaJanela(clock.instant(), limite)
                .stream()
                .map(this::paraDominio)
                .toList();
    }

    @Override
    public List<Agendamento> buscarPendentesExpiracaoJanela(int limite) {
        return jpaRepository.buscarPendentesExpiracaoJanela(clock.instant(), limite)
                .stream()
                .map(this::paraDominio)
                .toList();
    }

    @Override
    @Transactional
    public boolean atualizarStatusSeAtual(Long id, StatusAgendamento statusEsperado, StatusAgendamento novoStatus) {
        return jpaRepository.atualizarStatusSeAtual(id, statusEsperado, novoStatus) > 0;
    }

    @Override
    @Transactional
    public boolean atualizarStatusComMotivo(Long id, StatusAgendamento statusEsperado, StatusAgendamento novoStatus, String motivoLiberacao) {
        return jpaRepository.atualizarStatusComMotivo(id, statusEsperado, novoStatus, motivoLiberacao) > 0;
    }

    @Override
    public Optional<Agendamento> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::paraDominio);
    }

    private Agendamento paraDominio(AgendamentoJpaEntity entity) {
        return new Agendamento(
                entity.getId(),
                entity.getPacienteId(),
                entity.getRecursoId(),
                entity.getDataHoraAgendamento(),
                entity.getStatus(),
                entity.getCriadoEm(),
                entity.getJanelaAbreEm(),
                entity.getJanelaExpiraEm(),
                entity.getMotivoLiberacao());
    }
}
