package com.confirmasus.matching.application.query;

import com.confirmasus.matching.domain.SugestaoRepasse;

import java.util.UUID;

/**
 * Caso de uso de {@code GET /v1/recursos/{id}/sugestao} (Story 6.1): devolve
 * a {@link SugestaoRepasse} {@code PENDENTE} do Recurso, gerada ao consumir
 * {@code VagaLiberada} (AD-6). Somente leitura -- não recalcula a fila nem
 * publica evento. {@link RecursoNaoEncontradoException} propaga para virar
 * {@code 404}.
 */
public class ConsultarSugestaoRecurso {

    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final SugestaoRepasseConsultaRepositorio sugestaoConsultaRepositorio;

    public ConsultarSugestaoRecurso(RecursoConsultaRepositorio recursoConsultaRepositorio,
                                    SugestaoRepasseConsultaRepositorio sugestaoConsultaRepositorio) {
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.sugestaoConsultaRepositorio = sugestaoConsultaRepositorio;
    }

    public Resultado consultar(UUID recursoId) {
        recursoConsultaRepositorio.buscarPorId(recursoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(recursoId));
        return sugestaoConsultaRepositorio.buscarPendentePorRecurso(recursoId)
                .map(s -> new Resultado(recursoId, s.getSugestaoId(), s.getPacienteId()))
                .orElseGet(() -> new Resultado(recursoId, null, null));
    }

    /** {@code sugestaoId}/{@code pacienteId} {@code null} = sem sugestão pendente, nunca um erro. */
    public record Resultado(UUID recursoId, UUID sugestaoId, Long pacienteId) {
    }
}
