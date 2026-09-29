package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.query.ConsultarSugestaoRecurso;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint público do liberacao-repasse-service: {@code GET
 * /v1/recursos/{id}/sugestao} indica qual Paciente da Lista de Espera do
 * Recurso deveria ser sugerido para Repasse -- ordem FIFO pura por {@code
 * criadoEm} ({@code ConsultarSugestaoRecurso}, AD-6). Sem alocação/reserva
 * e sempre recalculado nesta consulta (sem cache).
 *
 * <p>{@code id} não-UUID no path vira {@code 400} via {@code
 * MethodArgumentTypeMismatchException} (tradução Spring automática, sem
 * conversor customizado -- {@code @PathVariable UUID} já rejeita valores
 * malformados antes do método rodar), e {@code recursoId} inexistente vira
 * {@code 404} via {@link com.confirmasus.matching.application.query.RecursoNaoEncontradoException}
 * -- ambos traduzidos para RFC 7807 por {@link RecursosExceptionHandler}.
 */
@RestController
public class RecursoSugestaoController {

    private final ConsultarSugestaoRecurso consultarSugestaoRecurso;

    public RecursoSugestaoController(ConsultarSugestaoRecurso consultarSugestaoRecurso) {
        this.consultarSugestaoRecurso = consultarSugestaoRecurso;
    }

    @GetMapping("/v1/recursos/{id}/sugestao")
    public SugestaoRecursoResponse consultar(@PathVariable("id") UUID id) {
        return SugestaoRecursoResponse.de(consultarSugestaoRecurso.consultar(id));
    }
}
