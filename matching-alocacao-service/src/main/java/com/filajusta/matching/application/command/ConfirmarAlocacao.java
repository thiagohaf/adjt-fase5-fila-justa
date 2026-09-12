package com.filajusta.matching.application.command;

import com.filajusta.matching.application.query.RecursoConsultaRepositorio;
import com.filajusta.matching.application.query.RecursoNaoEncontradoException;
import com.filajusta.matching.domain.Alocacao;
import com.filajusta.matching.domain.EventoOutbox;
import com.filajusta.matching.domain.Recurso;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso de {@code POST /v1/recursos/{id}/alocacoes} (Story 3-3b1):
 * confirma a Sugestão de Matching para um Recurso, criando uma
 * {@link Alocacao}, marcando o Recurso indisponível e publicando
 * {@code AlocacaoConfirmada} via outbox -- tudo na mesma transação local
 * (Boundaries da spec: mesmo padrão de {@code RegistrarTriagem},
 * triagem-score-service).
 *
 * <p>{@code @Transactional} vive aqui (não em {@code domain/}, framework-
 * agnóstico) porque este é o único ponto que precisa da atomicidade entre
 * as 3 escritas: {@link AlocacaoRepositorio#confirmar} (índice único decide
 * {@code 409}), {@link RecursoRepositorio#marcarIndisponivel} e
 * {@link EventoOutboxRepositorio#salvar}.
 *
 * <p>{@code correlationId} (mesma validação de {@code RegistrarTriagem}):
 * propagado do header {@code X-Correlation-Id} ou gerado localmente (UUID
 * v4) quando ausente/em branco -- nunca gravado nulo no outbox;
 * {@code > 128} caracteres vira {@code 400} via
 * {@link CorrelationIdInvalidoException}, validado ANTES de qualquer
 * escrita.
 *
 * <p>{@code recursoId} sem registro lança {@link RecursoNaoEncontradoException}
 * (checado via {@link RecursoConsultaRepositorio#buscarPorId}, mesma porta
 * de leitura usada por {@code ConsultarSugestaoRecurso}) ANTES de qualquer
 * escrita -- propaga sem tratamento para virar {@code 404} em
 * {@code infrastructure/web} (mesmo padrão de
 * {@code ConsultarSugestaoRecurso}). Um Recurso {@code disponivel=false}
 * (achado do code review multi-agente: um Recurso pode ficar indisponível
 * sem nenhuma {@code Alocacao} ativa, ex. via {@code POST /internal/recursos})
 * lança {@link RecursoJaAlocadoException} -- os 2 índices únicos parciais só
 * protegem contra uma segunda Alocação ativa, nunca contra confirmar um
 * Recurso já marcado indisponível por outro caminho; mesmo tratamento
 * "nunca elegível" de {@code ConsultarSugestaoRecurso}.
 */
public class ConfirmarAlocacao {

    private static final int VERSAO_INICIAL_EVENTO = 1;

    // eventos_outbox.correlation_id e VARCHAR(128) (migration
    // V4__create_eventos_outbox.sql) -- limite persistivel.
    private static final int CORRELATION_ID_MAX_LENGTH = 128;

    private final AlocacaoRepositorio alocacaoRepositorio;
    private final RecursoRepositorio recursoRepositorio;
    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public ConfirmarAlocacao(AlocacaoRepositorio alocacaoRepositorio,
                              RecursoRepositorio recursoRepositorio,
                              RecursoConsultaRepositorio recursoConsultaRepositorio,
                              EventoOutboxRepositorio eventoOutboxRepositorio,
                              Clock clock) {
        this.alocacaoRepositorio = alocacaoRepositorio;
        this.recursoRepositorio = recursoRepositorio;
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
    public Alocacao confirmar(UUID recursoId, long pacienteId, String correlationId) {
        String correlationIdEfetivo = correlationIdEfetivo(correlationId);

        Recurso recurso = recursoConsultaRepositorio.buscarPorId(recursoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(recursoId));
        if (!recurso.isDisponivel()) {
            throw new RecursoJaAlocadoException(recursoId);
        }

        Instant agora = clock.instant();
        Alocacao candidata = new Alocacao(
                UUID.randomUUID(), recursoId, pacienteId, Alocacao.STATUS_ATIVA, agora);

        Alocacao confirmada = alocacaoRepositorio.confirmar(candidata);

        recursoRepositorio.marcarIndisponivel(recursoId);

        EventoOutbox evento = new EventoOutbox(
                null, UUID.randomUUID(), "AlocacaoConfirmada", agora, VERSAO_INICIAL_EVENTO,
                correlationIdEfetivo, payloadAlocacaoConfirmada(confirmada));
        eventoOutboxRepositorio.salvar(evento);

        return confirmada;
    }

    private static String correlationIdEfetivo(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (correlationId.length() > CORRELATION_ID_MAX_LENGTH) {
            throw new CorrelationIdInvalidoException(
                    "correlationId excede o limite de " + CORRELATION_ID_MAX_LENGTH + " caracteres");
        }
        return correlationId;
    }

    private static Map<String, Object> payloadAlocacaoConfirmada(Alocacao alocacao) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alocacaoId", alocacao.getAlocacaoId());
        payload.put("recursoId", alocacao.getRecursoId());
        payload.put("pacienteId", alocacao.getPacienteId());
        payload.put("confirmadoEm", alocacao.getConfirmadoEm());
        return payload;
    }
}
