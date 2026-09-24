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
 * Cliente HTTP para upsertar Recurso via gateway-service.
 *
 * Story 5.1: Chama {@code POST /v1/recursos} do gateway (que roteia para
 * matching-alocacao-service) com JWT autenticado. Usa header
 * {@code Authorization: Bearer {token}} para autenticação.
 *
 * <p>Padrão idêntico a {@code TriagemScoreClient} (triagem-score-service).
 * Falha com erro claro se gateway indisponível.
 */
public class RecursoClient {
    private static final Logger logger = LoggerFactory.getLogger(RecursoClient.class);

    private final String gatewayUrl;
    private final AuthClient authClient;
    private final ObjectMapper mapper;

    public RecursoClient(String gatewayUrl, AuthClient authClient) {
        this.gatewayUrl = gatewayUrl;
        this.authClient = authClient;
        this.mapper = new ObjectMapper();
    }

    /**
     * Upsertar idempotente de Recurso via POST /v1/recursos.
     * Retorna recursoId (criado na primeira vez ou preservado em reexecução).
     *
     * @param codigoRecurso identificador único do recurso
     * @param especialidade tipo de atendimento (ex: "Cardiologia")
     * @param unidade localização/unidade de saúde
     * @param especificidadeRank nível de especialização (1-4)
     * @param disponivel se recurso está disponível
     * @return UUID do recurso persistido
     * @throws IllegalStateException se gateway indisponível ou request inválido
     */
    public UUID upsertar(String codigoRecurso, String especialidade, String unidade,
                         int especificidadeRank, boolean disponivel) {
        logger.info("Upsertando Recurso: codigo={}, especialidade={}, unidade={}, rank={}, disponivel={}",
                codigoRecurso, especialidade, unidade, especificidadeRank, disponivel);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(gatewayUrl + "/v1/recursos");

            // Autentica com JWT
            String token = authClient.obtenerToken();
            post.setHeader("Authorization", "Bearer " + token);

            // Monta payload
            Map<String, Object> body = new HashMap<>();
            body.put("codigoRecurso", codigoRecurso);
            body.put("especialidade", especialidade);
            body.put("unidade", unidade);
            body.put("especificidadeRank", especificidadeRank);
            body.put("disponivel", disponivel);

            String jsonBody = mapper.writeValueAsString(body);
            post.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                int statusCode = response.getCode();
                String responseBody = new String(response.getEntity().getContent().readAllBytes());

                if (statusCode == 201 || statusCode == 200) {
                    Map<String, Object> recursoResponse = mapper.readValue(responseBody, Map.class);
                    String recursoIdStr = (String) recursoResponse.get("recursoId");

                    if (recursoIdStr == null || recursoIdStr.trim().isEmpty()) {
                        throw new IllegalStateException("Gateway retornou recursoId vazio");
                    }

                    try {
                        UUID recursoId = UUID.fromString(recursoIdStr);
                        String action = statusCode == 201 ? "criado" : "atualizado";
                        logger.info("Recurso {} com sucesso: recursoId={}, codigo={}",
                                action, recursoId, codigoRecurso);
                        return recursoId;
                    } catch (IllegalArgumentException e) {
                        throw new IllegalStateException(
                                "Gateway retornou recursoId malformado (não é UUID válido): " + recursoIdStr, e);
                    }
                } else if (statusCode == 422) {
                    // Validação falhou (ex: codigoRecurso duplicado com dados diferentes)
                    logger.warn("Validação falhou (422) para recurso {}: {}",
                            codigoRecurso, responseBody);
                    throw new IllegalStateException(
                            "Validação falhou para Recurso " + codigoRecurso + ": " + responseBody);
                } else if (statusCode >= 500) {
                    throw new IllegalStateException(
                            "Gateway indisponível (status " + statusCode + "): " + responseBody);
                } else {
                    throw new IllegalStateException(
                            "Falha ao upsertar Recurso (status " + statusCode + "): " + responseBody);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao chamar gateway: " + e.getMessage(), e);
        }
    }
}
