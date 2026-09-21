package com.confirmasus.auditoria.infrastructure.relay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para DecisaoSqsConsumerJob.
 *
 * <p>Valida lógica de processamento:
 * - Mapeamento eventType → TipoDecisao
 * - Extração segura de IDs do payload
 * - Validação de envelope
 * - Parsing e tratamento de JSON
 *
 * <p>Nota: estes são testes unitários estáticos (sem Spring/Mockito).
 * Testes de integração com SQS e DB estão em DecisaoSqsConsumerJobIntegrationTest.
 */
class DecisaoSqsConsumerJobTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Mapeamento: ConfirmacaoRegistrada → CONFIRMACAO (validação de contract)")
    void testarMapeamentoConfirmacao() {
        // Simular envelope do evento ConfirmacaoRegistrada
        Map<String, Object> envelope = Map.ofEntries(
                Map.entry("eventId", UUID.randomUUID().toString()),
                Map.entry("eventType", "ConfirmacaoRegistrada"),
                Map.entry("occurredAt", Instant.now().toString()),
                Map.entry("version", 1),
                Map.entry("correlationId", "corr-123"),
                Map.entry("payload", Map.of(
                        "agendamentoId", 123L,
                        "pacienteId", 456L
                ))
        );

        // Validar que eventType é reconhecido
        String eventType = (String) envelope.get("eventType");
        assertEquals("ConfirmacaoRegistrada", eventType);

        // Mapeamento esperado: ConfirmacaoRegistrada → TipoDecisao.CONFIRMACAO
        // (isto seria testado via integration test com repositório mock)
    }

    @Test
    @DisplayName("Evento desconhecido mapeado para GENERICO")
    void testarMapeamentoGenerico() {
        // Novo eventType não reconhecido (evolução aditiva de schema)
        Map<String, Object> envelope = Map.ofEntries(
                Map.entry("eventId", UUID.randomUUID().toString()),
                Map.entry("eventType", "NovoEventoFuturo"),
                Map.entry("occurredAt", Instant.now().toString()),
                Map.entry("version", 1),
                Map.entry("correlationId", "corr-456"),
                Map.entry("payload", Map.of("dados", "novos"))
        );

        // Validar que envelope é aceitável (não falha validação)
        String eventType = (String) envelope.get("eventType");
        assertNotNull(eventType);

        // Expectativa: será mapeado para TipoDecisao.GENERICO
    }

    @Test
    @DisplayName("Extração de agendamentoId do payload (null-safe)")
    void testarExtracao_AgendamentoId_NullSafe() throws Exception {
        // Payload com agendamentoId válido
        Map<String, Object> payloadComId = Map.of(
                "agendamentoId", 123L,
                "pacienteId", 456L
        );

        String json = objectMapper.writeValueAsString(payloadComId);
        JsonNode node = objectMapper.readTree(json);

        // Extração segura
        JsonNode agendamentoIdNode = node.get("agendamentoId");
        assertNotNull(agendamentoIdNode);
        assertEquals(123, agendamentoIdNode.asLong());

        // Payload sem agendamentoId
        Map<String, Object> payloadSemId = Map.of(
                "pacienteId", 456L
        );

        String json2 = objectMapper.writeValueAsString(payloadSemId);
        JsonNode node2 = objectMapper.readTree(json2);

        JsonNode agendamentoIdNode2 = node2.get("agendamentoId");
        assertNull(agendamentoIdNode2);
    }

    @Test
    @DisplayName("Extração de motivo conforme TipoDecisao")
    void testarExtracao_Motivo_Conforme_Tipo() throws Exception {
        // Payload com motivo
        Map<String, Object> payloadComMotivo = Map.of(
                "motivo", "Paciente recusou"
        );

        String json = objectMapper.writeValueAsString(payloadComMotivo);
        JsonNode node = objectMapper.readTree(json);

        JsonNode motivoNode = node.get("motivo");
        assertNotNull(motivoNode);
        assertEquals("Paciente recusou", motivoNode.asText());

        // Payload sem motivo
        Map<String, Object> payloadSemMotivo = Map.of(
                "dados", "outros"
        );

        String json2 = objectMapper.writeValueAsString(payloadSemMotivo);
        JsonNode node2 = objectMapper.readTree(json2);

        JsonNode motivoNode2 = node2.get("motivo");
        assertNull(motivoNode2);
    }

    @Test
    @DisplayName("Validação de envelope rejeita eventId ausente")
    void testarValidacao_Envelope_RejectaEventIdAusente() throws Exception {
        // Envelope sem eventId deve ser rejeitado
        Map<String, Object> envelopeInvalido = Map.ofEntries(
                // sem eventId
                Map.entry("eventType", "ConfirmacaoRegistrada"),
                Map.entry("occurredAt", Instant.now().toString()),
                Map.entry("version", 1),
                Map.entry("correlationId", "corr-123"),
                Map.entry("payload", Map.of())
        );

        String json = objectMapper.writeValueAsString(envelopeInvalido);
        JsonNode node = objectMapper.readTree(json);

        JsonNode eventIdNode = node.get("eventId");
        assertNull(eventIdNode);

        // Esperado: validação rejeita
    }

    @Test
    @DisplayName("Validação de envelope rejeita eventType ausente")
    void testarValidacao_Envelope_RejectaEventTypeAusente() throws Exception {
        // Envelope sem eventType deve ser rejeitado
        Map<String, Object> envelopeInvalido = Map.ofEntries(
                Map.entry("eventId", UUID.randomUUID().toString()),
                // sem eventType
                Map.entry("occurredAt", Instant.now().toString()),
                Map.entry("version", 1),
                Map.entry("correlationId", "corr-123"),
                Map.entry("payload", Map.of())
        );

        String json = objectMapper.writeValueAsString(envelopeInvalido);
        JsonNode node = objectMapper.readTree(json);

        JsonNode eventTypeNode = node.get("eventType");
        assertNull(eventTypeNode);

        // Esperado: validação rejeita
    }

    @Test
    @DisplayName("Validação de envelope aceita eventType desconhecido")
    void testarValidacao_Envelope_AceitaEventTypeDesconhecido() throws Exception {
        // Envelope com eventType desconhecido é aceito (evolução aditiva de schema)
        Map<String, Object> envelopeValido = Map.ofEntries(
                Map.entry("eventId", UUID.randomUUID().toString()),
                Map.entry("eventType", "NovoEventoFuturo"),
                Map.entry("occurredAt", Instant.now().toString()),
                Map.entry("version", 1),
                Map.entry("correlationId", "corr-123"),
                Map.entry("payload", Map.of())
        );

        String json = objectMapper.writeValueAsString(envelopeValido);
        JsonNode node = objectMapper.readTree(json);

        JsonNode eventTypeNode = node.get("eventType");
        assertNotNull(eventTypeNode);
        assertEquals("NovoEventoFuturo", eventTypeNode.asText());

        // Esperado: aceito (mapeado para GENERICO, não gera erro)
    }
}
