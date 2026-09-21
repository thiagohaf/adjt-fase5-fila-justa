package com.confirmasus.triagem.application.port;

import com.confirmasus.triagem.domain.EventoOutbox;

/**
 * Porta para persistência de eventos em tabela outbox.
 */
public interface EventoOutboxRepositorio {
  /**
   * Persiste um evento na tabela outbox.
   */
  void salvar(EventoOutbox evento);
}
