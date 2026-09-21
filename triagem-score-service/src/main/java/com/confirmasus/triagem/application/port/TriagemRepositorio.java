package com.confirmasus.triagem.application.port;

import com.confirmasus.triagem.domain.Triagem;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta para persistência de Triagens.
 */
public interface TriagemRepositorio {
  /**
   * Busca uma Triagem pelo ID.
   */
  Optional<Triagem> buscarPorId(UUID id);

  /**
   * Persiste uma nova Triagem.
   */
  void salvar(Triagem triagem);
}
