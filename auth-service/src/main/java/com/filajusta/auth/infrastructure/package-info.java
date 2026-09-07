/**
 * Camada de infraestrutura do auth-service. Nesta story so o autoconfigure
 * do Spring Boot Actuator expoe {@code GET /actuator/health} (porta de
 * management separada, ver application.yml). {@code infrastructure/web}
 * (POST /v1/auth/login) e {@code infrastructure/persistence} (schema auth,
 * migration com usuarios sinteticos) entram na Story 1.2 (AD-14).
 */
package com.filajusta.auth.infrastructure;
