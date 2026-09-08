/**
 * Camada de infraestrutura do gateway-service: configuracao web/Spring
 * Cloud Gateway, actuator. O autoconfigure do Spring Boot Actuator expoe
 * {@code GET /actuator/health}, publico por padrao. {@code
 * infrastructure.security} (Story 1.2) e o unico ponto do sistema que
 * valida assinatura/expiracao do JWT HS256 (AD-8/AD-14), via
 * {@code GlobalFilter}, em toda rota fora da allowlist publica
 * ({@code /actuator/health}, {@code /v1/auth/login}).
 */
package com.filajusta.gateway.infrastructure;
