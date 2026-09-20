package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.LiberarRecurso;
import com.filajusta.matching.infrastructure.messaging.LiberacaoAgendadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Consumidor SQS da fila standard {@code liberacao-agendada} (Story 3-4b2):
 * poller {@code @Scheduled} que lê mensagens publicadas por
 * {@code LiberacaoAgendadaRelayJob} (Story 3-4a2), desserializa cada uma,
 * valida o schema (rejeitando major version incompatível para DLQ),
 * invoca o comando {@link LiberarRecurso} para executar a liberação real
 * do Recurso, e remove a mensagem da fila após sucesso -- nunca antes
 * (Boundaries da spec 3-4b2). Idempotência garantida pelo comando
 * {@link LiberarRecurso} (Story 3-4b1): reprocessamento acidental de
 * duplicadas é no-op puro.
 *
 * <p>Envelope esperado: {@code {alocacaoId, recursoId, correlationId,
 * version, occurredAt}}. {@code version} é obrigatório (Story 3-4b2,
 * Boundaries: tolerância a schema aditivo, mas rejeição clara de major
 * version incompatível). Versão incompatível (ex.: {@code version >= 2}
 * quando esperado {@code version=1}): log WARN, mensagem reenviada para
 * DLQ sem chamar {@link LiberarRecurso}, processando continua para a
 * próxima mensagem (I/O Matrix, {@code VERSION_INCOMPATIVEL}).
 *
 * <p>Mensagem malformada (JSON inválido, campos ausentes, type mismatch):
 * não removida da fila; reentregue após {@code VisibilityTimeout} e,
 * ao atingir {@code maxReceiveCount} (declarada em {@code infra-cdk}),
 * o próprio SQS a move para a DLQ automaticamente -- este job não rastreia
 * tentativas (mesmo padrão de {@code ScoreCalculadoConsumerJob}).
 *
 * <p>Erro transiente (ex.: timeout ao chamar {@link LiberarRecurso}):
 * mensagem não removida, volta à fila e {@code ApproximateReceiveCount}
 * é incrementado até atingir {@code maxReceiveCount} (Policy do SQS).
 * Idempotência: se {@link LiberarRecurso} retorna no-op (alocação já
 * liberada), consumidor apenas deleta a mensagem sem erro (mesmo padrão
 * de {@link LiberarRecurso}, que não distingue "já liberada" de
 * "alocacaoId inexistente").
 *
 * <p>{@code @ConditionalOnProperty}
 * ({@code filajusta.matching.liberacao-agendada-consumer.enabled}, SEM
 * {@code matchIfMissing} -- default {@code false}): kill switch
 * operacional de propósito distinto do relay de publicação (Story 3-4a2).
 * Namespace novo {@code liberacao-agendada-consumer.*} isolado do relay.
 *
 * <p>Poller {@code @Scheduled(fixedRate = 5000)}: intervalo de 5s,
 * customizável via {@code filajusta.matching.liberacao-agendada-consumer.poll-interval-ms}
 * (mesmo padrão de {@code ScoreCalculadoConsumerJob}). Long-poll com
 * {@code WaitTimeSeconds=20} e {@code MaxNumberOfMessages=10} (moldado
 * na spec 3-4b2, Boundaries).
 *
 * <p><strong>Patch 6: Trade-off de WaitTimeSeconds=20:</strong> O long-poll de
 * 20 segundos reduz a latência de detecção de novas mensagens (máximo 20s de
 * espera vazio antes de próximo ciclo de 5s) vs. aumenta requisições SQS
 * quando há muitas mensagens (cada receiveMessage custa). Neste caso, o
 * trade-off favorece latência baixa sobre custo (detecção rápida de liberações
 * agendadas é crítico para disponibilidade de recursos).
 *
 * <p><strong>Patch 7: Idempotência garantida:</strong> A combinação de
 * retry automático via SQS (ApproximateReceiveCount + visibilityTimeout + DLQ)
 * e idempotência do comando {@link LiberarRecurso} (AC-4 da spec 3-4b1)
 * garante segurança contra duplicação: N reentregas da mesma mensagem =
 * no-op puro (já liberada OU inexistente, ambos idênticos). Nenhum lock
 * pessimista ou rastreamento de processamento necessário.
 */
