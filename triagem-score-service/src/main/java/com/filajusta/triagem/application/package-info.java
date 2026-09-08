/**
 * Casos de uso do triagem-score-service (AD-2). {@code application.command}
 * muta estado e emite evento: {@link com.filajusta.triagem.application.command.RegistrarTriagem}
 * (FR-1, FR-3) e {@link com.filajusta.triagem.application.command.ResolverOuCriarPaciente}
 * (FR-2, idempotente por CPF). Portas de saida (repositorios) tambem vivem
 * aqui, implementadas em {@code infrastructure/persistence}.
 */
package com.filajusta.triagem.application;
