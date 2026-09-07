package com.filajusta.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do auth-service.
 *
 * <p>Story 1.1: esqueleto minimo, so {@code GET /actuator/health}. E o alvo
 * do teste de bypass do security group -- sua porta de aplicacao (AD-12) so
 * pode ser alcancada pelo security group do gateway-service; o health-check
 * roda numa porta de management separada, liberada por um security group de
 * health-check proprio e estreito (AD-8/AD-12). {@code POST /v1/auth/login}
 * entra na Story 1.2 (AD-14).
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
