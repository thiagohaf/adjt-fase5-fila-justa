package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.Alocacao;
import com.confirmasus.matching.domain.EventoOutbox;
import com.confirmasus.matching.domain.SugestaoRepasse;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestor confirma a Sugestão de Repasse (Story 6.1, AD-6/FR-10): escrita
 * condicional {@code WHERE status = 'PENDENTE'} -- a perdedora de uma corrida
 * recebe {@link SugestaoRepasseJaDecididaException} ({@code 409}). Na mesma
 * transação: cria a {@link Alocacao} ATIVA (o Repasse Confirmado, definitivo),
 * marca o Recurso indisponível e publica {@code RepasseConfirmado} via outbox.
 * Se o paciente/recurso já tiver Alocação ativa (índices únicos de V5), tudo
 * é revertido e a sugestão continua {@code PENDENTE}.
 */
public class ConfirmarRepasse {

    private final SugestaoRepasseRepositorio sugestaoRepositorio;
    private final AlocacaoRepositorio alocacaoRepositorio;
    private final RecursoRepositorio recursoRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public ConfirmarRepasse(SugestaoRepasseRepositorio sugestaoRepositorio,
                            AlocacaoRepositorio alocacaoRepositorio,
                            RecursoRepositorio recursoRepositorio,
                            EventoOutboxRepositorio eventoOutboxRepositorio,
                            Clock clock) {
        this.sugestaoRepositorio = sugestaoRepositorio;
        this.alocacaoRepositorio = alocacaoRepositorio;
        this.recursoRepositorio = recursoRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
    public Alocacao confirmar(UUID sugestaoId, String correlationId) {
        String correlationIdEfetivo = CorrelationIdEfetivo.de(correlationId);

        SugestaoRepasse sugestao = sugestaoRepositorio.buscarPorId(sugestaoId)
                .orElseThrow(() -> new SugestaoRepasseNaoEncontradaException(sugestaoId));
        Instant agora = clock.instant();

        if (!SugestaoRepasse.STATUS_PENDENTE.equals(sugestao.getStatus())
                || !sugestaoRepositorio.confirmar(sugestaoId, agora)) {
            throw new SugestaoRepasseJaDecididaException(sugestaoId);
        }

        Alocacao confirmada = alocacaoRepositorio.confirmar(new Alocacao(
                UUID.randomUUID(), sugestao.getRecursoId(), sugestao.getPacienteId(), Alocacao.STATUS_ATIVA, agora));
        recursoRepositorio.marcarIndisponivel(sugestao.getRecursoId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alocacaoId", confirmada.getAlocacaoId());
        payload.put("sugestaoId", sugestaoId);
        payload.put("agendamentoId", sugestao.getAgendamentoId());
        payload.put("recursoId", confirmada.getRecursoId());
        payload.put("pacienteId", confirmada.getPacienteId());
        payload.put("confirmadoEm", agora);
        eventoOutboxRepositorio.salvar(new EventoOutbox(
                null, UUID.randomUUID(), "RepasseConfirmado", agora, 1, correlationIdEfetivo, payload));

        return confirmada;
    }
}
