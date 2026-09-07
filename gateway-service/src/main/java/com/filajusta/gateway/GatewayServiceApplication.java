package com.filajusta.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do gateway-service (Spring Cloud Gateway).
 *
 * <p>Story 1.1: expoe apenas {@code GET /actuator/health}, publico e sem
 * token (AD-8/AD-12). Story 1.2 acrescenta a unica rota publica de dominio
 * -- {@code POST /v1/auth/login}, roteada sem filtro para o auth-service
 * (application.yml) -- ninguem tem token antes de logar. Validacao de JWT
 * para as demais rotas continua deferida (AD-8, deferred-work.md).
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
