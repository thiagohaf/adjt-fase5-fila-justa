package com.confirmasus.auditoria.infrastructure.relay;

import com.confirmasus.auditoria.application.port.DecisaoAuditoriaRepositorio;
import com.confirmasus.auditoria.domain.DecisaoAuditoria;
import com.confirmasus.auditoria.domain.TipoDecisao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumidor SQS FIFO que processa eventos e registra decisões auditáveis
 * (Story 4.1, Design Notes).
 *
 * <p>Poller {@code @Scheduled} que lê a fila SQS FIFO {@code auditoria-decisoes-fifo.fifo},
 * mapeia eventos para domínio, persiste em tabela append-only via {@link DecisaoAuditoriaRepositorio}
 * e só remove a mensagem da fila ({@code deleteMessage}) após persistência confirmada
 * (Boundaries da spec 4.1).
 *
 * <p>Envelope esperado: {@code {eventId, eventType, occurredAt, version, correlationId, payload}}
 * (contrato fixado no Epic 2 / Epic 3). Mapeia {@code eventType} → {@code TipoDecisao}:
 * <ul>
 *   <li>NotificacaoConfirmacaoPublicada → NOTIFICACAO
 *   <li>ConfirmacaoRegistrada → CONFIRMACAO
 *   <li>RecusaRegistrada → RECUSA
 *   <li>AgendamentoNaoConfirmado → NAO_CONFIRMADO
 *   <li>VagaLiberada → LIBERACAO
 *   <li>SugestaoRepasseGerada → SUGESTAO_GERADA
 *   <li>RepasseConfirmado → REPASSE_CONFIRMADO
 *   <li>SugestaoRepasseRecusada → SUGESTAO_RECUSADA
 *   <li>Qualquer outro → GENERICO
 * </ul>
 *
 * <p>Extrai {@code agendamentoId}, {@code pacienteId} do payload quando disponíveis.
 * Eventos desconhecidos são registrados com {@code tipoDecisao = GENERICO} e payload
 * bruto persistido, sem erro.
 *
 * <p>Idempotência: UNIQUE constraint em {@code eventId} garante que redelíveries
 * (DLQ redrive, reprocessamento) não criam duplicatas -- DataIntegrityViolationException
 * é capturada como "dedup idempotente" (Boundaries/I-O Matrix da spec 4.1).
 *
 * <p>Mensagem malformada (I/O Matrix): nem o parse do envelope nem uma falha de
 * persistência chamam {@code deleteMessage} -- a mensagem permanece na fila, é
 * reentregue após o {@code VisibilityTimeout} e, ao atingir {@code maxReceiveCount},
 * o próprio SQS a move para a DLQ automaticamente -- este job não precisa rastrear
 * tentativas.
 *
 * <p>{@code @ConditionalOnProperty} -- mesmo kill switch de
 * {@code confirmasus.auditoria.relay.enabled} (default {@code true}).
 */
@Component
@ConditionalOnProperty(prefix = "confirmasus.auditoria.relay", name = "enabled", matchIfMissing = true)
class DecisaoSqsConsumerJob {

    private static final Logger log = LoggerFactory.getLogger(DecisaoSqsConsumerJob.class);

    // Mapeamento de eventType → TipoDecisao
    private static final Map<String, TipoDecisao> EVENT_TYPE_MAPPING = Map.ofEntries(
            Map.entry("NotificacaoConfirmacaoPublicada", TipoDecisao.NOTIFICACAO),
            Map.entry("ConfirmacaoRegistrada", TipoDecisao.CONFIRMACAO),
            Map.entry("RecusaRegistrada", TipoDecisao.RECUSA),
            Map.entry("AgendamentoNaoConfirmado", TipoDecisao.NAO_CONFIRMADO),
            Map.entry("VagaLiberada", TipoDecisao.LIBERACAO),
            Map.entry("SugestaoRepasseGerada", TipoDecisao.SUGESTAO_GERADA),
            Map.entry("RepasseConfirmado", TipoDecisao.REPASSE_CONFIRMADO),
            Map.entry("SugestaoRepasseRecusada", TipoDecisao.SUGESTAO_RECUSADA)
    );

    private final SqsClient sqsClient;
    private final DecisaoAuditoriaRepositorio repositorio;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String queueUrl;
    private final int loteTamanho;
    private final int waitTimeSeconds;

