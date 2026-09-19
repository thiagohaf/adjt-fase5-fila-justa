package com.filajusta.matching.application.command;

import com.filajusta.matching.application.query.RecursoConsultaRepositorio;
import com.filajusta.matching.application.query.RecursoNaoEncontradoException;
import com.filajusta.matching.domain.EventoOutbox;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso de {@code POST /v1/recursos/{id}/alocacoes/recusa} (Story
 * 3-3c1): registra a recusa de uma Sugestão de Matching para o par {@code
 * (recursoId, pacienteId)} e publica {@code SugestaoRecusada} via outbox --
 * mesmo molde de {@link ConfirmarAlocacao} (Story 3-3b1), sem tocar
 * {@code Alocacao}/tabela {@code alocacao} (Boundaries "Never" da spec
 * 3-3c1): a recusa não usa o domínio de Alocação, só grava o par recusado em
 * {@code sugestao_recusada}.
 *
 * <p>{@code @Transactional} vive aqui (não em {@code domain/},
 * framework-agnóstico), mesma razão de {@link ConfirmarAlocacao}: atomicidade
 * entre as 2 escritas -- {@link SugestaoRecusadaRepositorio#registrar} e
 * {@link EventoOutboxRepositorio#salvar}.
 *
 * <p>{@code correlationId} (mesma validação de {@link ConfirmarAlocacao}):
 * propagado do header {@code X-Correlation-Id} ou gerado localmente (UUID
 * v4) quando ausente/em branco -- nunca gravado nulo no outbox; {@code >
 * 128} caracteres vira {@code 400} via {@link CorrelationIdInvalidoException},
 * validado ANTES de qualquer escrita.
 *
 * <p>{@code recursoId} sem registro lança {@link RecursoNaoEncontradoException}
 * (checado via {@link RecursoConsultaRepositorio#buscarPorId}, mesma porta
 * de leitura usada por {@link ConfirmarAlocacao}) ANTES de qualquer escrita
 * -- propaga sem tratamento para virar {@code 404} em {@code
 * infrastructure/web} (mesmo padrão de {@link ConfirmarAlocacao}).
 *
 * <p>A persistência da recusa ({@link SugestaoRecusadaRepositorio#registrar})
 * é um upsert idempotente pela PK composta {@code (recurso_id, paciente_id)}
 * -- recusar o mesmo par de novo apenas atualiza {@code motivo}/{@code
 * recusado_em}, nunca duplica linha nem falha (Boundaries "Always" da spec
 * 3-3c1) -- ao contrário de {@link ConfirmarAlocacao}, não há checagem de
 * disponibilidade do Recurso nem exceção de conflito.
 *
 * <p>{@link #recusar} devolve o {@code recusadoEm} efetivamente gravado
 * (diferente do Code Map da spec 3-3c1, que sugeria {@code void}): como não
 * existe um agregado de domínio para a recusa (só o par persistido), o
 * {@code infrastructure/web} precisa desse valor para montar o corpo do
 * {@code 201} (Boundaries: corpo mínimo com {@code recusadoEm}) -- sem
 * devolvê-lo, o controller teria que gerar seu próprio {@code Instant} via
 * outra chamada a {@code Clock#instant()}, divergindo do valor realmente
 * persistido por esta transação.
 */
public class RecusarSugestao {

    private static final int VERSAO_INICIAL_EVENTO = 1;

    // eventos_outbox.correlation_id e VARCHAR(128) (migration
    // V4__create_eventos_outbox.sql) -- limite persistivel, mesma constante
    // de ConfirmarAlocacao.
    private static final int CORRELATION_ID_MAX_LENGTH = 128;

    private final SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final Clock clock;

    public RecusarSugestao(SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio,
                            EventoOutboxRepositorio eventoOutboxRepositorio,
                            RecursoConsultaRepositorio recursoConsultaRepositorio,
                            Clock clock) {
        this.sugestaoRecusadaRepositorio = sugestaoRecusadaRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.clock = clock;
    }

    @Transactional
    public Instant recusar(UUID recursoId, long pacienteId, String motivo, String correlationId) {
        String correlationIdEfetivo = correlationIdEfetivo(correlationId);

        recursoConsultaRepositorio.buscarPorId(recursoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(recursoId));

        Instant agora = clock.instant();

        sugestaoRecusadaRepositorio.registrar(recursoId, pacienteId, motivo, agora);

        EventoOutbox evento = new EventoOutbox(
                null, UUID.randomUUID(), "SugestaoRecusada", agora, VERSAO_INICIAL_EVENTO,
                correlationIdEfetivo, payloadSugestaoRecusada(recursoId, pacienteId, motivo, agora));
        eventoOutboxRepositorio.salvar(evento);

        return agora;
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

    private static Map<String, Object> payloadSugestaoRecusada(
            UUID recursoId, long pacienteId, String motivo, Instant recusadoEm) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recursoId", recursoId);
        payload.put("pacienteId", pacienteId);
        payload.put("motivo", motivo);
        payload.put("recusadoEm", recusadoEm);
        return payload;
    }
}
