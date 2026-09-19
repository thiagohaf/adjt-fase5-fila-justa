/**
 * Camada de infraestrutura do auth-service. Actuator expoe
 * {@code GET /actuator/health} (porta de management separada, ver
 * application.yml). {@code infrastructure.web} expoe
 * {@code POST /v1/auth/login} (AD-14); {@code infrastructure.persistence}
 * mapeia o schema {@code auth} (JPA + migration Flyway com usuarios
 * sinteticos, AD-9); {@code infrastructure.security} emite o JWT HS256
 * (jjwt) -- auth-service so emite, nunca valida.
 */
package com.filajusta.auth.infrastructure;
