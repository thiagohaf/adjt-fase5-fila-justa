package com.confirmasus.matching.application.command;

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
 * Gestor recusa a Sugestão de Repasse (Story 6.1, AD-6/FR-11): grava o par
 * recusado ({@code sugestao_recusada}), reatribui a sugestão ao próximo
 * candidato FIFO (ou a marca {@code ESGOTADA}) numa escrita condicional
 * {@code WHERE status = 'PENDENTE'} -- a perdedora de uma corrida recebe
 * {@link SugestaoRepasseJaDecididaException} ({@code 409}) e a transação
 * inteira é revertida. Publica {@code SugestaoRepasseRecusada} e, havendo
 * próximo candidato, {@code SugestaoRepasseGerada}.
 */
public class RecusarSugestaoRepasse {

    public record Resultado(UUID sugestaoId, Long proximoPacienteId, Instant recusadoEm) {
    }

    private final SugestaoRepasseRepositorio sugestaoRepositorio;
    private final SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio;
    private final SelecionadorCandidatoFifo selecionador;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public RecusarSugestaoRepasse(SugestaoRepasseRepositorio sugestaoRepositorio,
                                  SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio,
                                  SelecionadorCandidatoFifo selecionador,
                                  EventoOutboxRepositorio eventoOutboxRepositorio,
                                  Clock clock) {
        this.sugestaoRepositorio = sugestaoRepositorio;
        this.sugestaoRecusadaRepositorio = sugestaoRecusadaRepositorio;
        this.selecionador = selecionador;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
    public Resultado recusar(UUID sugestaoId, String motivo, String correlationId) {
        String correlationIdEfetivo = CorrelationIdEfetivo.de(correlationId);

        SugestaoRepasse sugestao = sugestaoRepositorio.buscarPorId(sugestaoId)
                .orElseThrow(() -> new SugestaoRepasseNaoEncontradaException(sugestaoId));
        if (!SugestaoRepasse.STATUS_PENDENTE.equals(sugestao.getStatus())) {
            throw new SugestaoRepasseJaDecididaException(sugestaoId);
        }

        UUID recursoId = sugestao.getRecursoId();
        long recusado = sugestao.getPacienteId();
        Instant agora = clock.instant();

        // Registrar antes de selecionar: o recusado deixa de ser elegivel.
        sugestaoRecusadaRepositorio.registrar(recursoId, sugestao.getAgendamentoId(), recusado, motivo, agora);
        Optional<Long> proximo = selecionador.proximo(recursoId, sugestao.getAgendamentoId());

        if (!sugestaoRepositorio.reatribuir(sugestaoId, recusado, proximo.orElse(null), agora)) {
            throw new SugestaoRepasseJaDecididaException(sugestaoId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sugestaoId", sugestaoId);
        payload.put("agendamentoId", sugestao.getAgendamentoId());
        payload.put("recursoId", recursoId);
        payload.put("pacienteId", recusado);
        payload.put("motivo", motivo);
        payload.put("recusadoEm", agora);
        eventoOutboxRepositorio.salvar(new EventoOutbox(
                null, UUID.randomUUID(), "SugestaoRepasseRecusada", agora, 1, correlationIdEfetivo, payload));

        proximo.ifPresent(p -> eventoOutboxRepositorio.salvar(GerarSugestaoRepasse.eventoSugestaoGerada(
                sugestaoId, sugestao.getAgendamentoId(), recursoId, p, correlationIdEfetivo, agora)));

        return new Resultado(sugestaoId, proximo.orElse(null), agora);
    }
}
