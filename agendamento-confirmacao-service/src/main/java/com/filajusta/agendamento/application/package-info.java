/**
 * Casos de uso do agendamento-confirmacao-service (AD-2). {@code
 * application.command} muta estado: {@link com.filajusta.agendamento.application.command.ResolverOuCriarPaciente}
 * (FR-2, idempotente por CPF, reaproveitado do servico anterior deste
 * dominio, renomeado nesta story)
 * e {@link com.filajusta.agendamento.application.command.RegistrarAgendamento}
 * (Story 1.1) orquestram a resolucao do Paciente, a validacao de {@code
 * recursoId}/{@code dataHoraAgendamento} e a persistencia do Agendamento.
 * Portas de saida (repositorios) tambem vivem aqui, implementadas em
 * {@code infrastructure/persistence}.
 */
package com.filajusta.agendamento.application;
