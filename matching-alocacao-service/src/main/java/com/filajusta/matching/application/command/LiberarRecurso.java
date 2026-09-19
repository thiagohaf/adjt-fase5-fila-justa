package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.Alocacao;
import com.filajusta.matching.domain.EventoOutbox;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comando que realiza a liberação real de um Recurso (Story 3-4b1):
 * transiciona a {@link Alocacao} de {@link Alocacao#STATUS_ATIVA} para
 * {@link Alocacao#STATUS_LIBERADA} e devolve o Recurso ao pool disponível,
 * publicando {@code RecursoLiberado} via outbox -- tudo na mesma transação
 * local (mesmo padrão de {@link ConfirmarAlocacao}).
 *
 * <p>Idempotência por {@code alocacaoId}: {@link AlocacaoRepositorio#liberar}
 * é um UPDATE condicional {@code WHERE status='ATIVA'} que devolve {@code
 * false} quando a Alocação já estava {@code LIBERADA} OU quando {@code
 * alocacaoId} nunca existiu -- os 2 casos são indistinguíveis de propósito
 * (Design Notes da spec 3-4b1). Nesse caso, {@link #liberar} não chama
 * {@link RecursoRepositorio#marcarDisponivel} nem grava evento algum -- é um
 * no-op puro, nunca um erro.
 *
 * <p>{@code recursoId} é recebido do chamador (não relido do banco): ao
 * contrário de {@link ConfirmarAlocacao}, este comando não precisa carregar
 * o Recurso (nenhuma regra depende do seu estado atual) -- quem primeiro
 * confirmou a Alocação (Story 3-3b1) já sabe o {@code recursoId} associado e
 * é quem propaga esse par {@code alocacaoId}/{@code recursoId} para cá (a
 * futura Story 3-4b2, deferida, é quem vai preencher esses 2 valores a
 * partir da mensagem SQS da liberação agendada). {@code payload.recursoId} é
 * o que o relay SNS FIFO já existente usa como {@code MessageGroupId}
 * (Boundaries da spec 3-4b1) -- sem mudança nenhuma no relay.
 *
 * <p>{@link LiberarRecurso} é registrado como {@code @Bean} explícito em
 * {@code MatchingAlocacaoServiceApplication} (mesmo motivo de
 * {@link ConfirmarAlocacao}): {@code @Transactional} só funciona através de
 * um proxy AOP em cima de um bean gerenciado pelo container Spring -- sem
 * isso, as 3 escritas do método {@link #liberar} rodariam cada uma na sua
 * própria transação independente. Ainda assim, sem consumidor real conectado
 * nesta story: nenhum caminho de código real chama este comando (Story
 * 3-4b2, deferida, é quem vai injetar este bean e chamá-lo a partir do
 * consumidor SQS da liberação agendada).
 */
public class LiberarRecurso {

    private static final int VERSAO_INICIAL_EVENTO = 1;

    private final AlocacaoRepositorio alocacaoRepositorio;
    private final RecursoRepositorio recursoRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public LiberarRecurso(AlocacaoRepositorio alocacaoRepositorio,
                           RecursoRepositorio recursoRepositorio,
                           EventoOutboxRepositorio eventoOutboxRepositorio,
                           Clock clock) {
        this.alocacaoRepositorio = alocacaoRepositorio;
        this.recursoRepositorio = recursoRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
    public void liberar(UUID alocacaoId, UUID recursoId, String correlationId) {
        boolean liberou = alocacaoRepositorio.liberar(alocacaoId);
        if (!liberou) {
            return;
        }

        recursoRepositorio.marcarDisponivel(recursoId);

        EventoOutbox evento = new EventoOutbox(
                null, UUID.randomUUID(), "RecursoLiberado", clock.instant(), VERSAO_INICIAL_EVENTO,
                correlationId, payloadRecursoLiberado(recursoId, alocacaoId));
        eventoOutboxRepositorio.salvar(evento);
    }

    private static Map<String, Object> payloadRecursoLiberado(UUID recursoId, UUID alocacaoId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recursoId", recursoId);
        payload.put("alocacaoId", alocacaoId);
        return payload;
    }
}
