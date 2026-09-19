package com.filajusta.auth.application.query;

import com.filajusta.auth.domain.Usuario;

/**
 * Porta de saida para emissao de token de acesso. Implementada em
 * {@code infrastructure/security} (JWT HS256 via jjwt, AD-14). auth-service
 * so emite token, nunca valida (validacao fica no gateway-service).
 */
public interface TokenIssuer {

    String emitir(Usuario usuario);
}
