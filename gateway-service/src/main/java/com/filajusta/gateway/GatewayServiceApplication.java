package com.filajusta.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do gateway-service (Spring Cloud Gateway).
 *
 * <p>Story 1.1: expoe apenas {@code GET /actuator/health}, publico e sem
 * token (AD-8/AD-12). Nenhuma rota de dominio nem validacao de JWT ainda --
 * isso e Story 1.2 (AD-8/AD-14).
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
