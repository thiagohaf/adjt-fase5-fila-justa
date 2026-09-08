package com.filajusta.triagem.application.query;

import com.filajusta.triagem.domain.Triagem;

import java.util.Optional;

/**
 * Porta de saida para busca de {@link Triagem} por id (FR-3, Story 2.2).
 * Implementada em {@code infrastructure/persistence} (JPA, schema
 * {@code triagem_score}) -- distinta de
 * {@link com.filajusta.triagem.application.command.TriagemRepositorio}
 * (que so escreve) porque esta reconstroi o dominio a partir dos dados
 * persistidos (leitura), CQRS logico da arquitetura.
 */
public interface ConsultaTriagemRepositorio {

    Optional<Triagem> buscarPorId(Long id);
}
