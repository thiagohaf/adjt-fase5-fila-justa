package com.confirmasus.seedadapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente HTTP para criar Agendamento e orquestrar transições de estado via gateway-service.
 *
 * Story 5.2: Chama {@code POST /v1/agendamentos} do gateway para criar Agendamento com CPF,
 * recursoId, dataHora. Gateway resolve CPF internamente (Story 1.1 responsibility).
 * Orquestra transições de estado via {@code POST /v1/agendamentos/{id}/confirmacao} (Story 1.2/1.3)
 * e {@code POST /v1/agendamentos/{id}/recusa} (Story 1.4).
 *
 * <p>Idempotência: gateway deduplica por CPF+recursoId+dataHora naturalmente.
 * Se reexecução encontrar duplicata, tratado como 409 e prossegue.
 *
 * <p>Padrão idêntico a {@code RecursoClient}. Falha com erro claro se gateway indisponível.
 */
public class AgendamentoClient {
    private static final Logger logger = LoggerFactory.getLogger(AgendamentoClient.class);

    private final String gatewayUrl;
    private final AuthClient authClient;
    private final ObjectMapper mapper;

    public AgendamentoClient(String gatewayUrl, AuthClient authClient) {
        this.gatewayUrl = gatewayUrl;
        this.authClient = authClient;
        this.mapper = new ObjectMapper();
    }

    /**
     * Criar ou obter idempotente de Agendamento via POST /v1/agendamentos.
     * Retorna agendamentoId sincrono.
     *
     * @param cpf CPF do paciente (em texto claro; gateway resolve internamente)
     * @param recursoId UUID do recurso (já carregado em Story 5.1)
     * @param dataHoraAgendamento ISO 8601 datetime (ex: "2026-10-01T14:00:00Z")
     * @return UUID do agendamento persistido
     * @throws IllegalStateException se CPF/recursoId/dataHora inválidos (422) ou gateway indisponível (5xx)
     */
    public UUID criarOuObter(String cpf, UUID recursoId, String dataHoraAgendamento) {
        logger.info("Criando Agendamento: cpf={}, recursoId={}, dataHora={}",
                maskCpf(cpf), recursoId, dataHoraAgendamento);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(gatewayUrl + "/v1/agendamentos");

            // Autentica com JWT
            String token = authClient.obtenerToken();
            post.setHeader("Authorization", "Bearer " + token);

            // Monta payload
            Map<String, Object> body = new HashMap<>();
            body.put("cpf", cpf);
            body.put("recursoId", recursoId.toString());
            body.put("dataHoraAgendamento", dataHoraAgendamento);

            String jsonBody = mapper.writeValueAsString(body);
            post.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                int statusCode = response.getCode();
                String responseBody = new String(response.getEntity().getContent().readAllBytes());

                if (statusCode == 201) {
                    Map<String, Object> agendamentoResponse = mapper.readValue(responseBody, Map.class);
                    String agendamentoIdStr = (String) agendamentoResponse.get("agendamentoId");

                    if (agendamentoIdStr == null || agendamentoIdStr.isEmpty()) {
                        throw new IllegalStateException("Gateway retornou agendamentoId vazio");
                    }

                    UUID agendamentoId = UUID.fromString(agendamentoIdStr);
                    logger.info("Agendamento criado com sucesso: agendamentoId={}, cpf={}, recursoId={}",
                            agendamentoId, maskCpf(cpf), recursoId);
                    return agendamentoId;
                } else if (statusCode == 409) {
                    // Duplicata: agendamento já existe (idempotência)
                    // Tenta extrair agendamentoId da resposta de conflito
                    Map<String, Object> agendamentoResponse = mapper.readValue(responseBody, Map.class);
                    String agendamentoIdStr = (String) agendamentoResponse.get("agendamentoId");

                    if (agendamentoIdStr != null && !agendamentoIdStr.isEmpty()) {
                        UUID agendamentoId = UUID.fromString(agendamentoIdStr);
                        logger.info("Agendamento já existe (409): agendamentoId={}, cpf={}, recursoId={} — prosseguindo",
                                agendamentoId, maskCpf(cpf), recursoId);
                        return agendamentoId;
                    }

                    throw new IllegalStateException(
                            "Duplicata detectada (409) mas agendamentoId não retornado: " + responseBody);
                } else if (statusCode == 422) {
                    // Validação falhou (CPF inválido, recursoId não encontrado, dataHora no passado, etc)
                    logger.warn("Validação falhou (422) para agendamento cpf={}, recursoId={}: {}",
                            maskCpf(cpf), recursoId, responseBody);
                    throw new IllegalStateException(
                            "Validação falhou para Agendamento cpf=" + maskCpf(cpf) + ", recursoId=" + recursoId);
                } else if (statusCode >= 500) {
                    throw new IllegalStateException(
                            "Gateway indisponível (status " + statusCode + "): " + responseBody);
                } else {
                    throw new IllegalStateException(
                            "Falha ao criar Agendamento (status " + statusCode + "): " + responseBody);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao chamar gateway: " + e.getMessage(), e);
        }
    }

