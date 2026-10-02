package com.confirmasus.matching.application.command;

import com.confirmasus.matching.application.query.RecursoConsultaRepositorio;
import com.confirmasus.matching.application.query.RecursoNaoEncontradoException;
import com.confirmasus.matching.domain.EventoOutbox;
import com.confirmasus.matching.domain.SugestaoRepasse;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reação a {@code VagaLiberada} (Story 6.1, AD-6). Idempotente por
 * {@code agendamentoId} (UNIQUE em {@code sugestao_repasse}): reentrega/redrive
 * de DLQ devolve {@link Resultado#DUPLICADA} sem nenhum efeito.
 *
 * <p>Numa Vaga nova, na mesma transação: grava a {@link SugestaoRepasse}
 * (primeiro candidato FIFO, ou {@code ESGOTADA} se a Lista de Espera não tem
 * candidato elegível -- sem evento, sem repescagem retroativa), libera a
 * Alocação ativa do Recurso e o devolve ao pool, e publica
 * {@code SugestaoRepasseGerada} via outbox quando há candidato. {@code
 * recursoId} desconhecido lança {@link RecursoNaoEncontradoException}: a
 * mensagem não é apagada e segue para a DLQ.
 */
public class GerarSugestaoRepasse {

    public enum Resultado { GERADA, SEM_CANDIDATO, DUPLICADA }

    private final SugestaoRepasseRepositorio sugestaoRepositorio;
    private final SelecionadorCandidatoFifo selecionador;
    private final AlocacaoRepositorio alocacaoRepositorio;
    private final RecursoRepositorio recursoRepositorio;
    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public GerarSugestaoRepasse(SugestaoRepasseRepositorio sugestaoRepositorio,
                                SelecionadorCandidatoFifo selecionador,
                                AlocacaoRepositorio alocacaoRepositorio,
                                RecursoRepositorio recursoRepositorio,
                                RecursoConsultaRepositorio recursoConsultaRepositorio,
                                EventoOutboxRepositorio eventoOutboxRepositorio,
                                Clock clock) {
        this.sugestaoRepositorio = sugestaoRepositorio;
        this.selecionador = selecionador;
        this.alocacaoRepositorio = alocacaoRepositorio;
        this.recursoRepositorio = recursoRepositorio;
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
    public Resultado gerar(long agendamentoId, UUID recursoId, String correlationId) {
        String correlationIdEfetivo = CorrelationIdEfetivo.de(correlationId);

        recursoConsultaRepositorio.buscarPorId(recursoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(recursoId));

        Instant agora = clock.instant();
        Optional<Long> candidato = selecionador.proximo(recursoId, agendamentoId);
        SugestaoRepasse sugestao = SugestaoRepasse.para(
                UUID.randomUUID(), agendamentoId, recursoId, candidato.orElse(null), agora);

        if (!sugestaoRepositorio.criarSeAusente(sugestao)) {
            return Resultado.DUPLICADA;
        }

        alocacaoRepositorio.liberarPorRecurso(recursoId);
        recursoRepositorio.marcarDisponivel(recursoId);

        if (candidato.isEmpty()) {
            return Resultado.SEM_CANDIDATO;
        }
        eventoOutboxRepositorio.salvar(eventoSugestaoGerada(sugestao, correlationIdEfetivo, agora));
        return Resultado.GERADA;
    }

    static EventoOutbox eventoSugestaoGerada(SugestaoRepasse sugestao, String correlationId, Instant agora) {
        return eventoSugestaoGerada(sugestao.getSugestaoId(), sugestao.getAgendamentoId(),
                sugestao.getRecursoId(), sugestao.getPacienteId(), correlationId, agora);
    }

    static EventoOutbox eventoSugestaoGerada(UUID sugestaoId, long agendamentoId, UUID recursoId,
                                             long pacienteId, String correlationId, Instant agora) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sugestaoId", sugestaoId);
        payload.put("agendamentoId", agendamentoId);
        payload.put("recursoId", recursoId);
        payload.put("pacienteId", pacienteId);
        payload.put("sugeridoEm", agora);
        return new EventoOutbox(null, UUID.randomUUID(), "SugestaoRepasseGerada", agora, 1, correlationId, payload);
    }
}
