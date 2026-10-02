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

/**
 * Cliente HTTP para autenticação via auth-service.
 *
 * Story 5.1: Obtém JWT com credenciais técnicas (username/password) da
 * {@code POST /v1/auth/login} e armazena em memória. Reutiliza o token
 * em requisições subsequentes até expiração (refresh sob demanda).
 *
 * <p>Credenciais obtidas de variáveis de ambiente:
 * - {@code SEED_ADAPTER_USERNAME}
 * - {@code SEED_ADAPTER_PASSWORD}
 *
 * <p>Falha com erro claro se auth-service indisponível (não silencia).
 */
public class AuthClient {
    private static final Logger logger = LoggerFactory.getLogger(AuthClient.class);

    private final String authServiceUrl;
    private final String username;
    private final String password;
    private final ObjectMapper mapper;

    private String cachedToken;
    private long tokenExpiryTime;

    public AuthClient(String authServiceUrl, String username, String password) {
        this.authServiceUrl = authServiceUrl;
        this.username = username;
        this.password = password;
        this.mapper = new ObjectMapper();
        this.tokenExpiryTime = 0;
    }

    /**
     * Obtém JWT válido, reutilizando token em cache se ainda não expirou.
     * Falha explicitamente se auth-service indisponível.
     *
     * @return JWT token
     * @throws IllegalStateException se auth-service indisponível ou token inválido
     */
    public String obtenerToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            logger.debug("Reutilizando JWT em cache (válido por mais {} ms)",
                    tokenExpiryTime - System.currentTimeMillis());
            return cachedToken;
        }

        return loginEArmazenarToken();
    }

    private String loginEArmazenarToken() {
        logger.info("Obtendo novo JWT de {}", authServiceUrl);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(authServiceUrl + "/v1/auth/login");

            Map<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("password", password);

            String jsonBody = mapper.writeValueAsString(body);
            post.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                int statusCode = response.getCode();
                String responseBody = new String(response.getEntity().getContent().readAllBytes());

                if (statusCode == 200) {
                    Map<String, Object> loginResponse = mapper.readValue(responseBody, Map.class);
                    String token = (String) loginResponse.get("token");

                    if (token == null || token.isEmpty()) {
                        throw new IllegalStateException("Auth service retornou token vazio");
                    }

                    // Estima expiração: JWT típico expira em 1 hora (3600s)
                    // Para segurança, renueva com 5 minutos antes
                    cachedToken = token;
                    tokenExpiryTime = System.currentTimeMillis() + (55 * 60 * 1000); // 55 minutos
                    logger.info("JWT obtido com sucesso, válido por ~55 minutos");
                    return token;
                } else if (statusCode >= 500) {
                    throw new IllegalStateException(
                            "Auth-service indisponível (status " + statusCode + "): " + responseBody);
                } else {
                    throw new IllegalStateException(
                            "Falha ao obter JWT (status " + statusCode + "): " + responseBody);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao chamar auth-service: " + e.getMessage(), e);
        }
    }
}