    // Construtor para testes (Mockito)
    // BUG FIX #6: queueUrl não pode ser vazio (""), usar URL válida para testes
    protected DecisaoSqsConsumerJob() {
        this(null, null, null, null, "https://sqs.us-east-1.amazonaws.com/123456789012/auditoria-decisoes.fifo", 10, 1);
    }

    DecisaoSqsConsumerJob(SqsClient sqsClient,
                          DecisaoAuditoriaRepositorio repositorio,
                          ObjectMapper objectMapper,
                          Clock clock,
                          @Value("${confirmasus.auditoria.relay.queue-url}") String queueUrl,
                          @Value("${confirmasus.auditoria.relay.batch-size:10}") int loteTamanho,
                          @Value("${confirmasus.auditoria.relay.wait-time-seconds:1}") int waitTimeSeconds) {
        // Fail-fast: com o consumidor habilitado, uma queue-url vazia faria o
        // job rodar para sempre falhando em silencio a cada ciclo
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException(
                    "confirmasus.auditoria.relay.queue-url nao pode ser vazio com confirmasus.auditoria.relay.enabled=true");
        }
        this.sqsClient = sqsClient;
        this.repositorio = repositorio;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.queueUrl = queueUrl;
        // SQS limita ReceiveMessage a no máximo 10 mensagens por chamada
        this.loteTamanho = Math.min(10, Math.max(1, loteTamanho));
        // SQS rejeita WaitTimeSeconds fora de 0..20
        this.waitTimeSeconds = Math.min(20, Math.max(0, waitTimeSeconds));
    }

    @Scheduled(fixedDelayString = "${confirmasus.auditoria.relay.poll-interval-ms:5000}")
    void consumirPendentes() {
        List<Message> mensagens;
        try {
            mensagens = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(loteTamanho)
                            .waitTimeSeconds(waitTimeSeconds)
                            .build())
                    .messages();
        } catch (RuntimeException e) {
            // Nenhuma exceção pode escapar do job -- tenta de novo no próximo ciclo
            log.error("Falha ao receber mensagens da fila SQS FIFO auditoria-decisoes.fifo "
                    + "-- tenta de novo na proxima execucao", e);
            return;
        }

        for (Message mensagem : mensagens) {
            processar(mensagem);
        }
    }

    private void processar(Message mensagem) {
        JsonNode envelope;
        UUID eventId;
        String correlationId;
        try {
            envelope = objectMapper.readTree(mensagem.body());
            validarEnvelope(envelope);
            eventId = UUID.fromString(envelope.get("eventId").asText());
            // BUG FIX #9: MissingNode.asText() retorna "", usar instanceof para distinguir
            JsonNode correlationIdNode = envelope.get("correlationId");
            correlationId = (!(correlationIdNode instanceof MissingNode) && !correlationIdNode.isNull())
                    ? correlationIdNode.asText() : null;
        } catch (JsonProcessingException | RuntimeException e) {
            // I/O Matrix: "Mensagem malformada" -- log de erro, mensagem NÃO
            // removida; reentregue até maxReceiveCount, depois DLQ
            log.error("Mensagem malformada na fila SQS FIFO auditoria-decisoes.fifo "
                    + "(messageId={}) -- nao removida, sera reentregue ate ir para a DLQ",
                    mensagem.messageId(), e);
            return;
        }

        try {
            String eventType = envelope.get("eventType").asText();
            JsonNode payload = envelope.get("payload");
            Instant occurredAt = Instant.parse(envelope.get("occurredAt").asText());

            // Mapeia eventType → TipoDecisao (ou GENERICO se desconhecido)
            TipoDecisao tipoDecisao = EVENT_TYPE_MAPPING.getOrDefault(eventType, TipoDecisao.GENERICO);

            // Extrai IDs do payload (nullable)
            Long agendamentoId = extrairLongDoPayload(payload, "agendamentoId");
            Long pacienteId = extrairLongDoPayload(payload, "pacienteId");

            // Extrai motivo do payload conforme tipo (nullable)
            String motivo = extrairMotivoDoPayload(payload, tipoDecisao);

            // BUG FIX #8: serializar payload bruto para auditoria completa
            String payloadBruto = null;
            if (payload != null && !payload.isNull()) {
                try {
                    payloadBruto = objectMapper.writeValueAsString(payload);
                } catch (JsonProcessingException e) {
                    // Se falhar a serialização do payload, log mas continua (payload bruto opcional)
                    log.warn("Falha ao serializar payload bruto (eventId={}), continuando sem payload bruto", eventId, e);
                }
            }

            // Cria e persiste a decisão
            DecisaoAuditoria decisao = DecisaoAuditoria.criar(
                    eventId,
                    agendamentoId,
                    pacienteId,
                    tipoDecisao,
                    motivo,
                    occurredAt,
                    clock.instant(),
                    payloadBruto
            );

            repositorio.salvar(decisao);

        } catch (DataIntegrityViolationException e) {
            // Idempotência: eventId duplicado = constraint violation
            // Achado: log de info (não erro), mensagem pode ser removida
            // pois já foi processada em execução anterior
            log.info("Evento com eventId duplicado já registrado (messageId={}, eventId={}, "
                    + "correlationId={}) -- removendo da fila, dedup idempotente",
                    mensagem.messageId(), eventId, correlationId);
        } catch (RuntimeException e) {
            // Distinto de "malformado" -- achado do envelope válido, mas o
            // persistência falhou (ex.: DB indisponível) -- mensagem NÃO removida, retry
            log.error("Falha ao persistir decisão auditória (messageId={}, eventId={}, "
                    + "correlationId={}) -- mensagem NAO removida, retry na proxima execucao",
                    mensagem.messageId(), eventId, correlationId, e);
            return;
        }

        try {
            // Remove a mensagem da fila após persistência confirmada
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(mensagem.receiptHandle())
                    .build());
        } catch (RuntimeException e) {
            // Persistência já confirmada -- se delete falhar, reentrega será
            // processada novamente mas é idempotente (mesmo eventId)
            log.error("Falha ao remover da fila a mensagem já processada (messageId={}, eventId={}, "
                    + "correlationId={}) -- sera reentregue, reprocessamento e idempotente",
                    mensagem.messageId(), eventId, correlationId, e);
        }
    }

    /**
     * Extrai um valor Long do payload de forma segura (nullable).
     * BUG FIX #7: canConvertToLong() rejeita strings numéricas; tentar parsear string como Long
     */
    private static Long extrairLongDoPayload(JsonNode payload, String field) {
        if (payload == null) return null;
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }

        // Se for número direto, converter
        if (node.isNumber()) {
            return node.asLong();
        }

        // Se for string, tentar parsear como número
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Extrai o motivo do payload conforme o tipo de decisão.
     * Alguns tipos têm motivo, outros não.
     */
    private static String extrairMotivoDoPayload(JsonNode payload, TipoDecisao tipoDecisao) {
        if (payload == null) return null;

        // Tipos que esperam motivo
        if (tipoDecisao == TipoDecisao.RECUSA ||
            tipoDecisao == TipoDecisao.NAO_CONFIRMADO ||
            tipoDecisao == TipoDecisao.LIBERACAO ||
            tipoDecisao == TipoDecisao.REPASSE_CONFIRMADO ||
            tipoDecisao == TipoDecisao.SUGESTAO_RECUSADA) {
            JsonNode motivoNode = payload.get("motivo");
            if (motivoNode != null && !motivoNode.isNull()) {
                return motivoNode.asText();
            }
        }
        return null;
    }

    /**
     * Valida o envelope base (eventId, eventType, occurredAt presentes).
     * Eventos com eventType desconhecido são aceitos e registrados como GENERICO.
     *
     * BUG FIX #5: Jackson retorna MissingNode (não null), usar isMissing() em vez de == null
     */
    private static void validarEnvelope(JsonNode envelope) {
        if (envelope == null || envelope.isNull()) {
            throw new IllegalArgumentException("corpo da mensagem nao e um JSON valido");
        }
        if (envelope.get("eventId") instanceof MissingNode) {
            throw new IllegalArgumentException("envelope sem eventId");
        }
        if (envelope.get("eventType") instanceof MissingNode) {
            throw new IllegalArgumentException("envelope sem eventType");
        }
        if (envelope.get("occurredAt") instanceof MissingNode) {
            throw new IllegalArgumentException("envelope sem occurredAt");
        }
    }
}
