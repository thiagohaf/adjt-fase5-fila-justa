/**
 * Camada de dominio do triagem-score-service -- sem dependencia de framework
 * (AD-2). Contem os value objects {@link com.filajusta.triagem.domain.Cpf},
 * {@link com.filajusta.triagem.domain.SinaisVitais},
 * {@link com.filajusta.triagem.domain.GravidadePercebida},
 * {@link com.filajusta.triagem.domain.Score} e
 * {@link com.filajusta.triagem.domain.FatorContribuinte} (todos validam suas
 * proprias invariantes no construtor), os agregados
 * {@link com.filajusta.triagem.domain.Paciente} e
 * {@link com.filajusta.triagem.domain.Triagem}, o evento de dominio
 * {@link com.filajusta.triagem.domain.EventoOutbox} e o algoritmo
 * deterministico {@link com.filajusta.triagem.domain.CalculadorDeScore}
 * (FR-1, FR-3, FR-4, AD-11).
 */
package com.filajusta.triagem.domain;
