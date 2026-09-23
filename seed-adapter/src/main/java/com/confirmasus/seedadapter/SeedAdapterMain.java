package com.confirmasus.seedadapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrada principal do seed-adapter (Story 5.1).
 *
 * Job de deploy-time que:
 * 1. Obtém credenciais técnicas de variáveis de ambiente
 * 2. Cria AuthClient e obtém JWT
 * 3. Cria RecursoClient
 * 4. Executa SeedDataLoader
 * 5. Aborta com erro claro se qualquer serviço indisponível
 *
 * <p>Credenciais esperadas (env vars):
 * - SEED_ADAPTER_USERNAME
 * - SEED_ADAPTER_PASSWORD
 * - AUTH_SERVICE_URL (ex: http://auth-service:8080)
 * - GATEWAY_SERVICE_URL (ex: http://gateway-service:8080)
 */
public class SeedAdapterMain {
    private static final Logger logger = LoggerFactory.getLogger(SeedAdapterMain.class);

    public static void main(String[] args) {
        try {
            logger.info("Iniciando seed-adapter (Story 5.1)");

            // Carrega configurações de env vars
            String username = System.getenv("SEED_ADAPTER_USERNAME");
            String password = System.getenv("SEED_ADAPTER_PASSWORD");
            String authServiceUrl = System.getenv("AUTH_SERVICE_URL");
            String gatewayServiceUrl = System.getenv("GATEWAY_SERVICE_URL");

            if (username == null || username.isEmpty()) {
                throw new IllegalStateException("SEED_ADAPTER_USERNAME não definida");
            }
            if (password == null || password.isEmpty()) {
                throw new IllegalStateException("SEED_ADAPTER_PASSWORD não definida");
            }
            if (authServiceUrl == null || authServiceUrl.isEmpty()) {
                throw new IllegalStateException("AUTH_SERVICE_URL não definida");
            }
            if (gatewayServiceUrl == null || gatewayServiceUrl.isEmpty()) {
                throw new IllegalStateException("GATEWAY_SERVICE_URL não definida");
            }

            logger.info("Configuração carregada: authServiceUrl={}, gatewayServiceUrl={}",
                    authServiceUrl, gatewayServiceUrl);

            // Cria clients
            AuthClient authClient = new AuthClient(authServiceUrl, username, password);
            RecursoClient recursoClient = new RecursoClient(gatewayServiceUrl, authClient);
            SeedDataLoader loader = new SeedDataLoader(recursoClient);

            // Executa carga
            loader.carregar();

            logger.info("Seed-adapter executado com sucesso");
            System.exit(0);
        } catch (IllegalStateException e) {
            logger.error("Falha explícita no seed-adapter: {}", e.getMessage(), e);
            System.err.println("ERRO: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Erro inesperado no seed-adapter", e);
            System.err.println("ERRO INESPERADO: " + e.getMessage());
            System.exit(1);
        }
    }
}
