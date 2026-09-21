/**
 * Camada de dominio do agendamento-confirmacao-service -- sem dependencia de
 * framework (AD-2). Contem o value object {@link com.confirmasus.agendamento.domain.Cpf}
 * (valida suas proprias invariantes no construtor, reaproveitado do servico
 * anterior deste dominio sem alteracao de logica, so de pacote -- Story 1.1)
 * e os agregados {@link com.confirmasus.agendamento.domain.Paciente} e
 * {@link com.confirmasus.agendamento.domain.Agendamento} (estado inicial
 * {@code AGUARDANDO_JANELA}, demais transicoes chegam nas proximas stories
 * do Epic 1).
 */
package com.confirmasus.agendamento.domain;