@Component
@ConditionalOnProperty(prefix = "filajusta.matching.liberacao-agendada-consumer", name = "enabled")
class LiberacaoAgendadaSqsConsumerJob {

    private static final Logger log = LoggerFactory.getLogger(LiberacaoAgendadaSqsConsumerJob.class);
    private static final int VERSAO_ESPERADA = 1;
    private static final int WAIT_TIME_SECONDS = 20;
    private static final int MAX_BATCH_SIZE = 10;

    private final SqsClient sqsClient;
    private final LiberarRecurso liberarRecurso;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final String dlqUrl;
    private final int loteTamanho;

    LiberacaoAgendadaSqsConsumerJob(
            @Qualifier("liberacaoAgendadaSqsClient") SqsClient sqsClient,
            LiberarRecurso liberarRecurso,
            ObjectMapper objectMapper,
            @Value("${filajusta.matching.liberacao-agendada-consumer.queue-url}") String queueUrl,
            @Value("${filajusta.matching.liberacao-agendada-consumer.dlq-url}") String dlqUrl,
            @Value("${filajusta.matching.liberacao-agendada-consumer.batch-size:10}") int loteTamanho) {
        // Fail-fast: com o consumidor habilitado (único caso em que este bean
        // é criado, ver @ConditionalOnProperty), uma queue-url vazia faria
        // o job rodar para sempre falhando em silêncio -- melhor derrubar
        // a subida do serviço.
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException(
                    "filajusta.matching.liberacao-agendada-consumer.queue-url nao pode ser vazio com "
                            + "filajusta.matching.liberacao-agendada-consumer.enabled=true");
        }
        if (dlqUrl == null || dlqUrl.isBlank()) {
            throw new IllegalStateException(
                    "filajusta.matching.liberacao-agendada-consumer.dlq-url nao pode ser vazio com "
                            + "filajusta.matching.liberacao-agendada-consumer.enabled=true");
        }
        this.sqsClient = sqsClient;
        this.liberarRecurso = liberarRecurso;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        this.dlqUrl = dlqUrl;
        // SQS limita MaxNumberOfMessages a no máximo 10; batch-size <= 0
        // quebraria a chamada -- piso de 1.
        this.loteTamanho = Math.min(MAX_BATCH_SIZE, Math.max(1, loteTamanho));
    }

    @Scheduled(fixedDelayString = "${filajusta.matching.liberacao-agendada-consumer.poll-interval-ms:5000}")
    void consumirPendentes() {
        List<Message> mensagens;
        try {
            mensagens = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(loteTamanho)
                            .waitTimeSeconds(WAIT_TIME_SECONDS)
                            .build())
                    .messages();
        } catch (RuntimeException e) {
            // Nenhuma exceção pode escapar do job (não pode derrubar a app)
            // -- tenta de novo no próximo ciclo do @Scheduled.
            log.error("Falha ao receber mensagens da fila SQS standard liberacao-agendada "
                    + "-- tenta de novo na proxima execucao", e);
            return;
        }

        for (Message mensagem : mensagens) {
            processar(mensagem);
        }
    }

    private void processar(Message mensagem) {
        UUID alocacaoId;
        UUID recursoId;
        String correlationId;
        int receiveCount;

        // Patch 1: Desserialização com try-catch específico para erro de JSON
        LiberacaoAgendadaEvent evento;
        try {
            evento = objectMapper.readValue(
                    mensagem.body(), LiberacaoAgendadaEvent.class);
        } catch (Exception e) {
            // JsonProcessingException ou qualquer erro de desserialização:
            // log WARN e reenvia para DLQ, não deleta mensagem
            receiveCount = extrairApproximateReceiveCount(mensagem);
            log.warn("JSON malformado na mensagem SQS (messageId={}, receiveCount={}): "
                    + "nao removida, sera reentregue ate ir para a DLQ",
                    mensagem.messageId(), receiveCount, e);
            return;
        }

        try {
            // Validar version antes de qualquer outra operação
            if (evento.version() != VERSAO_ESPERADA) {
                receiveCount = extrairApproximateReceiveCount(mensagem);
                log.warn("Evento com version incompatível rejeitado para DLQ "
                        + "(messageId={}, version={}, receiveCount={})",
                        mensagem.messageId(), evento.version(), receiveCount);
                reenviareParaDlq(mensagem);
                return;
            }

            alocacaoId = evento.alocacaoId();
            recursoId = evento.recursoId();
            correlationId = evento.correlationId();
            receiveCount = extrairApproximateReceiveCount(mensagem);

            // Patch 2: Validar correlationId junto com alocacaoId e recursoId
            if (alocacaoId == null || recursoId == null || correlationId == null) {
                throw new IllegalArgumentException(
                        "Campos obrigatórios nulos: alocacaoId=" + alocacaoId
                                + ", recursoId=" + recursoId
                                + ", correlationId=" + correlationId);
            }
        } catch (IllegalArgumentException e) {
            // Campos obrigatórios nulos - mensagem malformada
            receiveCount = extrairApproximateReceiveCount(mensagem);
            log.error("Mensagem malformada na fila SQS liberacao-agendada "
                    + "(messageId={}, receiveCount={}): campos obrigatórios ausentes -- "
                    + "nao removida, sera reentregue ate ir para a DLQ",
                    mensagem.messageId(), receiveCount, e);
            return;
        } catch (RuntimeException e) {
            // Outro erro de runtime (ex.: conversão de UUID inválida)
            receiveCount = extrairApproximateReceiveCount(mensagem);
            log.error("Mensagem malformada na fila SQS standard liberacao-agendada "
                    + "(messageId={}, receiveCount={}): nao removida, sera reentregue ate ir para a DLQ",
                    mensagem.messageId(), receiveCount, e);
            return;
        }

        try {
            liberarRecurso.liberar(alocacaoId, recursoId, correlationId);
        } catch (RuntimeException e) {
            // Distinto de "malformado": o parse do envelope foi válido, mas
            // LiberarRecurso falhou (ex.: DB indisponível, timeout) --
            // mesmo tratamento: mensagem NÃO removida, retry na próxima
            // execução.
            log.error("Falha ao executar liberacao do recurso (messageId={}, alocacaoId={}, "
                    + "recursoId={}, correlationId={}, receiveCount={}): mensagem NAO removida, "
                    + "retry na proxima execucao",
                    mensagem.messageId(), alocacaoId, recursoId, correlationId, receiveCount, e);
            return;
        }

        try {
            // Só remove a mensagem da fila após LiberarRecurso confirmado
            // (Boundaries da spec 3-4b2) -- nunca antes.
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(mensagem.receiptHandle())
                    .build());
            log.info("Liberacao do recurso confirmada e processada com sucesso "
                    + "(messageId={}, alocacaoId={}, recursoId={}, correlationId={}, receiveCount={})",
                    mensagem.messageId(), alocacaoId, recursoId, correlationId, receiveCount);
        } catch (RuntimeException e) {
            // LiberarRecurso já confirmado -- se o delete falhar, a mensagem
            // será reentregue e LiberarRecurso repetido é um no-op (mesmo
            // eventId, idempotência do comando) -- não propaga.
            log.error("Falha ao remover da fila a mensagem ja processada (messageId={}, "
                    + "alocacaoId={}, recursoId={}, correlationId={}, receiveCount={}): "
                    + "sera reentregue, reprocessamento e idempotente",
                    mensagem.messageId(), alocacaoId, recursoId, correlationId, receiveCount, e);
        }
    }

    private void reenviareParaDlq(Message mensagem) {
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .messageBody(mensagem.body())
                    .build());

            // Remove da fila principal após sucesso em DLQ
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(mensagem.receiptHandle())
                    .build());
        } catch (RuntimeException e) {
            log.error("Falha ao reenviar mensagem para DLQ (messageId={}): "
                    + "mensagem permanecera na fila principal ate maxReceiveCount",
                    mensagem.messageId(), e);
        }
    }

    private static int extrairApproximateReceiveCount(Message mensagem) {
        try {
            String count = mensagem.attributes().get(
                    software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
            return count != null ? Integer.parseInt(count) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
