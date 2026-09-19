package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.query.ConsultarSugestaoRecurso;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint público do matching-alocacao-service (Story 3.2b3): {@code GET
 * /v1/recursos/{id}/sugestao} aplica o algoritmo de tiers de desempate sobre
 * a fila global já priorizada ({@code ConsultarFilaPriorizada}) para indicar
 * qual Paciente deveria ser sugerido para o Recurso consultado. Sem
 * alocação/reserva (fora de escopo, Story 3.3 -- Boundaries "Never" da spec
 * 3.2b3) e sempre recalculado nesta consulta (sem cache).
 *
 * <p>{@code id} não-UUID no path vira {@code 400} via {@code
 * MethodArgumentTypeMismatchException} (tradução Spring automática, sem
 * conversor customizado -- {@code @PathVariable UUID} já rejeita valores
 * malformados antes do método rodar), e {@code recursoId} inexistente vira
 * {@code 404} via {@link com.filajusta.matching.application.query.RecursoNaoEncontradoException}
 * -- ambos traduzidos para RFC 7807 por {@link RecursosExceptionHandler}.
 *
 * <p>Sem autenticação JWT/rota no gateway nesta fase -- mesmo padrão de
 * {@link FilaController} (roteamento no gateway ainda pendente, gap
 * pré-existente, não desta story).
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
