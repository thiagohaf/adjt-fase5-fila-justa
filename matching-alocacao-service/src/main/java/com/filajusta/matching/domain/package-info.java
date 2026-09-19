/**
 * Camada de dominio do matching-alocacao-service -- sem dependencia de
 * framework (AD-2). {@link com.filajusta.matching.domain.ScoreReplica}
 * (Story 3.1b): valor imutavel que valida suas proprias invariantes no
 * construtor e encapsula a regra de last-write-wins (Boundaries da spec
 * 3.1b) usada pela replica local de Score.
 * {@link com.filajusta.matching.domain.PrioridadeEfetiva} (Story 3.1c):
 * calculo puro de Score + Urgencia Acumulada (Aging com teto), consumido
 * por {@code ConsultarFilaPriorizada} (application/query) -- k/teto vem
 * calibrados de application.yml, nunca hardcoded aqui.
 * {@link com.filajusta.matching.domain.Recurso} (Story 3.2b2): agregado de
 * capacidade de atendimento (leito, especialista, sala etc.), valida suas
 * proprias invariantes no construtor (codigoRecurso nao vazio,
 * especificidadeRank positivo) -- upsertado idempotentemente por
 * codigoRecurso via {@code UpsertRecurso} (application/command).
 */
package com.filajusta.matching.domain;
