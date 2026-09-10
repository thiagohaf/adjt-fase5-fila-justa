/**
 * Camada de dominio do matching-alocacao-service -- sem dependencia de
 * framework (AD-2). Story 3.1b contem so
 * {@link com.filajusta.matching.domain.ScoreReplica}: valor imutavel que
 * valida suas proprias invariantes no construtor e encapsula a regra de
 * last-write-wins (Boundaries da spec 3.1b) usada pela replica local de
 * Score. Prioridade Efetiva/Aging (Score + Urgencia Acumulada) sao
 * deferidos para a Story 3.1c, quando a replica tiver um consumidor real
 * (GET /v1/fila).
 */
package com.filajusta.matching.domain;
