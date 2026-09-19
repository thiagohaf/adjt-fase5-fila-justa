package com.filajusta.auth.infrastructure.web;

/** Resposta {@code 200} de {@code POST /v1/auth/login}: o JWT assinado (AD-14). */
record LoginResponse(String token) {
}
