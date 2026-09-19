package com.filajusta.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /v1/auth/login}. Username/password ausentes ou em
 * branco sao {@code 400} (requisicao invalida), nao {@code 401} -- o
 * {@code 401} generico da spec 1.2 e reservado para credencial que existe
 * mas nao confere (ver {@link AuthExceptionHandler}). Sem esta validacao,
 * um corpo sem "password" cai direto em
 * {@code PasswordEncoder.matches(null, hash)}, que lanca
 * {@code IllegalArgumentException} e escaparia como 500 nao-RFC-7807.
 */
record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