    /**
     * Orquestra transição de Agendamento para estado desejado via chamadas encadeadas.
     *
     * <p>Estados suportados:
     * - AGUARDANDO_JANELA: estado inicial (não faz nada)
     * - AGUARDANDO_CONFIRMACAO: chamada a abrirJanela (POST /v1/agendamentos/{id}/confirmacao)
     * - CONFIRMADO: abrirJanela + confirmarPresenca (duas chamadas a POST .../confirmacao)
     * - LIBERADO: abrirJanela + recusarPresenca (POST .../confirmacao + POST .../recusa)
     *
     * @param agendamentoId UUID do agendamento já criado
     * @param estadoDesejado nome do estado alvo (enum: AGUARDANDO_JANELA, AGUARDANDO_CONFIRMACAO, CONFIRMADO, LIBERADO)
     * @throws IllegalStateException se gateway indisponível ou transição falhar
     */
    public void transicionarParaEstado(UUID agendamentoId, String estadoDesejado) {
        logger.info("Transicionando agendamento {} para estado {}", agendamentoId, estadoDesejado);

        switch (estadoDesejado) {
            case "AGUARDANDO_JANELA":
                // Estado inicial, nenhuma ação necessária
                logger.info("Agendamento {} já em estado AGUARDANDO_JANELA", agendamentoId);
                break;

            case "AGUARDANDO_CONFIRMACAO":
                // Abrirjana (Story 1.2): POST /v1/agendamentos/{id}/confirmacao
                abrirJanela(agendamentoId);
                logger.info("Agendamento {} transicionado para AGUARDANDO_CONFIRMACAO", agendamentoId);
                break;

            case "CONFIRMADO":
                // AbrirJanela + ConfirmarPresenca: duas chamadas a POST .../confirmacao
                abrirJanela(agendamentoId);
                confirmarPresenca(agendamentoId);
                logger.info("Agendamento {} transicionado para CONFIRMADO", agendamentoId);
                break;

            case "LIBERADO":
                // AbrirJanela + RecusarPresenca: POST .../confirmacao + POST .../recusa
                abrirJanela(agendamentoId);
                recusarPresenca(agendamentoId);
                logger.info("Agendamento {} transicionado para LIBERADO", agendamentoId);
                break;

            default:
                throw new IllegalArgumentException("Estado desconhecido: " + estadoDesejado);
        }
    }

    /**
     * Abre janela de confirmação (Story 1.2).
     * POST /v1/agendamentos/{id}/confirmacao (primeira chamada).
     * Transição: AGUARDANDO_JANELA -> AGUARDANDO_CONFIRMACAO
     */
    private void abrirJanela(UUID agendamentoId) {
        logger.debug("Abrindo janela de confirmação para agendamento {}", agendamentoId);
        chamarEndpointTransicao(agendamentoId, "/confirmacao", "abrirJanela");
    }

    /**
     * Confirma presença (Story 1.3).
     * POST /v1/agendamentos/{id}/confirmacao (segunda chamada, após abrirJanela).
     * Transição: AGUARDANDO_CONFIRMACAO -> CONFIRMADO
     */
    private void confirmarPresenca(UUID agendamentoId) {
        logger.debug("Confirmando presença para agendamento {}", agendamentoId);
        chamarEndpointTransicao(agendamentoId, "/confirmacao", "confirmarPresenca");
    }

    /**
     * Recusa presença (Story 1.4).
     * POST /v1/agendamentos/{id}/recusa.
     * Transição: AGUARDANDO_CONFIRMACAO -> LIBERADO (vacina-se para List de Espera)
     */
    private void recusarPresenca(UUID agendamentoId) {
        logger.debug("Recusando presença para agendamento {}", agendamentoId);
        chamarEndpointTransicao(agendamentoId, "/recusa", "recusarPresenca");
    }

    /**
     * Chama endpoint de transição de estado.
     * Genérico para /confirmacao e /recusa.
     *
     * @param agendamentoId UUID do agendamento
     * @param endpoint /confirmacao ou /recusa
     * @param acao nome da ação (para logs)
     * @throws IllegalStateException se falha (aborta com falha explícita)
     */
    private void chamarEndpointTransicao(UUID agendamentoId, String endpoint, String acao) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(gatewayUrl + "/v1/agendamentos/" + agendamentoId + endpoint);

            // Autentica com JWT
            String token = authClient.obtenerToken();
            post.setHeader("Authorization", "Bearer " + token);

            // Payload vazio ou com dados mínimos (depende do spec)
            Map<String, Object> body = new HashMap<>();
            String jsonBody = mapper.writeValueAsString(body);
            post.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            client.execute(post, response -> {
                int statusCode = response.getCode();
                String responseBody = new String(response.getEntity().getContent().readAllBytes());

                if (statusCode == 200 || statusCode == 201) {
                    logger.debug("{} executado com sucesso para agendamento {}", acao, agendamentoId);
                    return null;
                } else if (statusCode == 422) {
                    throw new IllegalStateException(
                            acao + " falhou (422) para agendamento " + agendamentoId + ": " + responseBody);
                } else if (statusCode >= 500) {
                    throw new IllegalStateException(
                            "Gateway indisponível (status " + statusCode + ") ao executar " + acao +
                            " para agendamento " + agendamentoId + ": " + responseBody);
                } else {
                    throw new IllegalStateException(
                            acao + " falhou (status " + statusCode + ") para agendamento " + agendamentoId +
                            ": " + responseBody);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao chamar gateway para " + acao + ": " + e.getMessage(), e);
        }
    }

    /**
     * Mascara CPF para logging (segurança).
     * Exibe apenas os últimos 2 dígitos: 12345678901 -> ****8901
     */
    private String maskCpf(String cpf) {
        if (cpf == null || cpf.length() < 2) {
            return "***";
        }
        return "****" + cpf.substring(cpf.length() - 2);
    }
}
