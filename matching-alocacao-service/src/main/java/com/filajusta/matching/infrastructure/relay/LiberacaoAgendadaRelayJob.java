package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.LiberacaoAgendadaRepositorio;
import com.filajusta.matching.domain.LiberacaoAgendada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relay que publica a mensagem de delay de {@code liberacao_agendada} (Story
 * 3-4a1) na fila SQS standard {@code liberacao-agendada} (declarada em
 * {@code infra-cdk}) -- Story 3-4a2, molde de {@link RelaySnsPublisherJob}
 * (Story 3-3a): poller {@code @Scheduled} que le linhas pendentes via
 * {@link LiberacaoAgendadaRepositorio#buscarPendentes}, publica cada uma e so
 * marca a linha como enviada
 * ({@link LiberacaoAgendadaRepositorio#marcarComoEnviado}) apos o ack do SQS
 * -- nunca antes. Nenhum consumidor real le esta fila ainda (a liberacao real
 * do Recurso e a Story 3-4b, deferida) -- este relay e so o lado da
 * publicacao, fechando a lacuna de concorrencia apontada no code review da
 * Story 3-4a1 ({@code buscarPendentes} nunca era chamado).
 *
 * <p>Fila STANDARD (nao FIFO, diferente de {@link RelaySnsPublisherJob}): a
 * ordem de liberacao entre Recursos diferentes nao importa (Boundaries da
 * spec 3-4a2), entao a logica de bloqueio por {@code MessageGroupId} de
 * {@link RelaySnsPublisherJob} nao se aplica aqui -- a falha de UM item so
 * pula esse item (log + segue para o proximo do lote), nunca aborta o lote
 * nem bloqueia itens seguintes de qualquer Recurso.
 *
 * <p>{@code DelaySeconds = liberacao.getDelaySegundos()} em cada
 * {@code sendMessage} -- e o proprio mecanismo de "liberacao agendada": a
 * mensagem so fica visivel na fila apos esse tempo, disparando a futura
 * liberacao real do Recurso (3-4b). Corpo da mensagem: {@code alocacaoId},
 * {@code recursoId}, {@code correlationId} (para rastreio) -- Boundaries da
 * spec 3-4a2, nenhum outro campo do dominio.
 *
 * <p>{@code @Transactional} (mesmo raciocinio de
 * {@link RelaySnsPublisherJob}): sem isso, {@code buscarPendentes} e
 * {@code marcarComoEnviado} rodariam em transacoes separadas e o lock de
 * linha ({@code SELECT ... FOR UPDATE SKIP LOCKED}, ver
 * {@code LiberacaoAgendadaJpaRepository}) seria liberado assim que a leitura
 * retornasse -- antes do ack do SQS -- permitindo que duas instancias deste
 * job lessem e publicassem a MESMA linha. Com o metodo inteiro numa unica
 * transacao, a segunda instancia que rodar {@code SKIP LOCKED}
 * concorrentemente pula as linhas que esta instancia ja esta processando.
 *
 * <p>{@code @ConditionalOnProperty} -- mesmo kill switch de
 * {@link LiberacaoAgendadaSqsClientConfig}
 * ({@code filajusta.matching.liberacao-agendada-relay.enabled}, SEM
 * {@code matchIfMissing} -- default {@code false}, mesmo padrao de
 * {@code outbox-relay}); namespace isolado de {@code relay.*}/
 * {@code outbox-relay.*} para nao colidir os dois pollers/beans
 * {@link SqsClient} independentes (Boundaries da spec 3-4a2).
 */
@Component
@ConditionalOnProperty(prefix = "filajusta.matching.liberacao-agendada-relay", name = "enabled")
class LiberacaoAgendadaRelayJob {

    private static final Logger log = LoggerFactory.getLogger(LiberacaoAgendadaRelayJob.class);

    private final LiberacaoAgendadaRepositorio liberacaoAgendadaRepositorio;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final int loteTamanho;

    LiberacaoAgendadaRelayJob(LiberacaoAgendadaRepositorio liberacaoAgendadaRepositorio,
                               @Qualifier("liberacaoAgendadaSqsClient") SqsClient sqsClient,
                               ObjectMapper objectMapper,
                               @Value("${filajusta.matching.liberacao-agendada-relay.queue-url}") String queueUrl,
                               @Value("${filajusta.matching.liberacao-agendada-relay.batch-size:50}") int loteTamanho) {
        // Fail-fast (mesmo raciocinio de RelaySnsPublisherJob/
        // ScoreCalculadoConsumerJob): com o relay habilitado (unico caso em
        // que este bean e criado, ver @ConditionalOnProperty), uma
        // queue-url vazia faria o job rodar para sempre falhando em
        // silencio a cada ciclo -- melhor derrubar a subida do servico.
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("filajusta.matching.liberacao-agendada-relay.queue-url nao pode ser "
                    + "vazio com filajusta.matching.liberacao-agendada-relay.enabled=true");
        }
        this.liberacaoAgendadaRepositorio = liberacaoAgendadaRepositorio;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        // batch-size <= 0 quebraria a query de leitura a cada execucao --
        // piso de 1 em vez de derrubar a subida, por ser um parametro de
        // tuning, nao uma dependencia externa.
        this.loteTamanho = Math.max(1, loteTamanho);
    }

    @Scheduled(fixedDelayString = "${filajusta.matching.liberacao-agendada-relay.poll-interval-ms:5000}")
    @Transactional
    void publicarPendentes() {
        List<LiberacaoAgendada> pendentes;
        try {
            pendentes = liberacaoAgendadaRepositorio.buscarPendentes(loteTamanho);
        } catch (RuntimeException e) {
            // Nenhuma excecao pode escapar do job (nao pode derrubar a app).
            log.error("Falha ao ler liberacoes agendadas pendentes -- tenta de novo na proxima execucao", e);
            return;
        }

        // Fila standard (nao FIFO): sem restricao de ordem entre Recursos --
        // a falha de UM item so pula esse item, nunca bloqueia os seguintes
        // (diferente do bloqueio por MessageGroupId de RelaySnsPublisherJob).
        for (LiberacaoAgendada liberacao : pendentes) {
            publicarEMarcar(liberacao);
        }
    }

    private void publicarEMarcar(LiberacaoAgendada liberacao) {
        String corpo;
        try {
            corpo = montarCorpo(liberacao);
        } catch (RuntimeException e) {
            // Distinto de falha de publicacao: um bug de serializacao do
            // corpo nao e uma falha de rede do SQS, e confundir os dois logs
            // atrapalha o diagnostico.
            log.error("Falha ao montar o corpo da mensagem da liberacao agendada {} (recursoId={}, "
                    + "correlationId={}) -- bug de serializacao, nao uma falha de rede; linha permanece pendente",
                    liberacao.getAlocacaoId(), liberacao.getRecursoId(), liberacao.getCorrelationId(), e);
            return;
        }

        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(corpo)
                    .delaySeconds(liberacao.getDelaySegundos())
                    .build());
        } catch (RuntimeException e) {
            log.error("Falha ao publicar a liberacao agendada {} (recursoId={}, correlationId={}) na fila SQS "
                    + "-- linha permanece pendente, retry na proxima execucao",
                    liberacao.getAlocacaoId(), liberacao.getRecursoId(), liberacao.getCorrelationId(), e);
            return;
        }

        try {
            boolean marcada = liberacaoAgendadaRepositorio.marcarComoEnviado(liberacao.getAlocacaoId());
            if (!marcada) {
                // Corrida entre instancias: outra instancia ja marcou esta
                // linha enviada primeiro -- nao e um erro, so um dado a
                // auditar.
                log.warn("Liberacao agendada {} ja havia sido marcada como enviada por outra instancia do job",
                        liberacao.getAlocacaoId());
            }
        } catch (RuntimeException e) {
            // A linha continua com enviado_em nulo (a transacao do UPDATE
            // nao commitou) e sera republicada na proxima execucao --
            // at-least-once. Nao propaga: a mensagem ja foi publicada com
            // sucesso, nao ha por que interromper o lote.
            log.error("Falha ao marcar a liberacao agendada {} como enviada apos o ack do SQS -- linha sera "
                    + "reenviada na proxima execucao (at-least-once)", liberacao.getAlocacaoId(), e);
        }
    }

    private String montarCorpo(LiberacaoAgendada liberacao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("alocacaoId", liberacao.getAlocacaoId());
        corpo.put("recursoId", liberacao.getRecursoId());
        corpo.put("correlationId", liberacao.getCorrelationId());
        return objectMapper.writeValueAsString(corpo);
    }
}
