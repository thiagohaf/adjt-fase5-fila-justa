package com.filajusta.matching.infrastructure.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Corpo de {@code 201} de {@code POST /v1/recursos/{id}/alocacoes/recusa}
 * (Story 3-3c1), molde {@code AlocacaoResponse} -- corpo mínimo (Boundaries
 * da spec 3-3c1).
 *
 * <p>Sem factory {@code de(...)} a partir de um objeto de domínio (ao
 * contrário de {@code AlocacaoResponse#de(Alocacao)}): não existe agregado
 * de domínio para a recusa, só o registro do par recusado -- {@code
 * RecusarSugestao#recusar} retorna apenas o {@code Instant recusadoEm}
 * efetivamente gravado (ver javadoc de {@code RecusarSugestao#recusar}),
 * então o controller monta esta resposta a partir desse retorno e dos
 * demais parâmetros recebidos ({@code recursoId}, {@code pacienteId},
 * {@code motivo}).
 */
record RecusarSugestaoResponse(UUID recursoId, long pacienteId, String motivo, Instant recusadoEm) {
}
