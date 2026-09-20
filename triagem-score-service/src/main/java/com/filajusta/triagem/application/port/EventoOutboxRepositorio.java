package com.filajusta.triagem.application.port;

import com.filajusta.triagem.domain.EventoOutbox;

/**
 * Porta para persistência de eventos em tabela outbox.
 */
public interface EventoOutboxRepositorio {
  /**
   * Persiste um evento na tabela outbox.
   */
  void salvar(EventoOutbox evento);
}
