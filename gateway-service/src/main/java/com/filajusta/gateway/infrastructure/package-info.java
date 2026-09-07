/**
 * Camada de infraestrutura do gateway-service: configuracao web/Spring
 * Cloud Gateway, actuator. Nesta story so o autoconfigure do Spring Boot
 * Actuator expoe {@code GET /actuator/health}, publico por padrao (nenhuma
 * dependencia de seguranca no classpath ainda -- AD-8/Story 1.2).
 */
package com.filajusta.gateway.infrastructure;
