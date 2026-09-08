/**
 * Camada de dominio do gateway-service -- sem dependencia de framework
 * (AD-2). Vazia: a validacao de JWT (Story 1.2, AD-8) nao introduziu regra
 * de dominio propria -- e um cross-cutting concern do Gateway
 * ({@code GlobalFilter}), tratado inteiro em
 * {@code infrastructure.security.JwtAuthenticationFilter}.
 */
package com.filajusta.gateway.domain;
