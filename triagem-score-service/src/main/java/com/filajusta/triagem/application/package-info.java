/**
 * Casos de uso do triagem-score-service (AD-2). {@code application.command}
 * muta estado e emite evento: {@link com.filajusta.triagem.application.command.RegistrarTriagem}
 * (FR-1, FR-3) e {@link com.filajusta.triagem.application.command.ResolverOuCriarPaciente}
 * (FR-2, idempotente por CPF). {@code application.query} (Story 2.2, CQRS
 * logico da arquitetura) so le, nunca muta estado:
 * {@link com.filajusta.triagem.application.query.ConsultarTriagem} e
 * {@link com.filajusta.triagem.application.query.ListarScoresAtuais} (Story
 * 3.1a, endpoint interno de bootstrap do {@code matching-alocacao-service}).
 * Portas de saida (repositorios) tambem vivem aqui, implementadas em
 * {@code infrastructure/persistence}.
 */
package com.filajusta.triagem.application;
